"""Separate immutable forward checkpoint for close/close price direction."""
from collections import defaultdict

from finscope_market_data.forecast.direction_evaluation import evaluate_direction
from finscope_market_data.forecast.calibration_gate import select_calibration_outputs
from finscope_market_data.overnight.direction_dataset import PROTOCOL, TARGET

FORWARD_DAYS = 60


def summarize_direction(store, now, meta=None):
    groups, expected, pending, seen = defaultdict(list), defaultdict(int), defaultdict(set), set()
    for report in sorted(store.iter_history(), key=lambda r: (r['generatedAt'], r['id'])):
        prediction = report.get('closeDirection') or {}
        if (report.get('evidenceKind') != 'FORWARD' or (report.get('jointResearch') or {}).get('cohort') != 'AUTOMATIC'
                or prediction.get('protocol') != PROTOCOL or prediction.get('target') != TARGET
                or not report.get('targetDate') or report['targetDate'] >= str(now.date())):
            continue
        key = f"{report['mode']}|{report['cutoff']}"
        identity = (key, report['signalDate'], report['instrumentCode'])
        if identity in seen:
            continue
        seen.add(identity)
        expected[key] += 1
        outcome = (report.get('outcome') or {}).get('closeDirection') or {}
        if (not prediction.get('artifactId') or prediction.get('upProbability') is None
                or prediction.get('baselineProbability') is None or outcome.get('status') != 'SETTLED'):
            pending[key].add(report['signalDate'])
            continue
        groups[key].append({**prediction, 'signalDate': report['signalDate'], 'actualUp': outcome['actualUp']})
    result = []
    for key, count in expected.items():
        rows = groups[key]
        complete = sorted({r['signalDate'] for r in rows} - pending[key])
        checkpoint_key = f'closeDirectionCheckpoint|{PROTOCOL}|{key}'
        checkpoint = meta.get(checkpoint_key) if meta else None
        days = checkpoint['checkpointDays'] if checkpoint else complete[:FORWARD_DAYS]
        selected = [r for r in rows if r['signalDate'] in set(days)]
        coverage = len(rows) / count
        audit = {'status': 'ACCUMULATING', 'eligible': False, 'dayCount': len(days), 'requiredDays': FORWARD_DAYS,
                 'pairedCount': len(rows), 'recordCount': count, 'coverage': coverage, 'checkpointDays': days}
        calibration_rows = [r for r in rows if r.get('rawUpProbability') is not None and r.get('calibratedUpProbability') is not None]
        audit['calibrationGate'] = select_calibration_outputs([r['rawUpProbability'] for r in calibration_rows],
            [r['calibratedUpProbability'] for r in calibration_rows], [r['actualUp'] for r in calibration_rows],
            [r['signalDate'] for r in calibration_rows])
        if selected:
            metrics = evaluate_direction([r['upProbability'] for r in selected], [r['actualUp'] for r in selected],
                [r['signalDate'] for r in selected], {'HISTORICAL_PRIOR': [r['baselineProbability'] for r in selected]}, family_size=3)
            metrics['task'] = TARGET
            audit['metrics'] = metrics
            audit['checkpointPassed'] = metrics['eligible']
            if len(days) >= FORWARD_DAYS:
                if meta and not checkpoint:
                    checkpoint = meta.put_once(checkpoint_key, audit)
                if checkpoint:
                    audit.update(metrics=checkpoint['metrics'], checkpointPassed=checkpoint['checkpointPassed'])
                recent_days = sorted({r['signalDate'] for r in rows})[-20:]
                recent = [r for r in rows if r['signalDate'] in set(recent_days)]
                recent_score = evaluate_direction([r['upProbability'] for r in recent], [r['actualUp'] for r in recent],
                    [r['signalDate'] for r in recent], {'HISTORICAL_PRIOR': [r['baselineProbability'] for r in recent]})
                comparison = recent_score['comparisons']['HISTORICAL_PRIOR']
                healthy = coverage >= .9 and comparison['brierDifference'] < 0 and comparison['accuracyDifference'] > 0
                audit['status'] = ('QUALIFIED' if healthy else 'MONITORING_DEGRADED') if audit['checkpointPassed'] else 'CHECKPOINT_FAILED'
                audit['eligible'] = audit['status'] == 'QUALIFIED'
        mode, cutoff = key.split('|')
        result.append({'key': key, 'mode': mode, 'cutoff': cutoff, **audit})
    return {'protocol': PROTOCOL, 'target': TARGET, 'computedAt': now.isoformat(), 'groups': result}
