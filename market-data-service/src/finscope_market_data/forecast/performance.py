from __future__ import annotations

from dataclasses import dataclass
import math
from typing import Sequence

import numpy as np

from finscope_market_data.forecast.features import ForecastSample
from finscope_market_data.forecast.walk_forward import WalkForwardObservation
from finscope_market_data.models import DailyBar


TRADING_DAYS = 242.0


@dataclass(frozen=True)
class EquityPoint:
    trade_date: str
    strategy_nav: float
    benchmark_nav: float
    drawdown: float
    invested: bool


@dataclass(frozen=True)
class SimulatedTrade:
    signal_date: str
    entry_date: str
    exit_date: str
    probability: float
    net_return: float
    cost: float
    holding_days: int


@dataclass(frozen=True)
class PerformanceSummary:
    total_return: float
    annualized_return: float
    annualized_volatility: float
    sharpe_ratio: float
    daily_win_rate: float
    max_drawdown: float
    max_drawdown_start_date: str
    max_drawdown_trough_date: str
    max_drawdown_recovery_date: str | None
    max_drawdown_duration_days: int


@dataclass(frozen=True)
class BacktestReport:
    benchmark_label: str
    strategy: PerformanceSummary
    benchmark: PerformanceSummary
    excess_return: float
    trade_count: int
    profitable_trade_rate: float
    turnover: float
    total_cost: float
    holding_time_ratio: float
    average_holding_days: float
    trades: tuple[SimulatedTrade, ...]
    equity_curve: tuple[EquityPoint, ...]


@dataclass(frozen=True)
class AnnualPerformance:
    year: int
    strategy_return: float
    benchmark_return: float
    excess_return: float
    max_drawdown: float
    trade_count: int


@dataclass(frozen=True)
class RegimePerformance:
    regime: str
    label: str
    sample_days: int
    strategy_return: float
    benchmark_return: float
    excess_return: float
    sharpe_ratio: float
    max_drawdown: float
    trade_count: int
    holding_time_ratio: float


def simulate_strategy(
    bars: Sequence[DailyBar],
    samples: Sequence[ForecastSample],
    observations: Sequence[WalkForwardObservation],
    *,
    threshold: float,
    holding_days: int,
    round_trip_cost: float,
) -> BacktestReport:
    if not 0 <= threshold <= 1 or holding_days < 1 or not 0 <= round_trip_cost < 1:
        raise ValueError("虚拟策略参数不合法")
    ordered = sorted(bars, key=lambda item: item.trade_date)
    index_by_date = {bar.trade_date: index for index, bar in enumerate(ordered)}
    sample_by_signal = {item.signal_date: item for item in samples}
    eligible = [
        (item, sample_by_signal[item.signal_date])
        for item in observations
        if item.signal_date in sample_by_signal
        and sample_by_signal[item.signal_date].exit_date in index_by_date
    ]
    if not eligible:
        raise ValueError("没有可用于虚拟策略评估的样本外观察")

    start_index = index_by_date[eligible[0][0].signal_date]
    end_index = max(index_by_date[item.exit_date] for _, item in eligible)
    observation_by_signal = {item.signal_date: item for item, _ in eligible}
    half_cost = round_trip_cost / 2.0
    cash, units, total_cost = 1.0, 0.0, 0.0
    pending: tuple[WalkForwardObservation, ForecastSample] | None = None
    exit_index: int | None = None
    active: tuple[WalkForwardObservation, ForecastSample] | None = None
    entry_nav = 0.0
    trades: list[SimulatedTrade] = []
    daily_navs: list[float] = []
    invested_days = 0
    turnover_notional = 0.0

    benchmark_entry = min(end_index, start_index + 1)
    benchmark_units = (1.0 - half_cost) / ordered[benchmark_entry].open
    benchmark_navs: list[float] = []
    points: list[EquityPoint] = []

    for index in range(start_index, end_index + 1):
        bar = ordered[index]
        held_during_day = units > 0
        if pending is not None and units == 0:
            observation, candidate = pending
            buy_notional = cash
            buy_cost = buy_notional * half_cost
            units = (cash - buy_cost) / bar.open
            total_cost += buy_cost
            turnover_notional += buy_notional
            cash = 0.0
            exit_index = index_by_date[candidate.exit_date]
            active = pending
            entry_nav = units * bar.open
            pending = None
            held_during_day = True

        if units > 0 and exit_index == index:
            gross_proceeds = units * bar.close
            sell_cost = gross_proceeds * half_cost
            cash = gross_proceeds - sell_cost
            total_cost += sell_cost
            turnover_notional += gross_proceeds
            if active is None:
                raise ValueError("虚拟持仓缺少对应信号")
            observation, candidate = active
            trades.append(
                SimulatedTrade(
                    signal_date=observation.signal_date,
                    entry_date=candidate.entry_date,
                    exit_date=candidate.exit_date,
                    probability=observation.probability,
                    net_return=cash / entry_nav - 1.0,
                    cost=entry_nav * half_cost + sell_cost,
                    holding_days=holding_days,
                )
            )
            units, exit_index, active = 0.0, None, None

        nav = cash if units == 0 else units * bar.close
        daily_navs.append(nav)
        if held_during_day:
            invested_days += 1

        if units == 0 and pending is None:
            observation = observation_by_signal.get(bar.trade_date)
            candidate = sample_by_signal.get(bar.trade_date)
            if (
                observation is not None
                and candidate is not None
                and observation.probability >= threshold
                and index_by_date[candidate.exit_date] <= end_index
            ):
                pending = (observation, candidate)

        if index < benchmark_entry:
            benchmark_nav = 1.0
        else:
            benchmark_nav = benchmark_units * bar.close
            if index == end_index:
                benchmark_nav *= 1.0 - half_cost
        benchmark_navs.append(benchmark_nav)

    strategy_summary = _performance(
        [bar.trade_date for bar in ordered[start_index : end_index + 1]], daily_navs
    )
    benchmark_summary = _performance(
        [bar.trade_date for bar in ordered[start_index : end_index + 1]], benchmark_navs
    )
    peak = 0.0
    for offset, (nav, benchmark_nav) in enumerate(zip(daily_navs, benchmark_navs)):
        peak = max(peak, nav)
        drawdown = 0.0 if peak <= 0 else nav / peak - 1.0
        points.append(
            EquityPoint(
                trade_date=ordered[start_index + offset].trade_date,
                strategy_nav=_finite(nav),
                benchmark_nav=_finite(benchmark_nav),
                drawdown=_finite(drawdown),
                invested=False,
            )
        )
    invested_dates: set[str] = set()
    for trade in trades:
        entry = index_by_date[trade.entry_date]
        exit_at = index_by_date[trade.exit_date]
        invested_dates.update(ordered[index].trade_date for index in range(entry, exit_at + 1))
    points = [
        EquityPoint(
            item.trade_date,
            item.strategy_nav,
            item.benchmark_nav,
            item.drawdown,
            item.trade_date in invested_dates,
        )
        for item in points
    ]
    average_nav = float(np.mean(daily_navs)) if daily_navs else 1.0
    return BacktestReport(
        benchmark_label="同股买入并持有",
        strategy=strategy_summary,
        benchmark=benchmark_summary,
        excess_return=strategy_summary.total_return - benchmark_summary.total_return,
        trade_count=len(trades),
        profitable_trade_rate=(
            sum(item.net_return > 0 for item in trades) / len(trades) if trades else 0.0
        ),
        turnover=_finite(turnover_notional / 2.0 / max(average_nav, 1e-12)),
        total_cost=_finite(total_cost),
        holding_time_ratio=invested_days / max(1, len(daily_navs)),
        average_holding_days=(
            sum(item.holding_days for item in trades) / len(trades) if trades else 0.0
        ),
        trades=tuple(trades),
        equity_curve=tuple(points),
    )


def annual_performance(report: BacktestReport) -> tuple[AnnualPerformance, ...]:
    grouped: dict[int, list[EquityPoint]] = {}
    for point in report.equity_curve:
        grouped.setdefault(int(point.trade_date[:4]), []).append(point)
    result: list[AnnualPerformance] = []
    previous_strategy = 1.0
    previous_benchmark = 1.0
    for year, points in sorted(grouped.items()):
        strategy_return = points[-1].strategy_nav / previous_strategy - 1.0
        benchmark_return = points[-1].benchmark_nav / previous_benchmark - 1.0
        navs = [previous_strategy, *(item.strategy_nav for item in points)]
        peak = navs[0]
        drawdown = 0.0
        for nav in navs:
            peak = max(peak, nav)
            drawdown = max(drawdown, 0.0 if peak <= 0 else 1.0 - nav / peak)
        result.append(
            AnnualPerformance(
                year=year,
                strategy_return=_finite(strategy_return),
                benchmark_return=_finite(benchmark_return),
                excess_return=_finite(strategy_return - benchmark_return),
                max_drawdown=_finite(drawdown),
                trade_count=sum(int(item.entry_date[:4]) == year for item in report.trades),
            )
        )
        previous_strategy = points[-1].strategy_nav
        previous_benchmark = points[-1].benchmark_nav
    return tuple(result)


def regime_performance(
    bars: Sequence[DailyBar], report: BacktestReport
) -> tuple[RegimePerformance, ...]:
    ordered = sorted(bars, key=lambda item: item.trade_date)
    index_by_date = {bar.trade_date: index for index, bar in enumerate(ordered)}
    labels = {
        point.trade_date: _regime_at(ordered, index_by_date[point.trade_date])
        for point in report.equity_curve
    }
    result: list[RegimePerformance] = []
    names = {
        "UPTREND": "上行阶段",
        "RANGE": "震荡阶段",
        "DOWNTREND": "下行阶段",
    }
    for regime in ("UPTREND", "RANGE", "DOWNTREND"):
        strategy_returns: list[float] = []
        benchmark_returns: list[float] = []
        invested = 0
        points = report.equity_curve
        for index in range(1, len(points)):
            if labels[points[index].trade_date] != regime:
                continue
            strategy_returns.append(points[index].strategy_nav / points[index - 1].strategy_nav - 1.0)
            benchmark_returns.append(points[index].benchmark_nav / points[index - 1].benchmark_nav - 1.0)
            invested += int(points[index].invested)
        if not strategy_returns:
            continue
        strategy_return = _compound(strategy_returns)
        benchmark_return = _compound(benchmark_returns)
        conditional_nav = [1.0]
        for value in strategy_returns:
            conditional_nav.append(conditional_nav[-1] * (1.0 + value))
        peak = conditional_nav[0]
        max_drawdown = 0.0
        for nav in conditional_nav:
            peak = max(peak, nav)
            max_drawdown = max(max_drawdown, 1.0 - nav / peak)
        standard_deviation = float(np.std(strategy_returns, ddof=1)) if len(strategy_returns) > 1 else 0.0
        sharpe = (
            float(np.mean(strategy_returns) / standard_deviation * math.sqrt(TRADING_DAYS))
            if standard_deviation > 1e-12
            else 0.0
        )
        result.append(
            RegimePerformance(
                regime=regime,
                label=names[regime],
                sample_days=len(strategy_returns),
                strategy_return=_finite(strategy_return),
                benchmark_return=_finite(benchmark_return),
                excess_return=_finite(strategy_return - benchmark_return),
                sharpe_ratio=_finite(sharpe),
                max_drawdown=_finite(max_drawdown),
                trade_count=sum(
                    labels.get(item.signal_date) == regime for item in report.trades
                ),
                holding_time_ratio=invested / len(strategy_returns),
            )
        )
    return tuple(result)


def _regime_at(bars: Sequence[DailyBar], index: int) -> str:
    if index < 120:
        return "RANGE"
    change = bars[index].close / bars[index - 120].close - 1.0
    if change >= 0.10:
        return "UPTREND"
    if change <= -0.10:
        return "DOWNTREND"
    return "RANGE"


def _compound(returns: Sequence[float]) -> float:
    value = 1.0
    for item in returns:
        value *= 1.0 + item
    return value - 1.0


def _performance(dates: Sequence[str], navs: Sequence[float]) -> PerformanceSummary:
    if not navs:
        raise ValueError("绩效净值不能为空")
    daily = np.diff(np.asarray(navs, dtype=np.float64)) / np.asarray(
        navs[:-1], dtype=np.float64
    )
    total_return = navs[-1] / navs[0] - 1.0 if navs[0] > 0 else 0.0
    periods = max(1, len(navs) - 1)
    annualized = (
        math.pow(navs[-1] / navs[0], TRADING_DAYS / periods) - 1.0
        if navs[0] > 0 and navs[-1] > 0
        else 0.0
    )
    volatility = float(np.std(daily, ddof=1) * math.sqrt(TRADING_DAYS)) if len(daily) > 1 else 0.0
    sharpe = (
        float(np.mean(daily) / np.std(daily, ddof=1) * math.sqrt(TRADING_DAYS))
        if len(daily) > 1 and np.std(daily, ddof=1) > 1e-12
        else 0.0
    )
    peak_value = navs[0]
    peak_index = 0
    worst = 0.0
    worst_peak = 0
    trough = 0
    for index, nav in enumerate(navs):
        if nav > peak_value:
            peak_value, peak_index = nav, index
        drawdown = nav / peak_value - 1.0 if peak_value > 0 else 0.0
        if drawdown < worst:
            worst, worst_peak, trough = drawdown, peak_index, index
    recovery = next(
        (index for index in range(trough + 1, len(navs)) if navs[index] >= navs[worst_peak]),
        None,
    )
    duration_end = recovery if recovery is not None else len(navs) - 1
    return PerformanceSummary(
        total_return=_finite(total_return),
        annualized_return=_finite(annualized),
        annualized_volatility=_finite(volatility),
        sharpe_ratio=_finite(sharpe),
        daily_win_rate=float(np.mean(daily > 0)) if len(daily) else 0.0,
        max_drawdown=_finite(abs(worst)),
        max_drawdown_start_date=dates[worst_peak],
        max_drawdown_trough_date=dates[trough],
        max_drawdown_recovery_date=dates[recovery] if recovery is not None else None,
        max_drawdown_duration_days=max(0, duration_end - worst_peak),
    )


def _finite(value: float) -> float:
    return float(value) if math.isfinite(value) else 0.0
