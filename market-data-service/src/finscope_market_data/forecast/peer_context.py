"""Point-in-time equal-weight peer momentum, not a provider industry index."""
from dataclasses import replace
from pathlib import Path

from finscope_market_data.forecast.context import build_aligned_context
from finscope_market_data.forecast.industry_features import load_industry_memberships


def with_peer_context(context, target, histories, memberships):
    ordered = sorted(target, key=lambda bar: bar.trade_date)
    code = ordered[-1].symbol.code
    prices = {peer: {bar.trade_date: bar.close for bar in bars if bar.adjustment == 'QFQ'}
              for peer, bars in histories.items() if peer != code}
    values = []
    for index, bar in enumerate(ordered):
        active = {}
        for record in sorted(memberships, key=lambda item: (item.available_on, item.industry)):
            if record.available_on <= bar.trade_date:
                active[record.industry] = record.codes
        peers = set().union(*(set(codes) for codes in active.values() if code in codes)) - {code}
        window = ordered[max(0, index - 20):index + 1]
        returns = [prices[peer][bar.trade_date] / prices[peer][window[0].trade_date] - 1
                   for peer in sorted(peers & prices.keys())
                   if len(window) == 21 and all(day.trade_date in prices[peer] for day in window)]
        values.append(sum(returns) / len(returns) if len(returns) >= 2 else None)
    coverage = sum(value is not None for value in values) / len(values)
    return replace(context, peer_momentum_20=tuple(values), industry_coverage=coverage,
                   industry_code='ARCHIVED_EQUAL_WEIGHT_PEERS' if coverage else None,
                   industry_regime=None)


def research_context(target, *, market_bars=(), store=None, membership_path=None, history_path=None, histories=None):
    context = build_aligned_context(target, market_bars=market_bars)
    if membership_path is None:
        return context
    memberships = load_industry_memberships(Path(membership_path), Path(history_path) if history_path else None)
    code = target[-1].symbol.code
    peers = set().union(*(set(item.codes) for item in memberships if code in item.codes)) - {code}
    available = dict(histories or {})
    if store is not None:
        keys = {key.split(':')[1]: key for key in store.daily_bar_symbols()}
        for peer in sorted(peers & keys.keys()):
            if peer not in available:
                available[peer] = store.daily_history_as_of(keys[peer], target[-1].trade_date, limit=5000)
    return with_peer_context(context, target, available, memberships)
