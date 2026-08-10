from __future__ import annotations

import math
from datetime import date, timedelta

import pytest

from finscope_market_data.forecast.factor_catalog import FACTORS
from finscope_market_data.forecast.features import (
    FEATURE_CODES,
    build_samples,
    current_features,
)
from finscope_market_data.models import DailyBar, StockSymbol


def bars(count: int) -> list[DailyBar]:
    symbol = StockSymbol(market="SH", code="600519")
    first = date(2018, 1, 2)
    result: list[DailyBar] = []
    for index in range(count):
        price = 80.0 + index * 0.35 + math.sin(index / 5.0)
        result.append(
            DailyBar(
                symbol=symbol,
                trade_date=(first + timedelta(days=index)).isoformat(),
                open=price - 0.2,
                high=price + 1.0,
                low=price - 1.0,
                close=price,
                volume=100_000 + index * 100,
                amount=(100_000 + index * 100) * price,
                adjustment="QFQ",
            )
        )
    return result


def test_features_do_not_read_bars_after_the_signal_date() -> None:
    history = bars(100)
    before = build_samples(history, transaction_cost_rate=0.002)
    signal_features = before[0].features

    history[70].close = 9_999
    history[70].high = 10_000
    after = build_samples(history, transaction_cost_rate=0.002)

    assert before[0].signal_date == history[60].trade_date
    assert after[0].features == pytest.approx(signal_features)


def test_label_uses_t_plus_one_entry_and_executable_future_open_after_costs() -> None:
    history = bars(100)
    history[61].open = 100
    history[81].open = 110
    history[81].high = 111

    sample = build_samples(history, transaction_cost_rate=0.002)[0]

    assert sample.entry_date == history[61].trade_date
    assert sample.exit_date == history[81].trade_date
    assert sample.net_return == pytest.approx(0.098)
    assert sample.positive is True


def test_feature_builder_requires_warmup_and_future_horizon() -> None:
    assert build_samples(bars(80), transaction_cost_rate=0.002) == []
    assert len(build_samples(bars(82), transaction_cost_rate=0.002)) == 1
    assert len(current_features(bars(81))) == 7


def test_feature_builder_supports_fixed_neighbor_horizons() -> None:
    history = bars(100)

    sample = build_samples(
        history, transaction_cost_rate=0.002, horizon_days=15
    )[0]

    assert sample.entry_date == history[61].trade_date
    assert sample.exit_date == history[76].trade_date
    assert tuple(item.code for item in FACTORS) == FEATURE_CODES


@pytest.mark.parametrize("horizon_days", [1, 5, 20])
def test_supported_horizons_hold_complete_trading_days(horizon_days: int) -> None:
    history = bars(100)

    sample = build_samples(
        history, transaction_cost_rate=0.002, horizon_days=horizon_days
    )[0]

    assert sample.entry_date == history[61].trade_date
    assert sample.exit_date == history[61 + horizon_days].trade_date
