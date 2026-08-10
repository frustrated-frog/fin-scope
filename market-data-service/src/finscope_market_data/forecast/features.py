from __future__ import annotations

from dataclasses import dataclass
import math
from typing import Sequence

from finscope_market_data.models import DailyBar


FEATURE_CODES = (
    "MOMENTUM_5",
    "MOMENTUM_20",
    "MOMENTUM_60",
    "PRICE_VS_MA20",
    "PRICE_VS_MA60",
    "VOLATILITY_20",
    "AMOUNT_RATIO_20_60",
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
                features=_features(ordered, signal),
                net_return=exit_bar.open / entry.open - 1.0 - transaction_cost_rate,
            )
        )
    return samples


def current_features(bars: Sequence[DailyBar]) -> tuple[float, ...]:
    ordered = _validated_bars(bars)
    if len(ordered) <= 60:
        raise ValueError("至少需要 61 根日线计算预测特征")
    return _features(ordered, len(ordered) - 1)


def _features(bars: Sequence[DailyBar], index: int) -> tuple[float, ...]:
    close = bars[index].close
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
    )


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
