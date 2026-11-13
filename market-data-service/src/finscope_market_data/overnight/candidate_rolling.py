"""Latest-matured refits selected on past, complete, actual candidate cohorts."""
import hashlib
import json
from collections import defaultdict

import numpy as np

from finscope_market_data.forecast.direction_evaluation import evaluate_direction
from finscope_market_data.overnight.candidate_evaluation import evaluate_candidates, evaluate_ranking
from finscope_market_data.overnight.direction_candidates import CANDIDATES, candidate_probabilities
from finscope_market_data.overnight.direction_dataset import FEATURES, TARGET
from finscope_market_data.overnight.direction_learning import covered_matured_rows
from finscope_market_data.overnight.fresh_direction import fresh_refit
from finscope_market_data.overnight.joint_dataset import TRAIN_DAYS, date_weights
from finscope_market_data.overnight.detection_metrics import detection
from finscope_market_data.overnight.up_selection import PROTOCOL
from finscope_market_data.overnight.candidate_training import RECIPE, fit_candidate_training
from finscope_market_data.overnight.candidate_training_evaluation import evaluate_candidate_training

SELECTION_DAYS = 20
MIN_SELECTION_DAYS = 10
TOP_K = 3


def recipe_metrics(rows, name):
    values = [{**r, 'probability': r['probabilities'][name], 'score': r['probabilities'][name]} for r in rows]
    labels = np.array([r['actualUp'] for r in values])
    p = np.array([r['probability'] for r in values])
    return {'topK': evaluate_ranking(values, top_k=TOP_K), 'calls': evaluate_candidates(values, top_k=TOP_K),
            'direction': detection(values), 'brierScore': float(np.average((p-labels)**2, weights=date_weights(values)))}


def select_candidate_recipe(checks, readiness, through):
    days = [d for d in readiness['days'] if d.get('targetAt') and d['targetAt'] < through][-SELECTION_DAYS:]
    planned = {d['signalDate'] for d in days}
    grouped = defaultdict(list)
    for row in checks:
        if row['signalDate'] in planned and row['exitAt'] < through:
            grouped[row['signalDate']].append(row)
    rows = []
    for day in days:
        values = grouped[day['signalDate']]
        if len({r['instrumentCode'] for r in values}) != len(values):
            raise ValueError('择模记录存在重复候选')
        if day['complete'] and len(values) == day['expectedCount']:
            rows.extend(values)
    complete = sorted({r['signalDate'] for r in rows})
    expected = sum(d['expectedCount'] for d in days)
    audit = {'policy': PROTOCOL, 'selectionDays': len(complete), 'plannedDays': len(days),
        'selectionRows': len(rows), 'expectedRows': expected, 'candidateCoverage': len(rows) / expected if expected else 0.,
        'selectionThrough': max((r['exitAt'] for r in rows), default=None), 'candidates': {},
        'scope': 'PAST_PREDICTIONS_ON_COMPLETE_ACTUAL_COHORTS'}
    if len(complete) < MIN_SELECTION_DAYS or audit['candidateCoverage'] < .75:
        return 'PRIOR', {**audit, 'reason': 'INSUFFICIENT_COMPLETE_CANDIDATES'}
    momentum = evaluate_ranking([{**r, 'score': r['momentum']} for r in rows], top_k=TOP_K)
    metrics = {name: recipe_metrics(rows, name) for name in CANDIDATES}
    windows = [set(part) for part in np.array_split(complete, 3)]
    for name, score in metrics.items():
        wins = 0
        for window in windows:
            subset = [r for r in rows if r['signalDate'] in window]
            rank = evaluate_ranking([{**r, 'score': r['probabilities'][name]} for r in subset], top_k=TOP_K)
            simple = evaluate_ranking([{**r, 'score': r['momentum']} for r in subset], top_k=TOP_K)
            if rank['lift'] > 0 and rank['precision'] > simple['precision']:
                wins += 1
        calls = score['calls']
        eligible = (name != 'PRIOR' and score['topK']['lift'] > 0
                    and score['topK']['precision'] > momentum['precision'] and wins >= 2
                    and calls['dayCoverage'] >= .5 and (calls['precision'] or 0) > .5
                    and score['brierScore'] <= metrics['PRIOR']['brierScore'] + .01)
        audit['candidates'][name] = {**score, 'subwindowWins': wins, 'eligible': eligible}
    eligible = [name for name, score in audit['candidates'].items() if score['eligible']]
    selected = max(eligible, key=lambda name: (metrics[name]['topK']['lift'], metrics[name]['calls']['precision'],
                   metrics[name]['direction']['upRecall'] or 0, -metrics[name]['brierScore'])) if eligible else 'PRIOR'
    return selected, {**audit, 'momentum': momentum, 'reason': 'CANDIDATE_ADVANTAGE' if eligible else 'NO_CANDIDATE_ADVANTAGE'}


def fit_rolling_candidates(rows, cohorts, readiness, through, cutoff, *, include_checks=False):
    identities = {(r['signalDate'], r['instrumentCode']): r for r in rows}
    if len(identities) != len(rows):
        raise ValueError('滚动择模不允许重复股票日期')
    checks, folds = [], []
    for day in readiness['days']:
        if not day['complete'] or not day.get('targetAt') or day['targetAt'] >= through:
            continue
        decision = f"{day['signalDate']}T{cutoff}:00"
        training, coverage = covered_matured_rows(rows, decision)
        if coverage['usableDays'] < TRAIN_DAYS:
            folds.append({'signalDate': day['signalDate'], 'status': 'INSUFFICIENT_TRAINING_HISTORY'})
            continue
        test = [identities.get((day['signalDate'], code)) for code in cohorts[(day['signalDate'], cutoff)]['candidates']]
        if len(test) != day['expectedCount'] or any(r is None or r['exitAt'] != day['targetAt'] for r in test):
            raise ValueError('完整候选日与实际样本不一致')
        reference, selection = select_candidate_recipe(checks, readiness, decision)
        model, fit_audit = fresh_refit(training, decision, reference)
        specialized, target_audit = fit_candidate_training(rows, cohorts, readiness, decision, cutoff)
        selected = RECIPE if specialized else 'PRIOR'
        if specialized:
            model['actualCandidate'] = specialized
        probabilities = candidate_probabilities(model, [r['features'] for r in test])
        for i, row in enumerate(test):
            checks.append({'signalDate': row['signalDate'], 'instrumentCode': row['instrumentCode'],
                'actualUp': bool(row['actualReturn'] > 0), 'exitAt': row['exitAt'], 'predictionAt': decision,
                'momentum': row['features'][0], 'probabilities': {k: float(p[i]) for k, p in probabilities.items()},
                'probability': float(probabilities[selected][i]), 'selectedModel': selected,
                'candidateReady': specialized is not None, 'poolProbability': float(probabilities[reference][i]),
                'poolShapeProbability': float(probabilities['SHAPE_LOGISTIC'][i]),
                'poolPrior': model['prior'], 'candidatePrior': specialized['prior'] if specialized else None})
        folds.append({'signalDate': day['signalDate'], 'predictionAt': decision, 'status': 'COMPLETE',
                      'modelFitThrough': max(fit_audit['trainingThrough'], target_audit['trainingThrough'] or ''),
                      'selected': selected, 'selection': selection, 'candidateTraining': target_audit})
    training, coverage = covered_matured_rows(rows, through)
    if coverage['usableDays'] < TRAIN_DAYS:
        return None
    reference, selection = select_candidate_recipe(checks, readiness, through)
    model, fit_audit = fresh_refit(training, through, reference)
    specialized, target_audit = fit_candidate_training(rows, cohorts, readiness, through, cutoff)
    model['selected'] = RECIPE if specialized else 'PRIOR'
    if specialized:
        model['actualCandidate'] = specialized
    fit_audit['trainingThrough'] = max(fit_audit['trainingThrough'], target_audit['trainingThrough'] or '')
    model['calibration'] = {'slope': 1., 'intercept': 0., 'status': 'NOT_FITTED'}
    model['calibrationGate'] = {'directionSource': 'RAW', 'probabilitySource': 'RAW'}
    historical = evaluate_direction([r['probability'] for r in checks], [r['actualUp'] for r in checks],
        [r['signalDate'] for r in checks], {'HISTORICAL_PRIOR': [r['probabilities']['PRIOR'] for r in checks]}) if checks else {}
    historical.update(eligible=False, upCandidates=evaluate_candidates(checks),
                      topK=evaluate_ranking([{**r, 'score': r['probability']} for r in checks]), direction=detection(checks))
    result = {'protocol': PROTOCOL, 'target': TARGET, 'features': list(FEATURES), 'model': model,
        'trainingScope': 'ACTUAL_COMPLETE_CANDIDATES', 'selectionScope': 'FIXED_CANDIDATE_RECIPE',
        'audit': {**fit_audit, 'modelFitThrough': fit_audit['trainingThrough'], 'selection': selection,
            'candidateTraining': target_audit,
            'candidateComparison': evaluate_candidate_training(checks, readiness, through),
            'calibrationThrough': max((r['exitAt'] for r in checks), default=fit_audit['trainingThrough']), 'calibrationUsed': False,
            'testThrough': max((r['exitAt'] for r in checks), default=fit_audit['trainingThrough']),
            'folds': folds, 'historical': historical, 'evidenceKind': 'RETROSPECTIVE', 'evaluation': 'NESTED_DAILY_WALK_FORWARD'},
        'data': {'fingerprint': hashlib.sha256(json.dumps(rows, sort_keys=True, allow_nan=False).encode()).hexdigest()},
        'readiness': readiness}
    if include_checks:
        result['checks'] = checks
    return result
