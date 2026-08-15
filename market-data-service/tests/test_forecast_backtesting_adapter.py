from __future__ import annotations

from datetime import date, timedelta

from finscope_market_data.forecast.backtesting_adapter import (
    build_signal_events,
    run_shadow_backtest,
)
from finscope_market_data.forecast.features import ForecastSample
from finscope_market_data.forecast.walk_forward import WalkForwardObservation
from finscope_market_data.models import DailyBar, StockSymbol


def bars(count: int = 40) -> list[DailyBar]:
    symbol = StockSymbol(market="SH", code="600519")
    first = date(2026, 1, 1)
    return [
        DailyBar(
            symbol=symbol,
            trade_date=(first + timedelta(days=index)).isoformat(),
            open=100.0 + index,
            high=101.0 + index,
            low=99.0 + index,
            close=100.5 + index,
            volume=1_000_000,
            amount=(100.5 + index) * 1_000_000,
            adjustment="QFQ",
        )
        for index in range(count)
    ]


def sample(history: list[DailyBar], signal: int, holding: int = 5) -> ForecastSample:
    return ForecastSample(
        signal_date=history[signal].trade_date,
        entry_date=history[signal + 1].trade_date,
        exit_date=history[signal + holding + 1].trade_date,
        features=(0.0,) * 7,
        net_return=history[signal + holding + 1].open / history[signal + 1].open - 1,
    )


def observation(item: ForecastSample, probability: float) -> WalkForwardObservation:
    return WalkForwardObservation(
        signal_date=item.signal_date,
        training_through="2025-01-01",
        probability=probability,
        baseline_probability=0.5,
        actual_return=item.net_return,
    )


def test_signal_events_apply_threshold_and_remove_overlapping_positions() -> None:
    history = bars()
    first = sample(history, 3)
    overlap = sample(history, 5)
    later = sample(history, 12)
    below = sample(history, 20)

    events = build_signal_events(
        [first, overlap, later, below],
        [
            observation(first, 0.7),
            observation(overlap, 0.8),
            observation(later, 0.65),
            observation(below, 0.59),
        ],
        threshold=0.60,
    )

    assert [item.signal_date for item in events] == [first.signal_date, later.signal_date]
    assert events[0].entry_date == first.entry_date
    assert events[0].exit_date == first.exit_date
    assert events[0].target_position == 1.0


def test_signal_events_only_reenter_after_the_previous_exit_is_executed() -> None:
    history = bars()
    first = sample(history, 3)
    signal_before_exit = sample(history, 8)
    signal_on_exit = sample(history, 9)

    events = build_signal_events(
        [first, signal_before_exit, signal_on_exit],
        [
            observation(first, 0.7),
            observation(signal_before_exit, 0.8),
            observation(signal_on_exit, 0.75),
        ],
        threshold=0.60,
    )

    assert [item.signal_date for item in events] == [
        first.signal_date,
        signal_on_exit.signal_date,
    ]


def test_shadow_backtest_uses_next_open_and_fixed_exit_open() -> None:
    history = bars()
    candidate = sample(history, 3)
    events = build_signal_events(
        [candidate], [observation(candidate, 0.75)], threshold=0.60
    )

    result = run_shadow_backtest(history, events, round_trip_cost=0.0015)

    assert result.available is True
    assert len(result.trades) == 1
    assert result.trades[0].signal_date == candidate.signal_date
    assert result.trades[0].entry_date == candidate.entry_date
    assert result.trades[0].exit_date == candidate.exit_date
    assert result.total_cost > 0
    assert result.max_drawdown >= 0


def test_shadow_backtest_returns_zero_trade_result_without_signals() -> None:
    result = run_shadow_backtest(bars(), [], round_trip_cost=0.0015)

    assert result.available is True
    assert result.trades == ()
    assert result.trade_count == 0
    assert result.total_return == 0.0


def test_shadow_backtest_degrades_when_third_party_engine_fails(monkeypatch) -> None:
    history = bars()
    candidate = sample(history, 3)
    events = build_signal_events(
        [candidate], [observation(candidate, 0.75)], threshold=0.60
    )

    def fail(*args, **kwargs):
        raise RuntimeError("engine exploded")

    monkeypatch.setattr("backtesting.Backtest.run", fail)

    result = run_shadow_backtest(history, events, round_trip_cost=0.0015)

    assert result.available is False
    assert result.error == "影子回测引擎执行失败"
