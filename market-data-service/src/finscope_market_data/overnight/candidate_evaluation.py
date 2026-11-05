"""Fixed top-three up-candidate diagnostic, with equal dates and unbiased ties."""
from collections import defaultdict

import numpy as np


def evaluate_candidates(rows, *, top_k=3, threshold=.5):
    if not isinstance(top_k, int) or top_k < 1 or not 0 <= threshold <= 1:
        raise ValueError('候选评价参数无效')
    return _evaluate(rows, top_k, threshold, 'probability')


def evaluate_ranking(rows, *, top_k=3):
    """Unbounded ordering scores are not probabilities; always rank the full day."""
    if not isinstance(top_k, int) or top_k < 1:
        raise ValueError('排序评价参数无效')
    return _evaluate(rows, top_k, None, 'score')


def _evaluate(rows, top_k, threshold, score_key):
    by_day, seen = defaultdict(list), set()
    for row in rows:
        identity = (row['signalDate'], row['instrumentCode'])
        if identity in seen:
            raise ValueError('候选评价不允许同日同股重复计数')
        seen.add(identity)
        p = row[score_key]
        if (not np.isfinite(p) or (score_key == 'probability' and not 0 <= p <= 1)
                or row['actualUp'] not in (False, True)):
            raise ValueError('候选概率或实际涨跌标签无效')
        by_day[row['signalDate']].append(row)
    daily = []
    for day, values in sorted(by_day.items()):
        p = np.array([row[score_key] for row in values])
        y = np.array([row['actualUp'] for row in values], dtype=float)
        eligible = np.ones(len(p), dtype=bool) if threshold is None else p >= threshold
        count = min(top_k, int(eligible.sum()))
        if not count:
            continue
        boundary = np.sort(p[eligible])[-count]
        weights = (p > boundary).astype(float)
        tied = p == boundary
        weights[tied] = (count - weights.sum()) / tied.sum()
        precision = float(weights @ y / count)
        daily.append({'signalDate': day, 'count': count, 'precision': precision,
                      'poolUpRate': float(y.mean()), 'lift': precision - float(y.mean())})
    result = {'topK': top_k, 'probabilityThreshold': threshold, 'dayCount': len(by_day),
              'selectedDays': len(daily), 'selectionCount': sum(item['count'] for item in daily),
              'dayCoverage': len(daily) / len(by_day) if by_day else 0.,
              'precision': None, 'poolUpRate': None, 'lift': None,
              'liftLower': None, 'liftUpper': None, 'tiePolicy': 'FRACTIONAL_AT_BOUNDARY',
              'weighting': 'EQUAL_DATE', 'evidenceKind': 'DIAGNOSTIC_ONLY'}
    if not daily:
        return result
    for name in ('precision', 'poolUpRate', 'lift'):
        result[name] = float(np.mean([item[name] for item in daily]))
    block = min(5, len(daily))
    rng = np.random.default_rng(20261105)
    starts = rng.integers(0, len(daily), size=(2000, int(np.ceil(len(daily) / block))))
    indices = ((starts[:, :, None] + np.arange(block)) % len(daily)).reshape(2000, -1)[:, :len(daily)]
    lift = np.array([item['lift'] for item in daily])
    interval = np.quantile(lift[indices].mean(axis=1), [.025, .975])
    result.update(liftLower=float(interval[0]), liftUpper=float(interval[1]))
    return result
