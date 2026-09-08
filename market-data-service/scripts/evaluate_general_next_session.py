"""Read-only deterministic cached A-share comparison; never publishes a live model.

Run from market-data-service with OMP_NUM_THREADS=1 OPENBLAS_NUM_THREADS=1.
Legacy and new recipes share stocks, dates and test labels.
"""
import argparse
from dataclasses import replace
import hashlib
import json
from pathlib import Path
import sqlite3

import numpy as np
from threadpoolctl import threadpool_limits

from finscope_market_data.forecast.adaptive_classifiers import fit_candidates
from finscope_market_data.forecast.direction_evaluation import evaluate_direction
from finscope_market_data.forecast.industry_features import IndustryMembership
from finscope_market_data.forecast.joint_dataset import build_joint_dataset
from finscope_market_data.forecast.joint_training import (
    METHOD_FROZEN_THROUGH, PARAMETERS, _calibrated, _classifiers, _daily_mean, _matrix,
    _probability, temporal_split, train_joint_snapshot,
)
from finscope_market_data.forecast.training_universe import load_training_universe
from finscope_market_data.models import DailyBar


class ReadOnlyHistory:
    def __init__(self, path):
        self.connection = sqlite3.connect(Path(path).resolve().as_uri() + '?mode=ro', uri=True)
        self.connection.execute('BEGIN')

    def daily_bar_symbols(self):
        return [row[0] for row in self.connection.execute(
            "SELECT symbol_key FROM market_data_snapshot WHERE capability='DAILY_BARS' ORDER BY symbol_key")]

    def daily_history_as_of(self, symbol_key, as_of, limit=1061):
        rows = self.connection.execute(
            "SELECT bar.value FROM market_data_snapshot, json_each(payload_json, '$.data') AS bar "
            "WHERE capability='DAILY_BARS' AND symbol_key=? AND json_extract(bar.value, '$.trade_date')<=? "
            "ORDER BY json_extract(bar.value, '$.trade_date') DESC LIMIT ?", (symbol_key, as_of, limit)).fetchall()
        return [DailyBar.model_validate_json(row[0]) for row in reversed(rows)]


def legacy_comparison(dataset):
    # Former recipe: original columns, unweighted fit, raw selection Brier,
    # then calibration on a separate block and evaluation on final dates.
    indices = [i for i, code in enumerate(dataset.feature_codes) if not code.startswith('STATE_')]
    rows = tuple(replace(row, sample=replace(row.sample,
        features=tuple(row.sample.features[i] for i in indices))) for row in dataset.rows)
    split = temporal_split(rows)
    tx, ty = _matrix(split['train'])
    sx, sy = _matrix(split['selection'])
    cx, cy = _matrix(split['calibration'])
    vx, vy = _matrix(split['test'])
    models = _classifiers(tx, ty)
    scores = {code: _daily_mean((_probability(model, sx) - (sy > 0)) ** 2, split['selection'])
              for code, model in models.items()}
    selected = min(scores, key=lambda code: (scores[code], code))
    probability = _calibrated(models[selected], cx, cy, vx)
    return probability, dict(selectedClassifier=selected, selectionScores=scores,
        directionEvaluation=evaluate_direction(probability, vy > 0,
            [row.sample.signal_date for row in split['test']], {'PRIOR': np.full(len(vy), float(np.mean(ty > 0)))}))


def evaluate(args):
    store = ReadOnlyHistory(args.snapshots)
    try:
        histories = load_training_universe(store, as_of=args.as_of, max_symbols=args.max_symbols)
        benchmark = store.daily_history_as_of('SH:000300', args.as_of)
    finally:
        store.connection.close()
    records = json.loads(Path(args.memberships).read_text()) if Path(args.memberships).exists() else []
    memberships = tuple(IndustryMembership(item['industry'], item['available_on'], tuple(item['codes'])) for item in records)
    print(json.dumps({'stage': 'dataset', 'stocks': len(histories), 'asOf': args.as_of}), flush=True)
    dataset = build_joint_dataset(histories, as_of=args.as_of, market_bars=benchmark, memberships=memberships)
    print(json.dumps({'stage': 'training', 'rows': len(dataset.rows), 'features': len(dataset.feature_codes)}), flush=True)
    with threadpool_limits(limits=1):
        snapshot = train_joint_snapshot(dataset)
        legacy_probability, legacy = legacy_comparison(dataset)
        split = temporal_split(dataset.rows)
        cx, cy = _matrix(split['calibration'])
        vx, vy = _matrix(split['test'])
        chosen = snapshot['evidence']['selectedClassifier']
        models = fit_candidates(split['train'], dataset.feature_codes, PARAMETERS)
        probability = _calibrated(models[chosen], cx, cy, vx)
        comparison = evaluate_direction(probability, vy > 0, [r.sample.signal_date for r in split['test']],
                                        {'LEGACY': legacy_probability})
    snapshot['evidence']['evidenceKind'] = 'RETROSPECTIVE'
    snapshot['experiment'] = dict(methodFrozenThrough=METHOD_FROZEN_THROUGH,
        universeRule='Cached A-shares in deterministic SHA256 order; not a historical listing universe',
        requestedMaxSymbols=args.max_symbols, codes=sorted(histories),
        inputFingerprint=hashlib.sha256(json.dumps(dataset.history_fingerprints, sort_keys=True).encode()).hexdigest(),
        flatTestSampleCount=sum(r.sample.net_return == 0 for r in split['test']),
        legacy=legacy, pairedLegacyComparison=comparison,
        limitations=['Known historical period: diagnostic comparison, not new forward evidence',
                     'Cached universe has survivorship and selection bias; no whole-market accuracy claim'])
    output = Path(args.output)
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text(json.dumps(snapshot, ensure_ascii=False, allow_nan=False))
    print(json.dumps({'stage': 'complete', 'output': str(output), 'evidence': snapshot['evidence'],
                      'legacy': legacy, 'pairedLegacyComparison': comparison}, ensure_ascii=False), flush=True)


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--as-of', required=True)
    parser.add_argument('--snapshots', default='data/market-data-snapshots.db')
    parser.add_argument('--memberships', default='data/quant/industry-membership-history.json')
    parser.add_argument('--max-symbols', type=int, default=240)
    parser.add_argument('--output', required=True)
    evaluate(parser.parse_args())
