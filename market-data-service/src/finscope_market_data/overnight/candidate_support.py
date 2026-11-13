"""Descriptive feature support on matched dates; never changes model probabilities."""
import numpy as np

from finscope_market_data.overnight.direction_dataset import FEATURES
from finscope_market_data.overnight.joint_dataset import date_weights

PROTOCOL = 'overnight-candidate-support-v1'
FEATURE_CODES = ('INTRADAY_RETURN', 'RELATIVE_AMOUNT_5')


def quantiles(values, weights):
    order = np.argsort(values, kind='stable')
    cumulative = np.cumsum(weights[order]) / weights.sum()
    return [float(values[order[min(np.searchsorted(cumulative, q), len(values)-1)]]) for q in (.05, .5, .95)]


def candidate_support(rows, cohorts, days, cutoff, through):
    complete = {day['signalDate'] for day in days if day['complete'] and day.get('targetAt') and day['targetAt'] < through}
    pool = [row for row in rows if row['signalDate'] in complete and row['exitAt'] < through
            and not row.get('candidateOnly') and row.get('trainingEligible', True)]
    paired = {row['signalDate'] for row in pool}
    candidates = [row for row in rows if row['signalDate'] in paired and row['exitAt'] < through
        and row['instrumentCode'] in cohorts.get((row['signalDate'], cutoff), {}).get('candidates', {})]
    audit = {'protocol': PROTOCOL, 'status': 'INSUFFICIENT_DATA', 'dayCount': len(paired),
        'unpairedDays': len(complete - paired), 'poolRows': len(pool), 'candidateRows': len(candidates),
        'outsidePoolRows': sum(bool(row.get('candidateOnly')) for row in candidates), 'features': [],
        'weighting': 'EQUAL_DATE_THEN_STOCK', 'scope': '完整已到期候选日与同日可训练研究池对照；描述差异，不证明错误原因'}
    if not pool or not candidates:
        return audit
    pool_weights, candidate_weights = date_weights(pool), date_weights(candidates)
    for code in FEATURE_CODES:
        index = FEATURES.index(code)
        base = np.array([row['features'][index] for row in pool])
        actual = np.array([row['features'][index] for row in candidates])
        lower, median, upper = quantiles(base, pool_weights)
        outside = float(np.average((actual < lower) | (actual > upper), weights=candidate_weights))
        audit['features'].append({'code': code, 'poolMedian': median,
            'candidateMedian': quantiles(actual, candidate_weights)[1],
            'poolP05': lower, 'poolP95': upper, 'outsideCentralRangeRate': outside})
    audit['status'] = 'SHIFT_DETECTED' if any(row['outsideCentralRangeRate'] > .5 for row in audit['features']) else 'WITHIN_REFERENCE'
    return audit
