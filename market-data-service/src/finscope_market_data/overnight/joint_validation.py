"""One fixed 60-day graduation checkpoint, then revocable rolling monitoring.

Never repeatedly retest growing windows until they happen to pass. A failed
protocol needs a new declared experiment and new future observations.
"""
from collections import defaultdict
from datetime import datetime

import numpy as np

from finscope_market_data.forecast.direction_evaluation import evaluate_direction
from finscope_market_data.overnight.engine import TARGETS
from finscope_market_data.overnight.joint_dataset import JointProfile, PROTOCOL

FORWARD_DAYS = 60
FAMILY_SIZE = 12


def assess(rows, total_count, *, complete_days=None, checkpoint=None):
    dates = sorted({row['signalDate'] for row in rows})
    available_dates = complete_days if complete_days is not None else dates
    checkpoint_days = checkpoint['checkpointDays'] if checkpoint else sorted(available_dates)[:FORWARD_DAYS]
    selected = [row for row in rows if row['signalDate'] in set(checkpoint_days)]
    coverage = len(rows) / total_count if total_count else 0.
    base = {'status': 'ACCUMULATING', 'dayCount': len(checkpoint_days), 'requiredDays': FORWARD_DAYS,
            'pairedCount': len(rows), 'recordCount': total_count, 'coverage': coverage,
            'eligible': False, 'checkpointFrom': checkpoint_days[0] if checkpoint_days else None,
            'checkpointThrough': checkpoint_days[-1] if checkpoint_days else None}
    if not selected:
        return base
    metrics = evaluate_direction([row['upProbability'] for row in selected],
        [row['actualNetReturn'] > 0 for row in selected], [row['signalDate'] for row in selected],
        {'INCUMBENT': [row['incumbentProbability'] for row in selected],
         'HISTORICAL_PRIOR': [row['baselineProbability'] for row in selected]}, family_size=FAMILY_SIZE)
    recent = [row for row in rows if row['signalDate'] in set(dates[-20:])]
    recent_daily = []
    for day in sorted({row['signalDate'] for row in recent}):
        day_rows = [row for row in recent if row['signalDate'] == day]
        recent_daily.append(float(np.mean([(row['upProbability'] - (row['actualNetReturn'] > 0)) ** 2
            - (row['incumbentProbability'] - (row['actualNetReturn'] > 0)) ** 2 for row in day_rows])))
    daily_returns, selected_days, qualified_count = [], 0, 0
    for day in checkpoint_days:
        picks = sorted([row for row in selected if row['signalDate'] == day and row['qualified']],
                       key=lambda row: (-row['rankScore'], row['instrumentCode']))[:3]
        qualified_count += len(picks)
        selected_days += bool(picks)
        daily_returns.append(float(np.mean([row['actualNetReturn'] for row in picks])) if picks else 0.)
    return_lower = None
    if len(daily_returns) >= FORWARD_DAYS:
        values = np.asarray(daily_returns)
        rng = np.random.default_rng(20261005)
        starts = rng.integers(0, len(values), size=(5000, int(np.ceil(len(values) / 5))))
        blocks = ((starts[:, :, None] + np.arange(5)) % len(values)).reshape(5000, -1)[:, :len(values)]
        return_lower = float(np.quantile(np.mean(values[blocks], axis=1), .05 / FAMILY_SIZE))
    calibrated = all(row['calibrationStatus'] == 'FITTED' for row in selected)
    passed = (metrics['eligible'] and selected_days >= 20 and return_lower is not None
              and return_lower > 0 and calibrated)
    selection = {'selectedDays': selected_days, 'selectedCount': qualified_count,
                 'meanNetReturn': float(np.mean(daily_returns)), 'netReturnLower': return_lower,
                 'rule': '概率≥55%、预测净收益>0、跌超2%概率≤25%、区间下界≥-4%，每天最多3只；空选计0'}
    if checkpoint:
        passed = checkpoint['checkpointPassed']
        metrics = checkpoint['metrics']
        selection = checkpoint['selection']
    status = 'ACCUMULATING'
    if len(checkpoint_days) >= FORWARD_DAYS:
        status = 'QUALIFIED' if passed else 'CHECKPOINT_FAILED'
        if passed and (coverage < .9 or float(np.mean(recent_daily)) >= 0):
            status = 'MONITORING_DEGRADED'
    return {**base, 'status': status, 'eligible': status == 'QUALIFIED', 'metrics': metrics,
            'recentBrierDifference': float(np.mean(recent_daily)),
            'selection': selection, 'checkpointDays': checkpoint_days, 'checkpointPassed': passed,
            'weighting': 'EQUAL_DATE_THEN_STOCK'}


def summarize_forward(store, now, meta=None):
    groups, expected, pending_dates, seen = defaultdict(list), defaultdict(int), defaultdict(set), set()
    reports = sorted(store.iter_history(), key=lambda row: (row['generatedAt'], row['id']))
    for report in reports:
        research = report.get('jointResearch') or {}
        if (report.get('evidenceKind') != 'FORWARD' or research.get('protocol') != PROTOCOL
                or not research.get('artifactId')):
            continue
        # Wait for the whole next session. Partial morning labels do not close a day early.
        if not report.get('targetDate') or report['targetDate'] >= str(now.date()):
            continue
        profile = JointProfile(report['mode'], report['cutoff'], report['costBps'])
        actuals = {row['target']: row['actualNetReturn'] for row in (report.get('outcome') or {}).get('targets', [])}
        forecasts = {row['target']: row for row in report['targets']}
        for target in TARGETS:
            key = f'{profile.key}|{target}'
            identity = (key, report['signalDate'], report['instrumentCode'])
            if identity in seen:
                continue
            seen.add(identity)
            expected[key] += 1
            forecast = forecasts.get(target) or {}
            joint = forecast.get('joint') or {}
            incumbent = joint.get('incumbentProbability')
            actual = actuals.get(target)
            if joint.get('status') != 'AVAILABLE' or incumbent is None or actual is None:
                pending_dates[key].add(report['signalDate'])
                continue
            groups[key].append({**joint, 'actualNetReturn': actual, 'signalDate': report['signalDate'],
                                'instrumentCode': report['instrumentCode']})
    results = []
    for key, count in expected.items():
        mode, cutoff, cost, target = key.split('|')
        rows = groups[key]
        complete = sorted({row['signalDate'] for row in rows} - pending_dates[key])
        checkpoint_key = f'jointCheckpoint|{PROTOCOL}|{key}'
        checkpoint = meta.get(checkpoint_key) if meta else None
        audit = assess(rows, count, complete_days=complete, checkpoint=checkpoint)
        if meta and not checkpoint and audit['dayCount'] >= FORWARD_DAYS:
            checkpoint = meta.put_once(checkpoint_key, audit)
            audit = assess(rows, count, complete_days=complete, checkpoint=checkpoint)
        results.append({'key': key, 'mode': mode, 'cutoff': cutoff, 'costBps': float(cost), 'target': target, **audit})
    return {'protocol': PROTOCOL, 'computedAt': now.isoformat(), 'groups': results,
            'requiredDays': FORWARD_DAYS, 'checkpointRule': '首60个结果完整的前瞻交易日仅评估一次；未通过不滚动试到通过。'}
