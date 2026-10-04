"""Lagged, leave-one-out research-pool context. This is not full-market breadth."""
from datetime import datetime, timedelta
import hashlib
import json

import numpy as np

from finscope_market_data.forecast.trading_calendar import previous_session
from finscope_market_data.overnight.engine import expected_times, group_bars
from finscope_market_data.overnight.direction_dataset import build_direction_panel

INDEX_CODES = ('000300.SH', '000852.SH', '399006.SZ')
FEATURES = ('POOL_RETURN', 'POOL_UP_SHARE', 'POOL_DISPERSION', 'POOL_INTRADAY', 'POOL_LAST_30M',
            'RELATIVE_POOL_RETURN', 'RELATIVE_POOL_LAST_30M', 'POOL_DOWN_3_SHARE',
            'INDUSTRY_PEER_RETURN', 'INDUSTRY_PEER_UP_SHARE', 'RELATIVE_INDUSTRY_RETURN', 'INDUSTRY_AVAILABLE',
            'CSI300_RETURN', 'CSI300_AVAILABLE', 'CSI1000_RETURN', 'CSI1000_AVAILABLE', 'GEM_INDEX_RETURN', 'GEM_INDEX_AVAILABLE')
MIN_PEERS = 19


def context_time(day, cutoff):
    return datetime.fromisoformat(f'{day}T{cutoff}:00') - timedelta(minutes=10)


def stock_context(grouped, day, cutoff):
    stamp = context_time(day, cutoff)
    times = expected_times(stamp.strftime('%H:%M'))
    bars = grouped.get(day, {})
    prior = grouped.get(previous_session(day), {}).get('15:00')
    if prior is None or prior.amount <= 0 or any(t not in bars or bars[t].amount <= 0 for t in times):
        return None
    last = bars[times[-1]].close
    return {'return': last / prior.close - 1, 'intraday': last / bars[times[0]].open - 1,
            'last30': last / bars[times[-7]].close - 1, 'observedAt': stamp.isoformat()}


def context_features(code, own, snapshot, memberships=()):
    pool = snapshot['pool']
    peers = sorted(set(pool) - {code})
    if (own is None or len(peers) < MIN_PEERS
            or len(pool) / max(snapshot['expectedSymbols'], 1) < .75):
        return None
    returns = np.array([pool[peer]['return'] for peer in peers])
    short = float(np.mean([pool[peer]['last30'] for peer in peers]))
    mean = float(returns.mean())
    values = [mean, float(np.mean(returns > 0)), float(returns.std()),
              float(np.mean([pool[peer]['intraday'] for peer in peers])), short,
              own['return'] - mean, own['last30'] - short, float(np.mean(returns <= -.03))]
    active = {}
    for item in sorted(memberships, key=lambda item: (item.available_on, item.industry)):
        if item.available_on <= snapshot['signalDate']:
            active[item.industry] = set(item.codes)
    industry = set().union(*(members for members in active.values() if code in members)) if active else set()
    industry = sorted(industry.intersection(peers))
    if len(industry) >= 2:
        sector = [pool[peer]['return'] for peer in industry]
        values.extend([float(np.mean(sector)), float(np.mean(np.array(sector) > 0)), own['return'] - float(np.mean(sector)), 1.])
    else:
        values.extend([0.] * 4)
    for index in INDEX_CODES:
        record = snapshot.get('indices', {}).get(index)
        values.extend([record['return'], 1.] if record else [0., 0.])
    return values


def build_context_panel(store, members, cutoff, through, *, contexts=None, memberships=()):
    base, audit = build_direction_panel(store, members, cutoff, through)
    days = sorted({row['signalDate'] for row in base})
    pool_by_day = {day: {} for day in days}
    # Keep compact per-stock/day observations, not 120 full minute histories
    # resident at once. A long-lived sample library must remain bounded in RAM.
    for code in sorted(set(members)):
        grouped = group_bars(store.bars(code, through))
        for day in days:
            value = stock_context(grouped, datetime.fromisoformat(day).date(), cutoff)
            if value is not None:
                pool_by_day[day][code] = value
    snapshots = {}
    historical_reconstruction = 0
    for day in days:
        decision = datetime.fromisoformat(f'{day}T{cutoff}:00')
        snapshot = contexts.at(day, cutoff, decision) if contexts else None
        if snapshot is None:
            snapshot = {'signalDate': day, 'pool': pool_by_day[day], 'expectedSymbols': len(members), 'indices': {}}
            historical_reconstruction += 1
        snapshots[day] = snapshot
    rows = []
    for row in base:
        code, day = row['instrumentCode'], row['signalDate']
        own = pool_by_day[day].get(code)
        x = context_features(code, own, snapshots[day], memberships)
        if x is not None:
            rows.append({**row, 'features': row['features'] + x})
    audit.update(fingerprint=hashlib.sha256(json.dumps(rows, sort_keys=True, allow_nan=False).encode()).hexdigest(),
                 rows=len(rows), dayCount=len({r['signalDate'] for r in rows}),
                 symbolCount=len({r['instrumentCode'] for r in rows}), missingContextRows=len(base) - len(rows),
                 reconstructedDays=historical_reconstruction, contextLagMinutes=10,
                 industryRows=sum(row['features'][-7] == 1 for row in rows),
                 indexRows=sum(any(row['features'][i] == 1 for i in (-1, -3, -5)) for row in rows),
                 contextScope='固定研究股票池，非全市场；行业成员只在记录可用日期后生效')
    return rows, audit
