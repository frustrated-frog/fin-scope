from __future__ import annotations

from dataclasses import dataclass
import math
from typing import Sequence

from finscope_market_data.models import DailyBar


@dataclass(frozen=True)
class AlignedForecastContext:
    market_code: str | None
    industry_code: str | None
    market_bars: tuple[DailyBar | None, ...]
    industry_bars: tuple[DailyBar | None, ...]
    market_coverage: float
    industry_coverage: float
    market_regime: str
    industry_regime: str | None


def build_aligned_context(
    target_bars: Sequence[DailyBar],
    *,
    market_bars: Sequence[DailyBar] = (),
    industry_bars: Sequence[DailyBar] = (),
) -> AlignedForecastContext:
    ordered = tuple(sorted(target_bars, key=lambda item: item.trade_date))
    if not ordered:
        raise ValueError("目标行情不能为空")
    market = _align(ordered, market_bars)
    industry = _align(ordered, industry_bars)
    return AlignedForecastContext(
        market_code=_single_code(market_bars),
        industry_code=_single_code(industry_bars),
        market_bars=market,
        industry_bars=industry,
        market_coverage=_coverage(market),
        industry_coverage=_coverage(industry),
        market_regime=_regime(market),
        industry_regime=_regime(industry) if any(industry) else None,
    )


def _single_code(bars: Sequence[DailyBar]) -> str | None:
    codes = {
        f"{item.symbol.code}.{item.symbol.market.value}"
        for item in bars
    }
    if len(codes) > 1:
        raise ValueError("同一上下文序列不能混入多个标的")
    return next(iter(codes), None)


def _align(
    target: Sequence[DailyBar], source: Sequence[DailyBar]
) -> tuple[DailyBar | None, ...]:
    by_date = {item.trade_date: item.model_copy(deep=True) for item in source}
    return tuple(by_date.get(item.trade_date) for item in target)


def _coverage(bars: Sequence[DailyBar | None]) -> float:
    return sum(item is not None for item in bars) / len(bars)


def _regime(bars: Sequence[DailyBar | None]) -> str:
    available = [item for item in bars if item is not None]
    if len(available) < 61:
        return "UNAVAILABLE"
    closes = [item.close for item in available]
    returns = [math.log(current / previous) for previous, current in zip(closes[-21:-1], closes[-20:])]
    volatility = _standard_deviation(returns) * math.sqrt(252.0)
    momentum = closes[-1] / closes[-21] - 1.0
    trend = closes[-1] / (sum(closes[-60:]) / 60.0) - 1.0
    if volatility >= 0.35:
        return "HIGH_VOLATILITY"
    if momentum >= 0.005 and trend >= 0:
        return "UPTREND"
    if momentum <= -0.005 and trend <= 0:
        return "DOWNTREND"
    return "RANGE"


def _standard_deviation(values: Sequence[float]) -> float:
    if len(values) < 2:
        return 0.0
    mean = sum(values) / len(values)
    return math.sqrt(sum((item - mean) ** 2 for item in values) / (len(values) - 1))
