"""Cost-independent direction with purged selection and order-preserving calibration."""
from dataclasses import asdict
from collections import defaultdict
from math import ceil

import numpy as np

from finscope_market_data.forecast.direction_calibration import fit_direction_calibration
from finscope_market_data.forecast.calibration_gate import select_calibration_outputs
from finscope_market_data.forecast.direction_evaluation import evaluate_direction
from finscope_market_data.overnight.direction_dataset import PROTOCOL, TARGET
from finscope_market_data.overnight.candidate_evaluation import evaluate_candidates
from finscope_market_data.overnight.joint_dataset import CALIBRATION_DAYS, MAX_DAYS, MIN_SYMBOLS, TRAIN_DAYS, date_weights
from finscope_market_data.overnight.joint_learning import sigmoid
from finscope_market_data.overnight.direction_candidates import (
    BASE_RECIPE, MULTISCALE_RECIPE, RECENCY_HALF_LIFE, CANDIDATE_RECIPE, candidate_probabilities, fit_candidates,
)
from finscope_market_data.overnight.direction_selection import POLICY, PROTOCOL as CONTEXT_PROTOCOL, LEGACY_PROTOCOL, select_direction
from finscope_market_data.overnight.up_selection import POLICY as UP_POLICY, LEGACY_PROTOCOL as UP_PROTOCOL, select_up_candidates

SELECTION_DAYS = 20
TEST_DAYS = 20
MIN_DATE_COVERAGE = .75


def covered_matured_rows(rows, through):
    matured = [row for row in rows if row['exitAt'] < through and not row.get('candidateOnly')]
    symbols, by_date = set(), defaultdict(set)
    for row in matured:
        symbols.add(row['instrumentCode'])
        by_date[row['signalDate']].add(row['instrumentCode'])
    required = max(MIN_SYMBOLS, ceil(len(symbols) * MIN_DATE_COVERAGE))
    excluded = sorted(day for day, codes in by_date.items() if len(codes) < required)
    usable = {day for day, codes in by_date.items() if len(codes) >= required}
    return [row for row in matured if row['signalDate'] in usable], {
        'requiredSymbolsPerDate': required, 'minimumDateCoverage': MIN_DATE_COVERAGE,
        'excludedDates': excluded, 'usableDays': len(usable), 'observedSymbols': len(symbols)}


def temporal_parts(rows, through):
    matured, _ = covered_matured_rows(rows, through)
    days = sorted({row['signalDate'] for row in matured})[-MAX_DAYS:]
    if len(days) < TRAIN_DAYS + SELECTION_DAYS + CALIBRATION_DAYS + 2:
        return None
    selection_start, calibration_start = days[-40], days[-20]
    training = [r for r in matured if days[0] <= r['signalDate'] < selection_start
                and r['exitAt'][:10] < selection_start and r.get('trainingEligible', True)]
    selection = [r for r in matured if selection_start <= r['signalDate'] < calibration_start and r['exitAt'][:10] < calibration_start]
    calibration = [r for r in matured if r['signalDate'] >= calibration_start]
    if (len({r['signalDate'] for r in training}) < TRAIN_DAYS
            or min(len({r['instrumentCode'] for r in part}) for part in (training, selection, calibration)) < MIN_SYMBOLS):
        return None
    return training, selection, calibration


def apply_calibration(p, calibration):
    p = np.clip(p, 1e-6, 1 - 1e-6)
    return sigmoid(calibration['slope'] * np.log(p / (1 - p)) + calibration['intercept'])


def predict_direction(model, features):
    raw = candidate_probabilities(model, features)[model['selected']]
    return raw, apply_calibration(raw, model['calibration'])


def fit_direction_before(rows, through, *, selection_policy='BRIER', recipe=BASE_RECIPE):
    if selection_policy not in ('BRIER', POLICY, UP_POLICY):
        raise ValueError('未知方向模型选择协议')
    parts = temporal_parts(rows, through)
    if parts is None:
        return None
    training, selection, calibration = parts
    if selection_policy == UP_POLICY:
        start, end = min(r['signalDate'] for r in selection), min(r['signalDate'] for r in calibration)
        selection = selection + [r for r in rows if r.get('candidateOnly') and start <= r['signalDate'] < end
                                 and r['exitAt'][:10] < end and r['exitAt'] < through]
    candidates = fit_candidates(training, recipe)
    sy = np.array([r['actualReturn'] > 0 for r in selection])
    weights = date_weights(selection)
    probabilities = candidate_probabilities(candidates, [r['features'] for r in selection])
    scores = {name: float(np.average((p - sy) ** 2, weights=weights)) for name, p in probabilities.items()}
    selected = min(probabilities, key=lambda name: (scores[name], list(probabilities).index(name)))
    selection_audit = {'policy': 'BRIER'}
    if selection_policy == POLICY:
        selected, selection_audit = select_direction(probabilities, selection)
    elif selection_policy == UP_POLICY:
        selected, selection_audit = select_up_candidates(probabilities, selection)
    # Selection labels may now train the chosen recipe; calibration remains separate.
    final_training = training + [row for row in selection if row.get('trainingEligible', True)]
    model = fit_candidates(final_training, recipe)
    model['selected'] = selected
    cy = np.array([r['actualReturn'] > 0 for r in calibration])
    raw = candidate_probabilities(model, [r['features'] for r in calibration])[selected]
    enough_days = min(len({r['signalDate'] for r, label in zip(calibration, cy) if label == value}) for value in (False, True)) >= 5
    mode = 'INTERCEPT' if enough_days else 'RAW'
    model['calibration'] = asdict(fit_direction_calibration(raw, cy, date_weights(calibration), mode))
    model['baselineProbability'] = float(np.average([r['actualReturn'] > 0 for r in training + selection + calibration],
                                                   weights=date_weights(training + selection + calibration)))
    return {'model': model, 'audit': {'selected': selected, 'selectionBrier': scores, 'selection': selection_audit,
        'recipe': recipe, 'recencyHalfLifeSessions': RECENCY_HALF_LIFE if recipe == MULTISCALE_RECIPE else None,
        'trainingThrough': max(r['exitAt'] for r in training), 'selectionStart': min(r['signalDate'] for r in selection),
        'modelFitThrough': max(r['exitAt'] for r in final_training),
        'selectionThrough': max(r['exitAt'] for r in selection), 'calibrationStart': min(r['signalDate'] for r in calibration),
        'calibrationThrough': max(r['exitAt'] for r in calibration),
        'trainingDays': len({r['signalDate'] for r in training}),
        'selectionDays': len({r['signalDate'] for r in selection}), 'calibrationDays': len({r['signalDate'] for r in calibration})}}


def fit_direction(rows, through, cutoff, *, selection_policy='BRIER', recipe=BASE_RECIPE,
                  test_days=TEST_DAYS, include_checks=False):
    if test_days < TEST_DAYS:
        raise ValueError('测试区间不得少于 20 个交易日')
    matured, coverage = covered_matured_rows(rows, through)
    days = sorted({r['signalDate'] for r in matured})
    if len(days) < TRAIN_DAYS + SELECTION_DAYS + CALIBRATION_DAYS + test_days + 3:
        return None
    checks, folds = [], []
    for day in days[-test_days:]:
        decision_at = f'{day}T{cutoff}:00'
        # Recompute training coverage from labels available at each decision,
        # never from which stocks will have outcomes later in this audit.
        fitted = fit_direction_before(rows, decision_at, selection_policy=selection_policy, recipe=recipe)
        if fitted is None:
            return None
        test = [r for r in matured if r['signalDate'] == day]
        raw, calibrated = predict_direction(fitted['model'], [r['features'] for r in test])
        gate = calibration_gate(checks, decision_at)
        p = calibrated if calibration_source(gate) == 'INTERCEPT' else raw
        checks.extend({**r, 'probability': float(value), 'raw': float(unadjusted), 'calibrated': float(adjusted),
                       'baseline': fitted['model']['baselineProbability']} for r, value, unadjusted, adjusted in zip(test, p, raw, calibrated))
        folds.append({'signalDate': day, 'cutoff': decision_at, **fitted['audit']})
    current = fit_direction_before(rows, through, selection_policy=selection_policy, recipe=recipe)
    if current is None:
        return None
    labels, dates = [r['actualReturn'] > 0 for r in checks], [r['signalDate'] for r in checks]
    baselines = {'HISTORICAL_PRIOR': [r['baseline'] for r in checks]}
    historical = evaluate_direction([r['probability'] for r in checks], labels, dates, baselines)
    historical['upCandidates'] = evaluate_candidates([{**r, 'actualUp': r['actualReturn'] > 0} for r in checks])
    raw = evaluate_direction([r['raw'] for r in checks], labels, dates, baselines)
    calibrated_audit = evaluate_direction([r['calibrated'] for r in checks], labels, dates, baselines)
    current['model']['calibrationGate'] = calibration_gate(checks, through)
    historical.update(task=TARGET, eligible=False, reason='历史开发诊断；次日涨跌需单独积累真实前瞻记录',
                      raw={key: raw[key] for key in ('accuracy', 'balancedAccuracy', 'brierScore', 'auc', 'predictedUpRate')},
                      calibrated={key: calibrated_audit[key] for key in ('accuracy', 'balancedAccuracy', 'brierScore', 'auc', 'predictedUpRate')})
    protocol = UP_PROTOCOL if selection_policy == UP_POLICY else CONTEXT_PROTOCOL if recipe == MULTISCALE_RECIPE else LEGACY_PROTOCOL if selection_policy == POLICY else PROTOCOL
    result = {'protocol': protocol, 'target': TARGET, 'model': current['model'], 'audit': {**current['audit'],
        'testStart': min(dates), 'testThrough': max(r['exitAt'] for r in checks),
        'historical': historical, 'folds': folds, 'evaluation': 'DAILY_WALK_FORWARD', 'dateCoverage': coverage}}
    if include_checks:
        result['checks'] = checks
    return result


def direction_prediction(fitted, features, live_gate=None):
    raw, adjusted = predict_direction(fitted['model'], [features])
    gate = live_gate if live_gate is not None else fitted['model'].get('calibrationGate', {})
    source = calibration_source(gate)
    p = float(adjusted[0] if source == 'INTERCEPT' else raw[0])
    audit = fitted['audit']
    baseline = fitted['model']['selected'] == 'PRIOR' or ('tree' not in fitted['model']
        and fitted['model']['selected'] != CANDIDATE_RECIPE)
    return {'protocol': fitted.get('protocol', PROTOCOL), 'target': TARGET, 'status': 'SHADOW', 'upProbability': p,
            'assessmentStatus': 'BASELINE_ONLY' if baseline else 'MODEL_ESTIMATE',
            'modelFitThrough': audit.get('modelFitThrough') or audit.get('selectionThrough') or audit.get('trainingThrough'),
            'initialTrainingThrough': audit.get('trainingThrough') if audit.get('selectionThrough') else None,
            'notUpProbability': 1 - p, 'rawUpProbability': float(raw[0]),
            'calibratedUpProbability': float(adjusted[0]), 'probabilitySource': source,
            'direction': 'UP' if p >= .5 else 'NOT_UP', 'selectedModel': fitted['model']['selected'],
            'baselineProbability': fitted['model']['baselineProbability'],
            'calibrationStatus': fitted['model']['calibration']['status'],
            'historical': fitted['audit']['historical'], 'corporateActionsVerified': False}


def calibration_gate(checks, through):
    matured = [row for row in checks if row['exitAt'] < through]
    return select_calibration_outputs([r['raw'] for r in matured], [r['calibrated'] for r in matured],
        [r['actualReturn'] > 0 for r in matured], [r['signalDate'] for r in matured])


def calibration_source(gate):
    return 'INTERCEPT' if gate.get('directionSource') == gate.get('probabilitySource') == 'INTERCEPT' else 'RAW'
