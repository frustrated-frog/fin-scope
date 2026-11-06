"""Latest-matured refits with causal rolling calibration; legacy stays reproducible."""
from dataclasses import asdict
import hashlib
import json

from finscope_market_data.forecast.direction_calibration import fit_direction_calibration
from finscope_market_data.forecast.direction_evaluation import evaluate_direction
from finscope_market_data.overnight.candidate_evaluation import evaluate_candidates
from finscope_market_data.overnight.direction_candidates import BASE_RECIPE, candidate_probabilities, fit_candidates
from finscope_market_data.overnight.direction_dataset import TARGET
from finscope_market_data.overnight.direction_learning import (
    apply_calibration, covered_matured_rows, fit_direction_before, TEST_DAYS, SELECTION_DAYS,
)
from finscope_market_data.overnight.joint_dataset import MAX_DAYS, TRAIN_DAYS, date_weights

PROTOCOL = 'overnight-fresh-direction-v1'
CALIBRATION_DAYS = 20
MIN_RESEARCH_DAYS = TRAIN_DAYS + SELECTION_DAYS + 2 * CALIBRATION_DAYS + TEST_DAYS + 4


def fresh_refit(rows, through, selected, *, recipe=BASE_RECIPE):
    matured, coverage = covered_matured_rows(rows, through)
    days = set(sorted({r['signalDate'] for r in matured})[-MAX_DAYS:])
    training = [r for r in matured if r['signalDate'] in days and r.get('trainingEligible', True)]
    if not training:
        raise ValueError('最新训练没有已到期且合格的样本')
    model = fit_candidates(training, recipe)
    model.update(selected=selected, baselineProbability=model['prior'])
    return model, {'trainingThrough': max(r['exitAt'] for r in training),
        'trainingDays': len({r['signalDate'] for r in training}), 'trainingRows': len(training),
        'trainingFingerprint': hashlib.sha256(json.dumps(training, sort_keys=True, allow_nan=False).encode()).hexdigest(),
        'dateCoverage': coverage}


def rolling_calibration(checks, through):
    # Every calibration score was issued before its outcome. Never score the
    # latest training rows again with the refitted model to calibrate in-sample.
    matured = [r for r in checks if r['exitAt'] < through and r['predictionAt'] < r['exitAt']]
    days = sorted({r['signalDate'] for r in matured})[-CALIBRATION_DAYS:]
    selected = [r for r in matured if r['signalDate'] in set(days)]
    enough = len(days) >= CALIBRATION_DAYS and all(
        len({r['signalDate'] for r in selected if (r['actualReturn'] > 0) == label}) >= 5
        for label in (False, True))
    if not selected:
        calibration = asdict(fit_direction_calibration([.5], [False], [1.], 'RAW'))
    else:
        calibration = asdict(fit_direction_calibration([r['raw'] for r in selected],
            [r['actualReturn'] > 0 for r in selected], date_weights(selected), 'INTERCEPT' if enough else 'RAW'))
    return calibration, {'calibrationMethod': 'PAST_ONLY_ROLLING_PREDICTIONS',
        'calibrationDays': len(days), 'calibrationRows': len(selected),
        'calibrationThrough': max((r['exitAt'] for r in selected), default=None),
        'calibrationPredictionThrough': max((r['predictionAt'] for r in selected), default=None)}


def metrics(checks, key):
    result = evaluate_direction([r[key] for r in checks], [r['actualReturn'] > 0 for r in checks],
        [r['signalDate'] for r in checks], {'HISTORICAL_PRIOR': [r['baseline'] for r in checks],
                                          'LEGACY': [r['legacy'] for r in checks]})
    result.update(eligible=False, task=TARGET,
        upCandidates=evaluate_candidates([{**r, 'actualUp': r['actualReturn'] > 0, 'probability': r[key]} for r in checks]))
    return result


def fit_fresh_direction(rows, through, cutoff, *, recipe=BASE_RECIPE, selection_policy='BRIER', include_checks=False,
                        selector_rows=None):
    selector_rows = rows if selector_rows is None else selector_rows
    selectors = {(r['signalDate'], r['instrumentCode']): r for r in selector_rows}
    identities = {(r['signalDate'], r['instrumentCode']) for r in rows}
    if len(identities) != len(rows) or len(selectors) != len(selector_rows) or identities != set(selectors):
        raise ValueError('时效对照必须使用相同股票日期且无重复')
    for row in rows:
        original = selectors[(row['signalDate'], row['instrumentCode'])]
        if any(original.get(key) != row.get(key) for key in ('actualReturn', 'exitAt', 'trainingEligible')):
            raise ValueError('时效对照标签或训练资格不一致')
    matured, coverage = covered_matured_rows(rows, through)
    days = sorted({r['signalDate'] for r in matured})
    # The preceding 20 issued days warm the calibrator. A further day covers
    # the T+1 label delay; none of the test dates train their own calibration.
    replay_days = days[-(CALIBRATION_DAYS + TEST_DAYS + 1):]
    if len(replay_days) < CALIBRATION_DAYS + TEST_DAYS + 1:
        return None
    checks, folds = [], []
    for day in replay_days:
        decision = f'{day}T{cutoff}:00'
        legacy = fit_direction_before(selector_rows, decision, recipe=recipe, selection_policy=selection_policy)
        if legacy is None:
            return None
        # Hold recipe selection fixed to the legacy arm to isolate freshness.
        model, audit = fresh_refit(rows, decision, legacy['model']['selected'], recipe=recipe)
        calibration, calibration_audit = rolling_calibration(checks, decision)
        test = [r for r in matured if r['signalDate'] == day]
        x = [r['features'] for r in test]
        raw = candidate_probabilities(model, x)[model['selected']]
        adjusted = apply_calibration(raw, calibration)
        original_x = [selectors[(r['signalDate'], r['instrumentCode'])]['features'] for r in test]
        old = candidate_probabilities(legacy['model'], original_x)[legacy['model']['selected']]
        checks.extend({**r, 'predictionAt': decision, 'raw': float(p), 'calibrated': float(c),
                       'legacy': float(b), 'baseline': legacy['model']['baselineProbability']}
                      for r, p, c, b in zip(test, raw, adjusted, old))
        folds.append({'signalDate': day, 'predictionAt': decision, **audit, **calibration_audit,
                      'legacyTrainingThrough': legacy['audit']['selectionThrough'], 'selected': model['selected']})
    legacy = fit_direction_before(selector_rows, through, recipe=recipe, selection_policy=selection_policy)
    model, audit = fresh_refit(rows, through, legacy['model']['selected'], recipe=recipe)
    model['calibration'], calibration_audit = rolling_calibration(checks, through)
    # Calibration remains a separately measured candidate, not an automatic
    # upgrade justified by repeatedly inspected development history.
    model['calibrationGate'] = {'directionSource': 'RAW', 'probabilitySource': 'RAW'}
    selected = [r for r in checks if r['signalDate'] in set(days[-TEST_DAYS:])]
    result = {'protocol': PROTOCOL, 'target': TARGET, 'model': model,
        'audit': {**audit, **calibration_audit, 'selected': model['selected'],
            'legacyTrainingThrough': legacy['audit']['selectionThrough'],
            'testStart': min(r['signalDate'] for r in selected), 'testThrough': max(r['exitAt'] for r in selected),
            'historical': metrics(selected, 'raw'), 'calibratedHistorical': metrics(selected, 'calibrated'),
            'folds': folds, 'evaluation': 'DAILY_WALK_FORWARD', 'evidenceKind': 'RETROSPECTIVE',
            'dateCoverage': coverage},
        'data': {'fingerprint': hashlib.sha256(json.dumps(rows, sort_keys=True, allow_nan=False).encode()).hexdigest()}}
    if include_checks:
        result['checks'] = checks
    return result
