"""Reproduce the locked next-close experiment from local, real discovery caches.

Run from market-data-service: .venv/bin/python scripts/evaluate_next_session_joint.py --as-of YYYY-MM-DD
No fetching, retrospective market membership reconstruction, or future-outcome claims.
"""
import argparse
import asyncio
import json
from pathlib import Path

from finscope_market_data.discovery.service import StockDiscoveryService
from finscope_market_data.discovery.schemas import DiscoveryRequest
from finscope_market_data.forecast.context import build_aligned_context
from finscope_market_data.forecast.joint_dataset import build_joint_dataset
from finscope_market_data.forecast.joint_training import temporal_split, train_joint_snapshot
from finscope_market_data.forecast.joint_snapshot import JointSnapshotStore
from finscope_market_data.forecast.next_session import build_close_samples, _fit_at
from finscope_market_data.models import DataCapability, StockSymbol
from finscope_market_data.settings import Settings
from finscope_market_data.snapshot_store import SnapshotStore


async def evaluate(as_of: str, broad: bool = False, publish: bool = False):
    config = Settings()
    store = SnapshotStore(config.data_dir / 'market-data-snapshots.db')
    universe = json.loads((config.data_dir / 'stock-discovery-universe.json').read_text())

    class CachedMarket:
        async def bars(self, market, code):
            envelope = store.load(DataCapability.DAILY_BARS, StockSymbol(code=code, market=market))
            return [bar for bar in (envelope.data if envelope and envelope.data else []) if bar.trade_date <= as_of][-1500:]

    market = CachedMarket()
    output = config.data_dir / 'quant' / f'{"broad" if broad else "joint"}-experiment-{as_of}.json'
    destination = config.data_dir / 'quant' / 'next-session-joint.json' if publish else output
    service = StockDiscoveryService([], market, training_store=store if broad else None,
        joint_store=JointSnapshotStore(destination),
        constituent_snapshot_path=config.data_dir / 'stock-discovery-constituents.json')
    warnings = []
    candidates, histories = await service._admit(universe['members'], DiscoveryRequest(business_date=as_of), warnings)
    histories = {item.code: histories[item.code] for item in candidates if item.admitted}
    benchmark = await market.bars('SH', '000300')
    print(json.dumps({'stage': 'dataset', 'stocks': len(histories), 'asOf': as_of}), flush=True)
    if broad:
        snapshot = service._train_joint_snapshot(histories, as_of, benchmark, warnings)
        if snapshot is None:
            raise RuntimeError('; '.join(warnings))
    else:
        dataset = build_joint_dataset(histories, as_of=as_of, market_bars=benchmark)
        print(json.dumps({'stage': 'training', 'rows': len(dataset.rows), 'features': len(dataset.feature_codes)}), flush=True)
        snapshot = train_joint_snapshot(dataset)
    JointSnapshotStore(output).save(snapshot)
    print(json.dumps(snapshot['evidence'], ensure_ascii=False), flush=True)
    # Fixed representative codes chosen before observing the final test (not best performers).
    comparisons = []
    for code in ('000001', '000166', '000519', '000568', '600095', '600316', '601328', '601988'):
        bars = histories.get(code)
        if not bars:
            continue
        context = build_aligned_context(bars, market_bars=benchmark)
        samples = build_close_samples(bars, context)[-1000:]
        observations = []
        fit = None
        for index, sample in enumerate(samples):
            if not snapshot['evidence']['testStart'] <= sample.signal_date < snapshot['evidence']['testEnd']:
                continue
            if fit is None or len(observations) % 20 == 0:
                fit = _fit_at(samples[:index], sample.signal_date)
            probability = fit.predict(sample.features)[0]
            observations.append((probability - sample.positive) ** 2)
        predictions = snapshot['predictions'][code]
        comparisons.append(dict(code=code, sampleCount=len(observations),
            originalBrier=sum(observations) / len(observations), jointBrier=predictions['stockBrierScore']))
        print(json.dumps(comparisons[-1]), flush=True)
    snapshot['representativeComparison'] = comparisons
    JointSnapshotStore(output).save(snapshot)
    print(f'Experiment saved: {output}', flush=True)


if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    parser.add_argument('--as-of', required=True)
    parser.add_argument('--broad', action='store_true')
    parser.add_argument('--publish', action='store_true')
    args = parser.parse_args()
    asyncio.run(evaluate(args.as_of, args.broad, args.publish))
