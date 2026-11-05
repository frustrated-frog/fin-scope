"""Descriptive failure slices on frozen automatic predictions, not causal claims."""
from collections import Counter, defaultdict
from datetime import datetime

import numpy as np

from finscope_market_data.forecast.trading_calendar import next_session
from finscope_market_data.overnight.candidate_cohort import scheduled_cohorts
from finscope_market_data.overnight.candidate_evaluation import evaluate_candidates
from finscope_market_data.overnight.direction_dataset import PROTOCOL, TARGET
from finscope_market_data.overnight.joint_dataset import date_weights


def slice_metrics(rows):
    weights = date_weights(rows)
    p = np.array([r['probability'] for r in rows])
    y = np.array([r['actualUp'] for r in rows])
    average = lambda values: float(np.average(values, weights=weights))
    return {'sampleCount': len(rows), 'dayCount': len({r['signalDate'] for r in rows}),
        'accuracy': average((p >= .5) == y), 'brierScore': average((p-y) ** 2),
        'falseUpCount': int(np.sum((p >= .5) & ~y)), 'missedUpCount': int(np.sum((p < .5) & y)),
        'observedUpRate': average(y), 'meanProbability': average(p),
        'status': 'SMALL_SAMPLE' if len({r['signalDate'] for r in rows}) < 20 else 'DESCRIPTIVE_ONLY'}


def market_state(snapshot):
    pool = (snapshot or {}).get('pool', {})
    if not pool or len(pool) / max(snapshot.get('expectedSymbols', 1), 1) < .75:
        return 'UNKNOWN'
    mean = float(np.mean([r['return'] for r in pool.values()]))
    return 'UP' if mean >= .005 else 'DOWN' if mean <= -.005 else 'MIXED'


def build_error_audit(store, meta, contexts, now):
    cohorts = scheduled_cohorts(meta, now.isoformat())
    reports = {}
    for report in sorted(store.iter_history(), key=lambda r: (r['generatedAt'], r['id'])):
        if (report.get('evidenceKind') != 'FORWARD' or report['generatedAt'] >= now.isoformat()
                or (report.get('jointResearch') or {}).get('cohort') != 'AUTOMATIC'):
            continue
        key = (report['signalDate'], report['cutoff'], report['instrumentCode'])
        reports.setdefault(key, report)
    groups = defaultdict(lambda: {'rows': [], 'missing': Counter(), 'scheduled': 0, 'complete': [], 'cohorts': []})
    for (day, cutoff), cohort in sorted(cohorts.items()):
        target = next_session(datetime.fromisoformat(day).date())
        if target is None or f'{target}T15:00:00' >= now.isoformat():
            continue
        mode = 'AFTER_CLOSE_HOLDING' if cutoff == '15:00' else 'TAIL_ENTRY'
        group = groups[f'{mode}|{cutoff}']
        group['cohorts'].append(cohort['fingerprint'])
        snapshot = contexts.at(day, cutoff, datetime.fromisoformat(f'{day}T{cutoff}:00'))
        before = len(group['rows'])
        for code, candidate in cohort['candidates'].items():
            group['scheduled'] += 1
            report = reports.get((day, cutoff, code), {})
            direction = report.get('closeDirection') or {}
            direction = direction.get('incumbentPrediction') or direction
            outcome = (report.get('outcome') or {}).get('closeDirection') or {}
            missing = missing_reason(report, direction, outcome, now)
            if missing:
                group['missing'][missing] += 1
                continue
            row = {'signalDate': day, 'instrumentCode': code, 'probability': direction['upProbability'],
                'actualUp': bool(outcome['actualUp']), 'source': (report.get('acquisition') or {}).get('source', report.get('sourceCode', 'UNKNOWN')),
                'lane': candidate.get('selectionLane', 'UNKNOWN'), 'marketState': market_state(snapshot),
                'board': 'GEM' if code.startswith('3') else 'MAIN',
                'confidence': 'HIGH' if abs(direction['upProbability'] - .5) >= .1 else 'LOW',
                'upSelection': (report.get('closeDirection') or {}).get('upSelection')}
            group['rows'].append(row)
        if len(group['rows']) - before == len(cohort['candidates']):
            group['complete'].append(day)
    return {'protocol': 'overnight-error-audit-v1', 'target': TARGET, 'computedAt': now.isoformat(),
            'scope': '真实尾盘候选与计划研判持仓；按日期等权；分组差异不证明因果',
            'groups': [summarize(key, value) for key, value in sorted(groups.items())]}


def missing_reason(report, direction, outcome, now):
    if not report:
        return 'MISSING_PREDICTION'
    if direction.get('protocol') != PROTOCOL or direction.get('target') != TARGET:
        return 'LEGACY_PROTOCOL'
    if direction.get('upProbability') is None or not direction.get('artifactId'):
        return 'MISSING_MODEL'
    if (outcome.get('status') != 'SETTLED' or not isinstance(outcome.get('actualUp'), bool)
            or outcome.get('exitAt', '') >= now.isoformat()):
        return 'MISSING_OUTCOME'
    return None


def summarize(key, group):
    rows = group['rows']
    slices = []
    for dimension in ('signalDate', 'marketState', 'source', 'lane', 'board', 'confidence'):
        buckets = defaultdict(list)
        for row in rows:
            buckets[row[dimension]].append(row)
        slices.extend({'dimension': dimension, 'value': value, **slice_metrics(items)} for value, items in sorted(buckets.items()))
    complete = [r for r in rows if r['signalDate'] in group['complete']]
    mode, cutoff = key.split('|')
    return {'key': key, 'mode': mode, 'cutoff': cutoff, 'scheduledCount': group['scheduled'],
        'settledCount': len(rows), 'coverage': len(rows) / group['scheduled'], 'missing': dict(group['missing']),
        'completeDays': len(group['complete']), 'cohortCount': len(group['cohorts']),
        'metrics': slice_metrics(rows) if rows else None, 'slices': slices,
        'upCandidates': evaluate_candidates(complete),
        'selectionComparison': selection_comparison(complete)}


def selection_comparison(rows):
    # Complete paired days only: a missing challenger must not improve its score.
    from finscope_market_data.overnight.up_selection import PROTOCOL as SELECTION_PROTOCOL
    by_day = defaultdict(list)
    for row in rows:
        by_day[row['signalDate']].append(row)
    paired = [row for values in by_day.values() if all(
        (r.get('upSelection') or {}).get('protocol') == SELECTION_PROTOCOL
        and r['upSelection'].get('upProbability') is not None for r in values) for row in values]
    return {'status': 'SHADOW', 'eligible': False, 'pairedDays': len({r['signalDate'] for r in paired}),
            'incumbent': evaluate_candidates(paired),
            'challenger': evaluate_candidates([{**r, 'probability': r['upSelection']['upProbability']} for r in paired])}
