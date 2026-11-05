"""Select an up-candidate specialist in the purged selection window only."""
import hashlib
import json
import numpy as np

from finscope_market_data.overnight.candidate_evaluation import evaluate_candidates
from finscope_market_data.overnight.joint_dataset import date_weights

PROTOCOL = 'overnight-up-selection-v1'
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


def fit_up_selection(rows, through, cutoff, cohorts):
    from finscope_market_data.overnight.candidate_cohort import mark_cohort
    from finscope_market_data.overnight.direction_candidates import MULTISCALE_RECIPE
    from finscope_market_data.overnight.direction_learning import fit_direction
    from finscope_market_data.overnight.context_research import MODEL_FEATURES
    marked = mark_cohort(rows, cohorts, cutoff)
    fitted = fit_direction(marked, through, cutoff, selection_policy=POLICY, recipe=MULTISCALE_RECIPE)
    if fitted:
        fitted.update(features=list(MODEL_FEATURES), trainingScope='FIXED_RESEARCH_POOL',
                      selectionScope='ACTUAL_CANDIDATE_OVERLAP',
                      data={'fingerprint': hashlib.sha256(json.dumps(marked, sort_keys=True, allow_nan=False).encode()).hexdigest(),
                            'rows': len(marked), 'candidateRows': sum(r['candidateMember'] is True for r in marked)})
    return fitted


def attach_up_selection(report, grouped, request, artifact, contexts):
    from finscope_market_data.forecast.industry_features import IndustryMembership
    from finscope_market_data.overnight.context_features import context_features, stock_context
    from finscope_market_data.overnight.context_research import MODEL_FEATURES
    from finscope_market_data.overnight.direction_dataset import TARGET, direction_features
    from finscope_market_data.overnight.direction_learning import direction_prediction
    from finscope_market_data.overnight.multiscale_features import multiscale_features
    from datetime import datetime
    if request.mode != 'TAIL_ENTRY':
        return
    parent = report['closeDirection']
    parent['upSelection'] = {'protocol': PROTOCOL, 'target': TARGET, 'status': 'WAITING_MODEL',
                             'reason': '看涨候选专用方案等待后台训练和真实候选积累', 'validated': False}
    fitted = artifact.get('upSelection')
    if not fitted or fitted.get('protocol') != PROTOCOL or fitted.get('features') != list(MODEL_FEATURES):
        return
    snapshot = contexts.at(request.signal_date, request.cutoff, datetime.fromisoformat(report['dataThrough']))
    if not snapshot or snapshot.get('universeFingerprint') != artifact.get('universeFingerprint'):
        return
    memberships = [IndustryMembership(v['industry'], v['available_on'], tuple(v['codes'])) for v in snapshot.get('memberships', [])]
    local = direction_features(grouped, request.signal_date, request.cutoff, request.instrument_code)
    history = multiscale_features(grouped, request.signal_date, request.cutoff)
    context = context_features(request.instrument_code, stock_context(grouped, request.signal_date, request.cutoff), snapshot, memberships)
    if local is None or history is None or context is None:
        parent['upSelection'].update(status='MISSING_FEATURES', reason='候选专用方案所需历史或环境数据不足')
        return
    # The specialist never replaces the main forecast from a historical score.
    forecast = direction_prediction(fitted, local + context + history, {})
    parent['upSelection'] = {**forecast, 'artifactId': artifact['id'], 'validated': False,
        'selectionScope': fitted['selectionScope'], 'reason': '只优化少量看涨候选；独立留档对照，尚未证明有效'}
