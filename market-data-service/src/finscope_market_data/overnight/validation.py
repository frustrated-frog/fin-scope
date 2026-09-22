"""All-history, date-balanced evaluation of frozen forecasts and their frozen priors."""
from collections import Counter, defaultdict
import math
from statistics import mean


def finite(value):
    return isinstance(value, (int, float)) and not isinstance(value, bool) and math.isfinite(value)


def daily_mean(rows, value):
    days = defaultdict(list)
    for row in rows:
        number = value(row)
        if number is not None:
            days[row['day']].append(number)
    return mean(mean(values) for values in days.values()) if days else None


def summarize(store):
    groups = {}
    total = 0
    for report in store.iter_history():
        total += 1
        key = (report['mode'], report['modelVersion'], report['cutoff'], report['costBps'], report['evidenceKind'])
        group = groups.setdefault(key, {'mode': key[0], 'modelVersion': key[1], 'cutoff': key[2],
            'costBps': key[3], 'evidenceKind': key[4], 'recordCount': 0, 'statuses': Counter(),
            'missingReasons': Counter(), 'rows': defaultdict(list)})
        group['recordCount'] += 1
        if report.get('status') != 'WATCH':
            group['missingReasons'][f"PREDICTION:{report.get('status', 'UNKNOWN')}"] += 1
        outcome = report.get('outcome') or {}
        group['statuses'][outcome.get('status', 'PENDING')] += 1
        group['missingReasons'].update(outcome.get('missingReasons', []))
        actuals = {x['target']: x for x in outcome.get('targets', [])}
        for target in report.get('targets', []):
            probability = target.get('upProbability')
            actual = actuals.get(target['target'], {}).get('actualNetReturn')
            if target.get('status') != 'WATCH' or not finite(probability) or not 0 <= probability <= 1 or not finite(actual):
                continue
            prior = target.get('baselineProbability')
            group['rows'][target['target']].append({'day': report['targetDate'], 'p': probability,
                'net': actual, 'prior': prior if finite(prior) and 0 <= prior <= 1 else None})
    result = []
    for group in groups.values():
        targets = []
        for name, rows in group.pop('rows').items():
            paired = [r for r in rows if r['prior'] is not None]
            selected = [r for r in rows if r['p'] >= .5]
            bins = []
            for low, high in ((0, .4), (.4, .5), (.5, .6), (.6, .7), (.7, 1.000001)):
                members = [r for r in rows if low <= r['p'] < high]
                bins.append({'lower': low, 'upper': min(high, 1), 'count': len(members),
                    'days': len({r['day'] for r in members}), 'predicted': daily_mean(members, lambda r: r['p']),
                    'actual': daily_mean(members, lambda r: float(r['net'] > 0))})
            # Fixed p>=0.5 selection, zero exposure on no-selection days, same dates/universe/costs.
            selected_by_day = defaultdict(list)
            for row in rows:
                selected_by_day[row['day']]
                if row['p'] >= .5:
                    selected_by_day[row['day']].append(row['net'])
            targets.append({'target': name, 'count': len(rows), 'days': len(selected_by_day),
                'accuracy': daily_mean(rows, lambda r: float((r['p'] >= .5) == (r['net'] > 0))),
                'brier': daily_mean(rows, lambda r: (r['p'] - (r['net'] > 0)) ** 2),
                'baselineCount': len(paired), 'pairedBrier': daily_mean(paired, lambda r: (r['p'] - (r['net'] > 0)) ** 2),
                'baselineBrier': daily_mean(paired, lambda r: (r['prior'] - (r['net'] > 0)) ** 2),
                'meanNetReturn': daily_mean(rows, lambda r: r['net']), 'selectedCount': len(selected),
                'selectedNetReturn': mean(mean(values) if values else 0 for values in selected_by_day.values()),
                'bins': bins})
        group['targets'] = targets
        result.append(group)
    return {'scope': 'ALL_ARCHIVED_RECORDS', 'recordCount': total, 'groups': result,
            'protocol': 'date-balanced-v1; selection=p>=0.5; no-selection-day=0',
            'limitations': ['全部收益均为固定时点价格代理，扣除冻结成本假设；不代表实际成交。',
                '一字价格区间剔除不能验证涨跌停队列；原始价格未校正公司行为。',
                '按目标交易日等权；版本、费用、时点、场景和生成时机分组，不混合历史回顾。',
                '概率基准为当时冻结的历史盈利比例；旧记录缺少基准时不追补。']}
