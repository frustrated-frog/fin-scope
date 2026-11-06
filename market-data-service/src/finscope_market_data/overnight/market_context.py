"""Point-in-time broad quotes; missing data is never reconstructed after close."""
from datetime import datetime
from itertools import groupby
import math
import statistics
from zoneinfo import ZoneInfo
from finscope_market_data.overnight.relative_strength import FEATURES as RELATIVE_FEATURES, relative_features

PROTOCOL = 'overnight-market-context-v1'
GROUPS = {
    'MARKET': ('MARKET_RETURN', 'MARKET_UP_SHARE', 'MARKET_DISPERSION'),
    'SENTIMENT': ('STRONG_3_SHARE', 'WEAK_3_SHARE', 'STRONG_FADE_SHARE'),
    'INDUSTRY': ('BROAD_PEER_RETURN', 'BROAD_PEER_UP_SHARE', 'RELATIVE_BROAD_PEER_RETURN'),
    'RELATIVE': RELATIVE_FEATURES,
}


def market_snapshot(frame, observed, received):
    quotes = {}
    expected = int(frame.attrs.get('expectedSymbols', 0))
    for row in frame.to_dict('records'):
        try:
            stamp = row['observed_at']
            if stamp.tzinfo is None:
                continue
            stamp = stamp.astimezone(ZoneInfo('Asia/Shanghai')).replace(tzinfo=None)
            code = str(row['code'])
            if len(code) != 8 or code[:2] not in ('sh', 'sz', 'bj') or not code[2:].isdigit():
                continue
            price, change, amount, high = [float(row[key]) for key in ('trade', 'change_pct', 'amount', 'high')]
            if (not observed <= stamp <= received or not all(math.isfinite(v) for v in (price, change, amount, high))
                    or price <= 0 or amount <= 0 or high < price or change <= -100):
                continue
            quotes[code[2:] + '.' + code[:2].upper()] = {
                'return': change / 100, 'drawdown': price / high - 1,
                'observedAt': stamp.isoformat(), 'receivedAt': received.isoformat()}
        except (KeyError, TypeError, ValueError, OverflowError):
            continue
    coverage = len(quotes) / expected if expected > 0 else 0
    sufficient = expected >= 1000 and .8 <= coverage <= 1
    return {'protocol': PROTOCOL, 'source': 'TENCENT_BATCH_A_SPOT',
            'observedAt': observed.isoformat(), 'receivedAt': received.isoformat(),
            'expectedSymbols': expected, 'capturedSymbols': len(quotes), 'coverage': coverage,
            'quality': 'SUFFICIENT' if sufficient else 'INSUFFICIENT_COVERAGE', 'quotes': quotes,
            'scope': '证券目录有效报价；强弱分布不是交易所涨跌停统计'}


def market_groups(code, snapshot):
    relative = relative_features(code, snapshot)
    result = {'RELATIVE': relative} if relative is not None else {}
    market = snapshot.get('market') or {}
    if market.get('protocol') != PROTOCOL or market.get('quality') != 'SUFFICIENT':
        return result
    quotes = market['quotes']
    peers = {key: value for key, value in quotes.items() if key != code}
    if len(peers) < 2:
        return result
    returns = [row['return'] for row in peers.values()]
    result.update({'MARKET': [statistics.mean(returns), sum(v > 0 for v in returns) / len(returns), statistics.pstdev(returns)],
              'SENTIMENT': [sum(v >= .03 for v in returns) / len(returns),
                            sum(v <= -.03 for v in returns) / len(returns),
                            sum(row['return'] >= .03 and row['drawdown'] <= -.02 for row in peers.values()) / len(peers)]})
    active = {}
    for item in sorted(snapshot.get('memberships', []), key=lambda value: (value['available_on'], value['industry'])):
        if item['available_on'] <= snapshot['signalDate']:
            active[item['industry']] = set(item['codes'])
    members = set().union(*(values for values in active.values() if code in values)) if active else set()
    members.discard(code)
    available = members & peers.keys()
    if code in quotes and len(available) >= 5 and len(available) / max(len(members), 1) >= .75:
        values = [peers[peer]['return'] for peer in sorted(available)]
        mean = statistics.mean(values)
        result['INDUSTRY'] = [mean, sum(v > 0 for v in values) / len(values), quotes[code]['return'] - mean]
    return result


def paired_market_panels(rows, contexts, cutoff):
    panels = {group: ([], []) for group in GROUPS}
    ordered = sorted(rows, key=lambda row: (row['signalDate'], row['instrumentCode']))
    # A broad snapshot contains thousands of quotes: retain only one date,
    # not the entire multi-month training history, in memory.
    for day, daily_rows in groupby(ordered, key=lambda row: row['signalDate']):
        snapshot = contexts.at(day, cutoff, datetime.fromisoformat(f'{day}T{cutoff}:00'))
        if not snapshot:
            continue
        for row in daily_rows:
            for group, values in market_groups(row['instrumentCode'], snapshot).items():
                base, enhanced = panels[group]
                base.append(row)
                enhanced.append({**row, 'features': [*row['features'], *values]})
    return panels
