"""Read archived predictions only: descriptive rank/calibration/error slices."""
import argparse
from collections import defaultdict
import gzip
import hashlib
import json
from pathlib import Path

import numpy as np


def rank_weights(probabilities, start, end):
    """Fractional overlap with descending rank interval; no arbitrary tie order."""
    p = np.asarray(probabilities, dtype=float)
    weights = np.zeros(len(p))
    cursor = 0
    for value in sorted(set(p), reverse=True):
        mask = p == value
        count = int(mask.sum())
        overlap = max(0., min(cursor + count, end) - max(cursor, start))
        weights[mask] = overlap / count
        cursor += count
    return weights


def summarize(rows, weights):
    daily = defaultdict(list)
    for row, weight in zip(rows, weights):
        if weight > 0:
            daily[row['signalDate']].append((row, float(weight)))
    fields = {'upRate': lambda r: r['actualReturn'] > 0, 'meanProbability': lambda r: r['probability'],
              'meanReturn': lambda r: r['actualReturn']}
    result = {'days': len(daily), 'rows': sum(len(v) for v in daily.values()),
              'weight': float(sum(weights)), 'upWeight': float(sum(w for r, w in zip(rows, weights) if r['actualReturn'] > 0))}
    for name, value in fields.items():
        averages = [sum(value(r) * w for r, w in values) / sum(w for _, w in values) for values in daily.values()]
        result[name] = float(np.mean(averages)) if averages else None
    return result


def inspect(rows, contexts, feature_names):
    daily = defaultdict(list)
    for index, row in enumerate(rows):
        daily[row['signalDate']].append(index)
    weights = {f'Q{i + 1}': np.zeros(len(rows)) for i in range(5)}
    selected = np.zeros(len(rows))
    for indices in daily.values():
        p = np.array([rows[i]['probability'] for i in indices])
        for i in range(5):
            weights[f'Q{i + 1}'][indices] = rank_weights(p, len(p) * i / 5, len(p) * (i + 1) / 5)
        eligible = np.array(indices)[p >= .5]
        selected[eligible] = rank_weights(p[p >= .5], 0, min(3, len(eligible)))
    ranks = {name: summarize(rows, w) for name, w in weights.items()}
    bins = []
    for low, high in ((0, .4), (.4, .5), (.5, .6), (.6, .7), (.7, 1.000001)):
        bins.append({'from': low, 'to': min(high, 1), **summarize(rows, [float(low <= r['probability'] < high) for r in rows])})
    features = [dict(zip(feature_names, contexts[(r['signalDate'], r['instrumentCode'])]['features'])) for r in rows]
    rules = {
        'INTRADAY_GE_3': lambda f: f['INTRADAY_RETURN'] >= .03,
        'INTRADAY_LT_3': lambda f: f['INTRADAY_RETURN'] < .03,
        'LAST30_NEGATIVE': lambda f: f['LAST_30M_RETURN'] < 0,
        'LAST30_NONNEGATIVE': lambda f: f['LAST_30M_RETURN'] >= 0,
        'DRAWDOWN_GE_2': lambda f: f['DRAWDOWN_FROM_HIGH'] <= -.02,
        'DRAWDOWN_LT_2': lambda f: f['DRAWDOWN_FROM_HIGH'] > -.02,
        'AMOUNT_BELOW_5D': lambda f: f['RELATIVE_AMOUNT_5'] < 0,
        'AMOUNT_AT_LEAST_5D': lambda f: f['RELATIVE_AMOUNT_5'] >= 0,
        'POOL_UP': lambda f: f['POOL_RETURN'] >= .005,
        'POOL_DOWN': lambda f: f['POOL_RETURN'] <= -.005,
        'POOL_MIXED': lambda f: -.005 < f['POOL_RETURN'] < .005,
        'INDUSTRY_PRESENT': lambda f: f['INDUSTRY_AVAILABLE'] == 1,
        'INDUSTRY_MISSING': lambda f: f['INDUSTRY_AVAILABLE'] == 0,
    }
    slices = {}
    for name, rule in rules.items():
        mask = np.array([rule(f) for f in features], dtype=float)
        slices[name] = {'selected': summarize(rows, selected * mask), 'pool': summarize(rows, mask)}
    examples = []
    for i in sorted(range(len(rows)), key=lambda i: -rows[i]['probability']):
        row = rows[i]
        if selected[i] > 0 and row['actualReturn'] <= 0:
            examples.append({key: row[key] for key in ('signalDate', 'instrumentCode', 'probability', 'actualReturn')}
                            | {'selectionWeight': float(selected[i]), 'features': {key: features[i][key] for key in
                               ('INTRADAY_RETURN', 'LAST_30M_RETURN', 'DRAWDOWN_FROM_HIGH', 'RELATIVE_AMOUNT_5', 'POOL_RETURN', 'INDUSTRY_AVAILABLE')}})
        if len(examples) == 5:
            break
    return {'rankQuintiles': ranks, 'probabilityBins': bins, 'selected': summarize(rows, selected),
            'slices': slices, 'highProbabilityErrors': examples,
            'constantRankDays': sum(len({rows[i]['probability'] for i in ids}) == 1 for ids in daily.values())}


def analyze(path):
    raw = path.read_bytes()
    evidence = json.loads(gzip.decompress(raw))
    if evidence.get('protocol') != 'frozen-selection-checks-v1':
        raise ValueError('Unrecognized archived prediction protocol')
    checks = evidence['checks']
    contexts = {(r['signalDate'], r['instrumentCode']): r for r in checks['CONTEXT']}
    identities = [(r['signalDate'], r['instrumentCode'], r['actualReturn'], r['exitAt']) for r in checks['CONTEXT']]
    if len(contexts) != len(identities):
        raise ValueError('Duplicate observations')
    for rows in checks.values():
        if [(r['signalDate'], r['instrumentCode'], r['actualReturn'], r['exitAt']) for r in rows] != identities:
            raise ValueError('Prediction populations or labels differ')
        if any(not np.isfinite(r['probability']) or not 0 <= r['probability'] <= 1
               or not np.isfinite(r['actualReturn']) for r in rows):
            raise ValueError('Invalid prediction or label')
    return {'protocol': 'selection-error-diagnostic-v1', 'cutoff': evidence['cutoff'], 'artifactId': evidence['artifactId'],
            'checksSha256': hashlib.sha256(raw).hexdigest(), 'environment': evidence['environment'],
            'scope': evidence['scope'], 'eligible': False, 'weighting': 'EQUAL_DATE_WITHIN_EACH_GROUP',
            'limitations': 'Descriptive associations only; groups may cover different dates; no feature attribution or parameter tuning',
            'results': {name: inspect(rows, contexts, evidence['features']['CONTEXT']) for name, rows in checks.items()}}


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--inputs', nargs='+', type=Path, required=True)
    parser.add_argument('--output', type=Path, required=True)
    args = parser.parse_args()
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps([analyze(path) for path in args.inputs], ensure_ascii=False, indent=2, allow_nan=False))


if __name__ == '__main__':
    main()
