from dataclasses import replace

import pytest

from test_forecast_service import bars
from finscope_market_data.forecast.context import build_aligned_context
from finscope_market_data.forecast.peer_context import with_peer_context
from finscope_market_data.forecast.industry_features import IndustryMembership
from finscope_market_data.forecast.features import current_features


def test_peer_momentum_uses_membership_known_on_signal_date_and_excludes_target():
    target = bars(90)
    histories = {'600519': target, '000001': bars(90), '000002': bars(90)}
    day = target[-1].trade_date
    members = [IndustryMembership('881001', day, tuple(histories))]
    base = build_aligned_context(target)
    context = with_peer_context(base, target, histories, members)
    assert all(value is None for value in context.peer_momentum_20[:-1])
    expected = target[-1].close / target[-21].close - 1
    assert current_features(target, context)[18] == pytest.approx(expected)
    assert context.industry_coverage == pytest.approx(1 / 90)
    future = [replace(members[0], available_on='2099-01-01')]
    assert with_peer_context(base, target, histories, future).industry_coverage == 0
    assert with_peer_context(base, target, {'600519': target, '000001': target}, members).industry_coverage == 0


def test_peer_features_do_not_read_future_prices():
    target = bars(90)
    histories = {'000001': bars(100), '000002': bars(100)}
    members = [IndustryMembership('881001', target[60].trade_date, ('600519', *histories))]
    base = build_aligned_context(target)
    first = with_peer_context(base, target, histories, members)
    changed = {code: [bar if bar.trade_date <= target[-1].trade_date else bar.model_copy(update={'close': 100000})
                      for bar in values] for code, values in histories.items()}
    assert with_peer_context(base, target, changed, members) == first
