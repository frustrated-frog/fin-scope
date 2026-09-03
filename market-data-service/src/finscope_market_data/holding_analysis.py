from __future__ import annotations

from datetime import date
from math import sqrt
from statistics import stdev
from typing import Literal

from pydantic import BaseModel, ConfigDict, Field

from finscope_market_data.models import DailyBar


def _to_camel(value: str) -> str:
    head, *tail = value.split("_")
    return head + "".join(part.capitalize() for part in tail)


class HoldingAnalysisModel(BaseModel):
    model_config = ConfigDict(alias_generator=_to_camel, populate_by_name=True)


class HoldingAnalysisRequest(HoldingAnalysisModel):
    instrument_code: str = Field(pattern=r"^\d{6}\.(SH|SZ|BJ)$")
    entry_date: date
    cost_basis: float = Field(gt=0)
    quantity: float = Field(gt=0)
    market_price: float = Field(gt=0)


class HoldingPathPoint(HoldingAnalysisModel):
    trade_date: str
    close: float
    return_since_entry: float
    drawdown: float


class HoldingAnalysisResult(HoldingAnalysisModel):
    instrument_code: str
    entry_date: date
    first_observed_date: date
    as_of_date: date
    holding_calendar_days: int = Field(ge=0)
    observed_trading_days: int = Field(ge=1)
    cost_basis: float = Field(gt=0)
    latest_price: float = Field(gt=0)
    quantity: float = Field(gt=0)
    total_cost: float
    market_value: float
    unrealized_profit: float
    holding_return: float
    maximum_favorable_excursion: float
    maximum_adverse_excursion: float
    maximum_drawdown: float
    maximum_drawdown_days: int = Field(ge=0)
    annualized_volatility: float
    quality_status: Literal["COMPLETE", "PARTIAL_HISTORY"]
    source_code: str
    method: str = "QFQ_NORMALIZED_TO_RAW_QUOTE_V1"
    warnings: list[str] = Field(default_factory=list)
    series: list[HoldingPathPoint] = Field(default_factory=list)


def analyze_holding(
    request: HoldingAnalysisRequest,
    bars: list[DailyBar],
    *,
    source_code: str,
) -> HoldingAnalysisResult:
    ordered = sorted(
        (bar for bar in bars if date.fromisoformat(bar.trade_date) >= request.entry_date),
        key=lambda bar: bar.trade_date,
    )
    if not ordered:
        raise ValueError("持仓日期之后没有可分析的日线")

    latest_adjusted_close = ordered[-1].close
    if latest_adjusted_close <= 0:
        raise ValueError("最新日线价格无效")
    scale = request.market_price / latest_adjusted_close
    closes = [bar.close * scale for bar in ordered]
    highs = [bar.high * scale for bar in ordered]
    lows = [bar.low * scale for bar in ordered]
    drawdowns, maximum_drawdown, maximum_drawdown_days = _drawdown_path(
        ordered,
        closes,
    )
    first_observed = date.fromisoformat(ordered[0].trade_date)
    as_of_date = date.fromisoformat(ordered[-1].trade_date)
    warnings = ["历史路径使用前复权日线，并按最新原始行情归一；真实盈亏以交易账本为准"]
    quality_status: Literal["COMPLETE", "PARTIAL_HISTORY"] = "COMPLETE"
    if (first_observed - request.entry_date).days > 7:
        quality_status = "PARTIAL_HISTORY"
        warnings.append("可用日线晚于建仓日期，区间风险指标仅覆盖现有历史")

    total_cost = request.cost_basis * request.quantity
    market_value = request.market_price * request.quantity
    series = [
        HoldingPathPoint(
            trade_date=bar.trade_date,
            close=round(close, 4),
            return_since_entry=round(close / request.cost_basis - 1, 8),
            drawdown=round(drawdown, 8),
        )
        for bar, close, drawdown in zip(ordered, closes, drawdowns)
    ]
    return HoldingAnalysisResult(
        instrument_code=request.instrument_code,
        entry_date=request.entry_date,
        first_observed_date=first_observed,
        as_of_date=as_of_date,
        holding_calendar_days=max(0, (as_of_date - request.entry_date).days),
        observed_trading_days=len(ordered),
        cost_basis=request.cost_basis,
        latest_price=request.market_price,
        quantity=request.quantity,
        total_cost=round(total_cost, 2),
        market_value=round(market_value, 2),
        unrealized_profit=round(market_value - total_cost, 2),
        holding_return=round(request.market_price / request.cost_basis - 1, 8),
        maximum_favorable_excursion=round(
            max(highs) / request.cost_basis - 1,
            8,
        ),
        maximum_adverse_excursion=round(
            min(lows) / request.cost_basis - 1,
            8,
        ),
        maximum_drawdown=round(maximum_drawdown, 8),
        maximum_drawdown_days=maximum_drawdown_days,
        annualized_volatility=round(_annualized_volatility(closes), 8),
        quality_status=quality_status,
        source_code=source_code,
        warnings=warnings,
        series=series,
    )


def _drawdown_path(
    bars: list[DailyBar],
    closes: list[float],
) -> tuple[list[float], float, int]:
    peak = closes[0]
    peak_date = date.fromisoformat(bars[0].trade_date)
    drawdowns: list[float] = []
    maximum_drawdown = 0.0
    maximum_drawdown_peak_date = peak_date
    maximum_drawdown_peak = peak
    maximum_drawdown_trough_index = 0
    for bar, close in zip(bars, closes):
        current_date = date.fromisoformat(bar.trade_date)
        if close >= peak:
            peak = close
            peak_date = current_date
        drawdown = close / peak - 1
        drawdowns.append(drawdown)
        if drawdown < maximum_drawdown:
            maximum_drawdown = drawdown
            maximum_drawdown_peak_date = peak_date
            maximum_drawdown_peak = peak
            maximum_drawdown_trough_index = len(drawdowns) - 1
    if maximum_drawdown == 0:
        return drawdowns, maximum_drawdown, 0
    recovery_date = date.fromisoformat(bars[-1].trade_date)
    for index in range(maximum_drawdown_trough_index + 1, len(closes)):
        if closes[index] >= maximum_drawdown_peak:
            recovery_date = date.fromisoformat(bars[index].trade_date)
            break
    maximum_drawdown_days = (recovery_date - maximum_drawdown_peak_date).days
    return drawdowns, maximum_drawdown, maximum_drawdown_days


def _annualized_volatility(closes: list[float]) -> float:
    if len(closes) < 3:
        return 0.0
    returns = [closes[index] / closes[index - 1] - 1 for index in range(1, len(closes))]
    return stdev(returns) * sqrt(252) if len(returns) >= 2 else 0.0
