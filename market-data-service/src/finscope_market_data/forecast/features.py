from __future__ import annotations

from dataclasses import dataclass
import math
from typing import Sequence

from finscope_market_data.models import DailyBar
from finscope_market_data.forecast.context import AlignedForecastContext


FEATURE_CODES = (
    "MOMENTUM_5",
    "MOMENTUM_20",
    "MOMENTUM_60",
    "PRICE_VS_MA20",
    "PRICE_VS_MA60",
    "VOLATILITY_20",
    "AMOUNT_RATIO_20_60",
    "OVERNIGHT_GAP",
    "INTRADAY_RETURN",
    "DOWNSIDE_VOLATILITY_20",
    "DISTANCE_FROM_HIGH_60",
    "DISTANCE_FROM_LOW_60",
    "VOLATILITY_RATIO_10_60",
    "MARKET_MOMENTUM_5",
    "MARKET_MOMENTUM_20",
    "RELATIVE_MOMENTUM_20_MARKET",
    "MARKET_VOLATILITY_20",
    "MARKET_BETA_60",
    "INDUSTRY_MOMENTUM_20",
    "RELATIVE_MOMENTUM_20_INDUSTRY",
)


@dataclass(frozen=True)
class ForecastSample:
    signal_date: str
    entry_date: str
    exit_date: str
    features: tuple[float, ...]
    net_return: float

    @property
    def positive(self) -> bool:
        return self.net_return > 0


def build_samples(
    bars: Sequence[DailyBar],
    transaction_cost_rate: float,
    horizon_days: int = 20,
    context: AlignedForecastContext | None = None,
) -> list[ForecastSample]:
    if horizon_days < 1:
        raise ValueError("预测周期必须为正整数")
    ordered = _validated_bars(bars)
    samples: list[ForecastSample] = []
    for signal in range(60, len(ordered) - horizon_days - 1):
        entry = ordered[signal + 1]
        exit_bar = ordered[signal + horizon_days + 1]
        samples.append(
            ForecastSample(
                signal_date=ordered[signal].trade_date,
                entry_date=entry.trade_date,
                exit_date=exit_bar.trade_date,
                features=_features(ordered, signal, context),
                net_return=exit_bar.open / entry.open - 1.0 - transaction_cost_rate,
            )
        )
    return samples


def current_features(
    bars: Sequence[DailyBar], context: AlignedForecastContext | None = None
) -> tuple[float, ...]:
    ordered = _validated_bars(bars)
    if len(ordered) <= 60:
        raise ValueError("至少需要 61 根日线计算预测特征")
    return _features(ordered, len(ordered) - 1, context)


def _features(
    bars: Sequence[DailyBar], index: int, context: AlignedForecastContext | None
) -> tuple[float, ...]:
    close = bars[index].close
    target_returns = _returns(bars, index - 59, index)
    market = context.market_bars if context is not None else ()
    industry = context.industry_bars if context is not None else ()
    market_momentum_5 = _context_momentum(market, index, 5)
    market_momentum_20 = _context_momentum(market, index, 20)
    industry_momentum_20 = _context_momentum(industry, index, 20)
    return (
        close / bars[index - 5].close - 1.0,
        close / bars[index - 20].close - 1.0,
        close / bars[index - 60].close - 1.0,
        close / _average_close(bars, index - 19, index) - 1.0,
        close / _average_close(bars, index - 59, index) - 1.0,
        _volatility(bars, index - 19, index),
        _average_amount(bars, index - 19, index)
        / _average_amount(bars, index - 59, index)
        - 1.0,
        bars[index].open / bars[index - 1].close - 1.0,
        bars[index].close / bars[index].open - 1.0,
        _downside_volatility(target_returns[-20:]),
        close / max(item.high for item in bars[index - 59 : index + 1]) - 1.0,
        close / min(item.low for item in bars[index - 59 : index + 1]) - 1.0,
        _standard_deviation(target_returns[-10:])
        / max(_standard_deviation(target_returns), 1e-9)
        - 1.0,
        market_momentum_5,
        market_momentum_20,
        (
            close / bars[index - 20].close - 1.0 - market_momentum_20
            if _has_context_window(market, index, 20)
            else 0.0
        ),
        _context_volatility(market, index, 20),
        _beta(target_returns, _context_returns(market, index, 60)),
        industry_momentum_20,
        (
            close / bars[index - 20].close - 1.0 - industry_momentum_20
            if _has_context_window(industry, index, 20)
            else 0.0
        ),
    )


def _context_momentum(bars: Sequence[DailyBar | None], index: int, window: int) -> float:
    if not _has_context_window(bars, index, window):
        return 0.0
    current, previous = bars[index], bars[index - window]
    assert current is not None and previous is not None
    return current.close / previous.close - 1.0


def _has_context_window(
    bars: Sequence[DailyBar | None], index: int, window: int
) -> bool:
    return (
        index < len(bars)
        and index - window >= 0
        and bars[index] is not None
        and bars[index - window] is not None
    )


def _context_returns(
    bars: Sequence[DailyBar | None], index: int, window: int
) -> list[float]:
    if index >= len(bars) or index - window < 0:
        return []
    result: list[float] = []
    for offset in range(index - window + 1, index + 1):
        previous, current = bars[offset - 1], bars[offset]
        if previous is None or current is None:
            return []
        result.append(math.log(current.close / previous.close))
    return result


def _context_volatility(
    bars: Sequence[DailyBar | None], index: int, window: int
) -> float:
    values = _context_returns(bars, index, window)
    return _standard_deviation(values) * math.sqrt(252.0) if values else 0.0


def _returns(bars: Sequence[DailyBar], start: int, end: int) -> list[float]:
    return [math.log(bars[offset].close / bars[offset - 1].close) for offset in range(start, end + 1)]


def _standard_deviation(values: Sequence[float]) -> float:
    if len(values) < 2:
        return 0.0
    mean = sum(values) / len(values)
    return math.sqrt(sum((item - mean) ** 2 for item in values) / (len(values) - 1))


def _downside_volatility(values: Sequence[float]) -> float:
    downside = [min(0.0, item) for item in values]
    return math.sqrt(sum(item * item for item in downside) / len(downside)) * math.sqrt(252.0)


def _beta(target: Sequence[float], market: Sequence[float]) -> float:
    if len(target) != len(market) or len(market) < 2:
        return 0.0
    market_mean = sum(market) / len(market)
    target_mean = sum(target) / len(target)
    variance = sum((item - market_mean) ** 2 for item in market)
    if variance < 1e-12:
        return 0.0
    return sum(
        (left - target_mean) * (right - market_mean)
        for left, right in zip(target, market)
    ) / variance


def _average_close(bars: Sequence[DailyBar], start: int, end: int) -> float:
    return sum(bar.close for bar in bars[start : end + 1]) / (end - start + 1)


def _average_amount(bars: Sequence[DailyBar], start: int, end: int) -> float:
    return sum(float(bar.amount or 0) for bar in bars[start : end + 1]) / (end - start + 1)


def _volatility(bars: Sequence[DailyBar], start: int, end: int) -> float:
    returns = [
        math.log(bars[index].close / bars[index - 1].close)
        for index in range(start, end + 1)
    ]
    mean = sum(returns) / len(returns)
    variance = sum((value - mean) ** 2 for value in returns) / max(1, len(returns) - 1)
    return math.sqrt(variance) * math.sqrt(252.0)


def _validated_bars(bars: Sequence[DailyBar]) -> list[DailyBar]:
    if not bars:
        raise ValueError("行情历史不能为空")
    ordered = sorted(bars, key=lambda item: item.trade_date)
    symbol = ordered[0].symbol.cache_key
    dates: set[str] = set()
    for bar in ordered:
        if bar.symbol.cache_key != symbol or bar.trade_date in dates:
            raise ValueError("行情代码、日期或唯一性校验失败")
        dates.add(bar.trade_date)
        values = (bar.open, bar.high, bar.low, bar.close, bar.volume, bar.amount)
        if bar.adjustment != "QFQ" or any(value is None or value <= 0 for value in values):
            raise ValueError("预测只接受字段完整的前复权日线")
        if bar.high < max(bar.open, bar.close) or bar.low > min(bar.open, bar.close):
            raise ValueError("行情 OHLC 校验失败")
    return ordered
