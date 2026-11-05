"""Replay immutable production panels; diagnose ranking without tuning or publishing."""
import argparse
from collections import Counter, defaultdict
import gzip
import hashlib
import json
from pathlib import Path

from finscope_market_data.overnight.candidate_evaluation import evaluate_candidates
from finscope_market_data.overnight.direction_candidates import MULTISCALE_RECIPE
from finscope_market_data.overnight.direction_learning import fit_direction
from finscope_market_data.overnight.direction_selection import POLICY


def keyed(rows):
    result = {(r['signalDate'], r['instrumentCode']): r for r in rows}
    if len(result) != len(rows):
        raise ValueError('Duplicate stock/date observations')
    return result


def scores(rows):
    labeled = [{**row, 'actualUp': row['actualReturn'] > 0} for row in rows]
    daily = defaultdict(list)
    for row in labeled:
        daily[row['signalDate']].append(row)
    return {'thresholdSelection': evaluate_candidates(labeled),
            'rankingOnly': evaluate_candidates(labeled, threshold=0),
            'rawSelection': evaluate_candidates([{**r, 'probability': r['raw']} for r in labeled]),
            'constantProbabilityDays': sum(len({r['probability'] for r in values}) == 1 for values in daily.values()),
            'daily': [{'day': day, **evaluate_candidates(values)} for day, values in sorted(daily.items())]}


def replay(path):
    content = path.read_bytes()
    frozen = json.loads(gzip.decompress(content))
    artifact, panel = frozen['artifact'], frozen['panel']
    cutoff = artifact['profile']['cutoff']
    fits = {}
    for name, key, options in (
        ('INCUMBENT', 'directionRows', {}),
        ('CONTEXT', 'contextRows', {'selection_policy': POLICY, 'recipe': MULTISCALE_RECIPE}),
    ):
        print(cutoff, name, 'replaying', flush=True)
        fitted = fit_direction(panel[key], frozen['trainingStartedAt'], cutoff, include_checks=True, **options)
        if fitted is None:
            raise ValueError('Frozen panel cannot reproduce training')
        stored = artifact['closeDirection' if name == 'INCUMBENT' else 'contextDirection']['audit']['historical']
        actual = fitted['audit']['historical']
        for metric in ('accuracy', 'brierScore'):
            if abs(stored[metric] - actual[metric]) > 1e-6:
                raise ValueError(f'Frozen replay mismatch: {name} {metric}: stored={stored[metric]}, replay={actual[metric]}')
        fits[name] = fitted
    maps = {name: keyed(fit['checks']) for name, fit in fits.items()}
    identities = sorted(set(maps['INCUMBENT']) & set(maps['CONTEXT']))
    if not identities:
        raise ValueError('No paired test observations')
    for key in identities:
        a, b = maps['INCUMBENT'][key], maps['CONTEXT'][key]
        if a['actualReturn'] != b['actualReturn'] or a['exitAt'] != b['exitAt']:
            raise ValueError('Mismatched labels')
    aligned = {name: [values[key] for key in identities] for name, values in maps.items()}
    # Rank only on decision-time intraday return, fixed before reading results.
    daily = defaultdict(list)
    for row in aligned['INCUMBENT']:
        daily[row['signalDate']].append(row)
    momentum = []
    for rows in daily.values():
        values = sorted(set(r['features'][0] for r in rows))
        ranks = {value: (index + 1) / len(values) for index, value in enumerate(values)}
        momentum.extend({**row, 'probability': ranks[row['features'][0]], 'raw': ranks[row['features'][0]]} for row in rows)
    aligned['MOMENTUM_RANK'] = momentum
    aligned['UNIFORM_POOL'] = [{**row, 'probability': .5, 'raw': .5} for row in aligned['INCUMBENT']]
    shared_days = set(daily)
    for name in ('INCUMBENT', 'CONTEXT'):
        shared_days &= {row['signalDate'] for row in aligned[name] if row['probability'] >= .5}
    return {'cutoff': cutoff, 'artifactId': artifact['id'], 'inputSha256': hashlib.sha256(content).hexdigest(),
            'trainingStartedAt': frozen['trainingStartedAt'], 'reproducedStoredMetrics': True,
            'scope': 'FROZEN_RESEARCH_POOL_NOT_ACTUAL_DISCOVERY_COHORT', 'eligible': False,
            'pairedRows': len(identities), 'pairedDays': len(daily),
            'excludedRows': {name: len(values) - len(identities) for name, values in maps.items()},
            'sharedSelectionDays': sorted(shared_days),
            'results': {name: scores(rows) for name, rows in aligned.items()},
            'sharedDaySelection': {name: evaluate_candidates([{**r, 'actualUp': r['actualReturn'] > 0}
                for r in rows if r['signalDate'] in shared_days], threshold=.5 if name in fits else 0)
                for name, rows in aligned.items()},
            'foldModels': {name: dict(Counter(f['selected'] for f in fit['audit']['folds'])) for name, fit in fits.items()}}


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--inputs', nargs='+', type=Path, required=True)
    parser.add_argument('--output', type=Path, required=True)
    args = parser.parse_args()
    result = {'protocol': 'frozen-selection-audit-v1', 'topK': 3, 'threshold': .5,
              'limitations': ['Historical development diagnostic; not an independent holdout',
                             'Research pool differs from automatic discovery candidates',
                             'No model parameters or live gates changed'], 'results': []}
    args.output.parent.mkdir(parents=True, exist_ok=True)
    for path in args.inputs:
        result['results'].append(replay(path))
        args.output.write_text(json.dumps(result, ensure_ascii=False, indent=2, allow_nan=False))
        print('saved', path.name, flush=True)


if __name__ == '__main__':
    main()
