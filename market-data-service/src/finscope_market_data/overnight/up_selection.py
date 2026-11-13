"""Rolling actual-candidate selection, with the legacy selector kept reproducible."""
import hashlib
import json
import numpy as np

from finscope_market_data.overnight.candidate_evaluation import evaluate_candidates
from finscope_market_data.overnight.joint_dataset import date_weights
from finscope_market_data.overnight.training_inputs import REVISION as TRAINING_POLICY

LEGACY_PROTOCOL = 'overnight-up-selection-v1'
PROTOCOL = 'overnight-candidate-rolling-v2'
POLICY = 'UP_CANDIDATES_V1'
MIN_SELECTION_DAYS = 10


def select_up_candidates(probabilities, rows):
    # Production rows carry actual candidate membership. Never substitute today's
    # winners or the whole research pool when those historical snapshots are absent.
    actual_cohort = any('candidateMember' in row for row in rows)
    indices = [i for i, row in enumerate(rows) if not actual_cohort or row.get('candidateMember') is True]
    selected_rows = [rows[i] for i in indices]
    days = sorted({r['signalDate'] for r in selected_rows})
    expected = {row['signalDate']: row.get('candidateExpectedCount', 0) for row in rows}
    coverage = len(indices) / max(sum(expected.values()), 1) if actual_cohort else 1.
    audit = {'policy': POLICY, 'scope': 'ACTUAL_CANDIDATE_OVERLAP' if actual_cohort else 'RESEARCH_POOL_DIAGNOSTIC',
             'selectionDays': len(days), 'selectionRows': len(indices), 'candidateCoverage': coverage, 'candidates': {}}
    if len(days) < MIN_SELECTION_DAYS or coverage < .75:
        return 'PRIOR', {**audit, 'reason': 'INSUFFICIENT_CANDIDATE_HISTORY'}
    windows = [set(part) for part in np.array_split(days, 3)]
    for name, values in probabilities.items():
        checks = [{**row, 'probability': float(values[i]), 'actualUp': row['actualReturn'] > 0}
                  for i, row in zip(indices, selected_rows)]
        score = evaluate_candidates(checks)
        labels = np.array([r['actualReturn'] > 0 for r in selected_rows])
        weights = date_weights(selected_rows)
        error = float(np.average((np.asarray(values)[indices] - labels) ** 2, weights=weights))
        prior_error = float(np.average((np.asarray(probabilities['PRIOR'])[indices] - labels) ** 2, weights=weights))
        wins = 0
        for window in windows:
            sub = evaluate_candidates([r for r in checks if r['signalDate'] in window])
            if sub['lift'] is not None and sub['lift'] > 0 and sub['precision'] > .5:
                wins += 1
        # A specialist may miss other rising stocks, but cannot win by emitting
        # one lucky high probability or a constant majority-class guess.
        eligible = (name != 'PRIOR' and score['selectedDays'] >= MIN_SELECTION_DAYS
                    and score['dayCoverage'] >= .5 and score['precision'] > .5
                    and score['lift'] > 0 and wins >= 2 and error <= prior_error + .01)
        audit['candidates'][name] = {**score, 'brierScore': error, 'subwindowWins': wins, 'eligible': eligible}
    eligible = [name for name, score in audit['candidates'].items() if score['eligible']]
    selected = max(eligible, key=lambda name: (audit['candidates'][name]['lift'],
        audit['candidates'][name]['precision'], -list(probabilities).index(name))) if eligible else 'PRIOR'
    return selected, {**audit, 'reason': 'CANDIDATE_ADVANTAGE' if eligible else 'NO_CANDIDATE_ADVANTAGE'}


def fit_up_selection(rows, through, cutoff, cohorts, *, readiness):
    from finscope_market_data.overnight.candidate_rolling import fit_rolling_candidates
    return fit_rolling_candidates(rows, cohorts, readiness, through, cutoff)


def attach_up_selection(report, grouped, request, artifact, contexts):
    from finscope_market_data.overnight.direction_dataset import TARGET, FEATURES, direction_features
    from finscope_market_data.overnight.direction_learning import direction_prediction
    if request.mode != 'TAIL_ENTRY':
        return
    parent = report['closeDirection']
    parent['upSelection'] = {'protocol': PROTOCOL, 'target': TARGET, 'status': 'WAITING_MODEL',
                             'reason': '看涨候选专用方案等待后台训练和真实候选积累', 'validated': False,
                             'trainingPolicy': TRAINING_POLICY}
    parent['rollingSelectionModels'] = [dict(parent['upSelection'], group='ROLLING')]
    fitted = artifact.get('upSelection')
    decision = report['dataThrough']
    if (not fitted or fitted.get('protocol') != PROTOCOL or fitted.get('features') != list(FEATURES)
            or artifact.get('createdAt', '9999') >= decision or artifact.get('labelsThrough', '9999') >= decision):
        return
    local = direction_features(grouped, request.signal_date, request.cutoff, request.instrument_code)
    if local is None or parent.get('upProbability') is None:
        parent['upSelection'].update(status='MISSING_FEATURES', reason='候选专用方案所需历史分钟数据不足')
        parent['rollingSelectionModels'] = [dict(parent['upSelection'], group='ROLLING')]
        return
    # The specialist never replaces the main forecast from a historical score.
    forecast = direction_prediction(fitted, local, {})
    parent['upSelection'] = {**forecast, 'artifactId': artifact['id'], 'validated': False, 'group': 'ROLLING',
        'trainingPolicy': artifact.get('trainingPolicy'),
        'trainedAt': artifact['createdAt'], 'referenceAt': decision,
        'referencePrice': grouped[request.signal_date][request.cutoff].close,
        'matchedBaselineProbability': parent['upProbability'], 'liveProbability': parent['upProbability'],
        'featureFingerprint': hashlib.sha256(json.dumps(local, allow_nan=False).encode()).hexdigest(),
        'momentumScore': local[0], 'selection': {key: fitted['audit']['selection'].get(key) for key in
            ('reason', 'selectionDays', 'plannedDays', 'candidateCoverage', 'selectionThrough')},
        'selectionScope': fitted['selectionScope'], 'reason': '最近真实候选上的滚动择模；概率与排序同时核验'}
    parent['rollingSelectionModels'] = [parent['upSelection']]
