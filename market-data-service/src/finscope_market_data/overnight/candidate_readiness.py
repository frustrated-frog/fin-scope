"""Explicit availability of each frozen candidate, without requiring optional context."""
from collections import Counter, defaultdict
from datetime import datetime

from finscope_market_data.forecast.trading_calendar import next_session
from finscope_market_data.overnight.direction_dataset import close_outcome, direction_features
from finscope_market_data.overnight.engine import group_bars

PROTOCOL = 'overnight-candidate-readiness-v1'
MAX_COHORT_DAYS = 60


def build_actual_candidate_panel(base, store, cohorts, cutoff, through):
    original = {(r['signalDate'], r['instrumentCode']): r for r in base}
    if len(original) != len(base):
        raise ValueError('候选样本存在重复股票日期')
    selected = [(day, value) for (day, stamp), value in sorted(cohorts.items())
                if stamp == cutoff and f'{day}T{stamp}:00' < through.isoformat()][-MAX_COHORT_DAYS:]
    records, pending, extra = [], defaultdict(list), []
    for day, cohort in selected:
        target = next_session(datetime.fromisoformat(day).date())
        target_at = f'{target}T15:00:00' if target else None
        for code in sorted(cohort['candidates']):
            record = {'signalDate': day, 'instrumentCode': code, 'targetAt': target_at,
                      'snapshotAt': cohort['snapshotAt'], 'reason': 'AVAILABLE'}
            records.append(record)
            if target_at is None:
                record['reason'] = 'CALENDAR_UNAVAILABLE'
            elif target_at >= through.isoformat():
                record['reason'] = 'NOT_DUE'
            elif (day, code) in original and original[(day, code)]['exitAt'] != target_at:
                record['reason'] = 'MISSING_OUTCOME'
            elif (day, code) not in original:
                pending[code].append(record)
    for code, items in pending.items():
        grouped = group_bars(store.bars(code, through))
        for record in items:
            day = datetime.fromisoformat(record['signalDate']).date()
            outcome = close_outcome(grouped, day, through, cutoff)
            if outcome['status'] != 'SETTLED':
                record['reason'] = 'MISSING_OUTCOME' if grouped.get(day, {}).get(cutoff) else 'MISSING_DECISION_BARS'
                continue
            local = direction_features(grouped, day, cutoff, code)
            if local is None:
                record['reason'] = 'MISSING_PAST_FEATURES'
                continue
            extra.append({'signalDate': str(day), 'instrumentCode': code, 'features': local,
                          'actualReturn': outcome['actualReturn'], 'exitAt': outcome['exitAt'], 'candidateOnly': True})
    by_day = defaultdict(list)
    for record in records:
        by_day[record['signalDate']].append(record)
    days = []
    for day, items in sorted(by_day.items()):
        reasons = Counter(r['reason'] for r in items)
        days.append({'signalDate': day, 'targetAt': items[0]['targetAt'], 'expectedCount': len(items),
                     'availableCount': reasons['AVAILABLE'], 'complete': reasons['AVAILABLE'] == len(items),
                     'missing': {k: v for k, v in reasons.items() if k != 'AVAILABLE'}})
    return base + extra, {'protocol': PROTOCOL, 'cutoff': cutoff, 'computedAt': through.isoformat(),
        'featureScope': 'DECISION_TIME_STOCK_FEATURES', 'days': days, 'records': records,
        'reasonCounts': dict(Counter(r['reason'] for r in records)),
        'completeDays': sum(day['complete'] for day in days), 'cohortDays': len(days),
        'scope': '真实候选名单；历史输入仅用于按时间回放，不改写原始前瞻预测'}
