from __future__ import annotations

from datetime import date, timedelta

import pytest

from finscope_market_data.forecast.features import ForecastSample
from finscope_market_data.forecast.performance import (
    annual_performance,
    regime_performance,
    simulate_strategy,
)
from finscope_market_data.forecast.walk_forward import WalkForwardObservation
from finscope_market_data.models import DailyBar, StockSymbol


def bars(count: int, falling_from: int | None = None) -> list[DailyBar]:
    symbol = StockSymbol(market="SH", code="603618")
    first = date(2020, 1, 1)
    result: list[DailyBar] = []
    for index in range(count):
        price = 100 + index
        if falling_from is not None and index >= falling_from:
            price = 100 + falling_from - (index - falling_from) * 3
        result.append(
            DailyBar(
                symbol=symbol,
                trade_date=(first + timedelta(days=index)).isoformat(),
                open=float(price),
                high=float(price + 1),
                low=float(max(1, price - 1)),
                close=float(price),
                volume=1000,
                amount=float(price * 1000),
                adjustment="QFQ",
            )
        )
    return result


def sample(history: list[DailyBar], signal: int, horizon: int = 20) -> ForecastSample:
    return ForecastSample(
        signal_date=history[signal].trade_date,
        entry_date=history[signal + 1].trade_date,
        exit_date=history[signal + horizon].trade_date,
        features=(0, 0, 0, 0, 0, 0, 0),
        net_return=history[signal + horizon].close / history[signal + 1].open - 1,
    )


def observation(item: ForecastSample, probability: float) -> WalkForwardObservation:
    return WalkForwardObservation(
        signal_date=item.signal_date,
        training_through="2019-12-31",
        probability=probability,
        baseline_probability=0.5,
        actual_return=item.net_return,
    )


def test_strategy_uses_next_open_non_overlapping_holding_and_same_stock_benchmark() -> None:
    history = bars(100)
    candidates = [sample(history, 10), sample(history, 15), sample(history, 40)]
    observations = [observation(item, 0.7) for item in candidates]

    report = simulate_strategy(
        history,
        candidates,
        observations,
        threshold=0.60,
        holding_days=20,
        round_trip_cost=0.0015,
    )

    assert report.benchmark_label == "同股买入并持有"
    assert len(report.trades) == 2
    assert report.trades[0].entry_date == history[11].trade_date
    assert report.trades[0].exit_date == history[30].trade_date
    assert report.trades[1].entry_date == history[41].trade_date
    assert report.total_cost > 0
    assert report.trade_count == 2
    assert 0 < report.holding_time_ratio < 1
    assert report.strategy.total_return < report.benchmark.total_return


def test_performance_reports_drawdown_duration_risk_and_costs() -> None:
    history = bars(100, falling_from=50)
    candidate = sample(history, 40)

    report = simulate_strategy(
        history,
        [candidate],
        [observation(candidate, 0.8)],
        threshold=0.60,
        holding_days=20,
        round_trip_cost=0.0015,
    )

    assert report.strategy.max_drawdown > 0
    assert report.strategy.max_drawdown_duration_days > 0
    assert report.strategy.annualized_volatility >= 0
    assert report.strategy.sharpe_ratio == pytest.approx(
        report.strategy.sharpe_ratio
    )
    assert report.turnover > 0
    assert report.total_cost > 0


def test_performance_splits_calendar_years_and_backward_looking_stock_regimes() -> None:
    history = bars(500)
    for index, bar in enumerate(history):
        if index < 170:
            price = 100.0
        elif index < 330:
            price = 100.0 * (1.002 ** (index - 170))
        else:
            price = 100.0 * (1.002**160) * (0.997 ** (index - 330))
        bar.open = price
        bar.high = price * 1.01
        bar.low = price * 0.99
        bar.close = price
        bar.amount = price * 1000
    candidates = [sample(history, index) for index in range(130, 470, 30)]
    observations = [observation(item, 0.7) for item in candidates]
    report = simulate_strategy(
        history,
        candidates,
        observations,
        threshold=0.60,
        holding_days=20,
        round_trip_cost=0.0015,
    )

    years = annual_performance(report)
    regimes = regime_performance(history, report)

    assert len(years) >= 2
    assert all(item.strategy_return == pytest.approx(item.strategy_return) for item in years)
    assert {item.regime for item in regimes} == {"UPTREND", "RANGE", "DOWNTREND"}
    assert all(item.sample_days > 0 for item in regimes)
