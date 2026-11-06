"""Same-day, same-universe, equal-count selection evidence, including abstentions."""
from collections import defaultdict

import numpy as np

PROTOCOL = 'overnight-same-day-selection-v1'
ARMS = ('INDEX', 'STOCK_ONLY', 'INDEX_ONLY')


def selection_weights(scores, count):
    if count == 0:
        return np.zeros(len(scores))
    boundary = np.sort(scores)[-count]
    weights = (scores > boundary).astype(float)
    tied = scores == boundary
    weights[tied] = (count - weights.sum()) / tied.sum()
    return weights


def paired_selection(rows, *, top_k=3, threshold=.5):
    if isinstance(top_k, bool) or not isinstance(top_k, int) or top_k < 1 or not 0 <= threshold <= 1:
        raise ValueError('同日选股评价参数无效')
    by_day, identities = defaultdict(list), set()
    for row in rows:
        key = (row['signalDate'], row['instrumentCode'])
        if key in identities:
            raise ValueError('同日同股不能重复进入配对')
        identities.add(key)
        if not isinstance(row['actualUp'], bool):
            raise ValueError('实际上涨标签必须明确')
        for arm in ARMS:
            value = row['scores'][arm]
            if isinstance(value, bool) or not np.isfinite(value) or not 0 <= value <= 1:
                raise ValueError('选股对照缺少有效概率')
        by_day[row['signalDate']].append(row)
    daily = []
    for day, values in sorted(by_day.items()):
        labels = np.array([r['actualUp'] for r in values], dtype=float)
        scores = {arm: np.array([r['scores'][arm] for r in values]) for arm in ARMS}
        if np.ptp(scores['INDEX_ONLY']) > 1e-12:
            raise ValueError('仅指数模型在同一天不能区分股票')
        forced = min(top_k, len(values))
        count = min(top_k, int(np.sum(scores['INDEX'] >= threshold)))
        daily.append({'signalDate': day, 'poolSize': len(values), 'poolUpRate': float(labels.mean()),
            'forcedCount': forced, 'candidateCount': count,
            'forced': {arm: float(selection_weights(p, forced) @ labels / forced) for arm, p in scores.items()},
            'matched': {arm: float(selection_weights(p, count) @ labels / count) if count else None
                        for arm, p in scores.items()}})
    return {'protocol': PROTOCOL, 'eligible': False, 'topK': top_k, 'threshold': threshold,
        'dayCount': len(daily), 'sampleCount': len(rows), 'noCandidateDays': sum(r['candidateCount'] == 0 for r in daily),
        'forcedTopK': summarize(daily, 'forced', 'forcedCount'),
        'matchedCandidates': summarize(daily, 'matched', 'candidateCount'),
        'weighting': 'EQUAL_DATE', 'tiePolicy': 'FRACTIONAL_AT_BOUNDARY',
        'countPolicy': 'SAME_DATE_UNIVERSE_AND_COUNT', 'days': daily}


def summarize(daily, field, count_key):
    selected = [r for r in daily if r[count_key] > 0]
    result = {'dayCount': len(daily), 'selectedDays': len(selected),
        'selectionCountPerArm': sum(r[count_key] for r in selected),
        'dayCoverage': len(selected) / len(daily) if daily else 0.,
        'precision': {arm: None for arm in ARMS}, 'differences': {}}
    if not selected:
        return result
    result['precision'] = {arm: float(np.mean([r[field][arm] for r in selected])) for arm in ARMS}
    block = min(5, len(selected))
    rng = np.random.default_rng(20261106)
    starts = rng.integers(0, len(selected), size=(2000, int(np.ceil(len(selected) / block))))
    indices = ((starts[:, :, None] + np.arange(block)) % len(selected)).reshape(2000, -1)[:, :len(selected)]
    for arm in ARMS[1:]:
        differences = np.array([r[field]['INDEX'] - r[field][arm] for r in selected])
        # Fixed two comparisons; these remain development diagnostics.
        low, high = np.quantile(differences[indices].mean(axis=1), [.0125, .9875])
        result['differences'][arm] = {'mean': float(differences.mean()), 'lower': float(low), 'upper': float(high)}
    return result
