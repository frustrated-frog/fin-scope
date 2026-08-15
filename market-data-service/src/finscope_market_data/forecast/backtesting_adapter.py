from __future__ import annotations

from dataclasses import dataclass
import math
import time
from typing import Sequence

import numpy as np
import pandas as pd
from backtesting import Backtest, Strategy

from finscope_market_data.forecast.features import ForecastSample
from finscope_market_data.forecast.walk_forward import WalkForwardObservation
from finscope_market_data.models import DailyBar


INITIAL_CASH = 1_000_000.0


@dataclass(frozen=True)
class SignalEvent:
    signal_date: str
    entry_date: str
    exit_date: str
    probability: float
    target_position: float
    reason: str


@dataclass(frozen=True)
class ShadowTrade:
    signal_date: str
    entry_date: str
    exit_date: str
    net_return: float
    cost: float


@dataclass(frozen=True)
class ShadowBacktestResult:
    available: bool
    trade_count: int
    total_return: float
    max_drawdown: float
    sharpe_ratio: float
    total_cost: float
    trades: tuple[ShadowTrade, ...]
    duration_ms: int
    error: str | None = None


def build_signal_events(
    samples: Sequence[ForecastSample],
    observations: Sequence[WalkForwardObservation],
    *,
    threshold: float,
) -> tuple[SignalEvent, ...]:
    if not 0 <= threshold <= 1:
        raise ValueError("影子回测信号阈值必须位于 0 到 1")
    samples_by_date = {item.signal_date: item for item in samples}
    next_available_date = ""
    events: list[SignalEvent] = []
    for observation in sorted(observations, key=lambda item: item.signal_date):
        sample = samples_by_date.get(observation.signal_date)
        if sample is None or observation.probability < threshold:
            continue
        if next_available_date and sample.signal_date < next_available_date:
            continue
        events.append(
            SignalEvent(
                signal_date=sample.signal_date,
                entry_date=sample.entry_date,
                exit_date=sample.exit_date,
                probability=observation.probability,
                target_position=1.0,
                reason=f"滚动样本外概率达到固定阈值 {threshold:.2f}",
            )
        )
        next_available_date = sample.exit_date
    return tuple(events)


def run_shadow_backtest(
    bars: Sequence[DailyBar],
    events: Sequence[SignalEvent],
    *,
    round_trip_cost: float,
    audit_start_date: str | None = None,
    audit_end_date: str | None = None,
) -> ShadowBacktestResult:
    started = time.perf_counter()
    if not 0 <= round_trip_cost < 1:
        raise ValueError("影子回测成本率不合法")
    ordered = sorted(bars, key=lambda item: item.trade_date)
    if not ordered:
        raise ValueError("影子回测缺少日线")
    if not events:
        return ShadowBacktestResult(
            available=True,
            trade_count=0,
            total_return=0.0,
            max_drawdown=0.0,
            sharpe_ratio=0.0,
            total_cost=0.0,
            trades=(),
            duration_ms=_elapsed_ms(started),
        )

    try:
        frame = _frame(ordered, events)
        backtest = Backtest(
            frame,
            _SignalStrategy,
            cash=INITIAL_CASH,
            commission=round_trip_cost / 2.0,
            trade_on_close=False,
            hedging=False,
            exclusive_orders=True,
            finalize_trades=True,
        )
        stats = backtest.run()
        trades = _trades(stats, events)
        total_return, max_drawdown, sharpe_ratio = _normalized_metrics(
            stats,
            audit_start_date=audit_start_date,
            audit_end_date=audit_end_date,
        )
        return ShadowBacktestResult(
            available=True,
            trade_count=len(trades),
            total_return=total_return,
            max_drawdown=max_drawdown,
            sharpe_ratio=sharpe_ratio,
            total_cost=_total_cost(stats),
            trades=trades,
            duration_ms=_elapsed_ms(started),
        )
    except Exception:
        return ShadowBacktestResult(
            available=False,
            trade_count=0,
            total_return=0.0,
            max_drawdown=0.0,
            sharpe_ratio=0.0,
            total_cost=0.0,
            trades=(),
            duration_ms=_elapsed_ms(started),
            error="影子回测引擎执行失败",
        )


class _SignalStrategy(Strategy):
    def init(self) -> None:
        return None

    def next(self) -> None:
        if self.position and bool(self.data.ExitSignal[-1]):
            self.position.close()
            return
        if not self.position and bool(self.data.EntrySignal[-1]):
            self.buy(size=0.999, tag=str(self.data.SignalDate[-1]))


def _frame(
    bars: Sequence[DailyBar], events: Sequence[SignalEvent]
) -> pd.DataFrame:
    dates = [item.trade_date for item in bars]
    index_by_date = {value: index for index, value in enumerate(dates)}
    entry_signals = [False] * len(bars)
    exit_signals = [False] * len(bars)
    signal_dates = [""] * len(bars)
    for event in events:
        signal_index = index_by_date.get(event.signal_date)
        exit_index = index_by_date.get(event.exit_date)
        if signal_index is None or exit_index is None or exit_index < 1:
            continue
        entry_signals[signal_index] = True
        signal_dates[signal_index] = event.signal_date
        exit_signals[exit_index - 1] = True
    return pd.DataFrame(
        {
            "Open": [item.open for item in bars],
            "High": [item.high for item in bars],
            "Low": [item.low for item in bars],
            "Close": [item.close for item in bars],
            "Volume": [item.volume for item in bars],
            "EntrySignal": entry_signals,
            "ExitSignal": exit_signals,
            "SignalDate": signal_dates,
        },
        index=pd.to_datetime(dates),
    )


def _trades(stats, events: Sequence[SignalEvent]) -> tuple[ShadowTrade, ...]:
    table = stats["_trades"]
    event_by_signal = {item.signal_date: item for item in events}
    result: list[ShadowTrade] = []
    for row in table.to_dict("records"):
        signal_date = str(row.get("Tag") or "")
        event = event_by_signal.get(signal_date)
        if event is None:
            continue
        result.append(
            ShadowTrade(
                signal_date=signal_date,
                entry_date=row["EntryTime"].date().isoformat(),
                exit_date=row["ExitTime"].date().isoformat(),
                net_return=_finite(float(row["ReturnPct"])),
                cost=_finite(float(row.get("Commission", 0.0)) / INITIAL_CASH),
            )
        )
    return tuple(result)


def _total_cost(stats) -> float:
    table = stats["_trades"]
    if "Commission" not in table:
        return 0.0
    return _finite(float(table["Commission"].sum()) / INITIAL_CASH)


def _normalized_metrics(
    stats,
    *,
    audit_start_date: str | None,
    audit_end_date: str | None,
) -> tuple[float, float, float]:
    curve = stats["_equity_curve"]["Equity"]
    if audit_start_date is not None:
        curve = curve.loc[pd.Timestamp(audit_start_date) :]
    if audit_end_date is not None:
        curve = curve.loc[: pd.Timestamp(audit_end_date)]
    values = curve.to_numpy(dtype=np.float64)
    if len(values) == 0 or values[0] <= 0:
        raise ValueError("影子回测审计区间缺少有效净值")
    daily = np.diff(values) / values[:-1]
    total_return = values[-1] / values[0] - 1.0
    drawdowns = 1.0 - values / np.maximum.accumulate(values)
    max_drawdown = float(np.max(drawdowns)) if len(drawdowns) else 0.0
    volatility = float(np.std(daily, ddof=1)) if len(daily) > 1 else 0.0
    sharpe_ratio = (
        float(np.mean(daily) / volatility * math.sqrt(242.0))
        if volatility > 1e-12
        else 0.0
    )
    return (
        _finite(total_return),
        _finite(max_drawdown),
        _finite(sharpe_ratio),
    )


def _elapsed_ms(started: float) -> int:
    return max(0, int((time.perf_counter() - started) * 1000))


def _finite(value: float) -> float:
    return value if math.isfinite(value) else 0.0
