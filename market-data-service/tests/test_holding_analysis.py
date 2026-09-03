from __future__ import annotations

from finscope_market_data.holding_analysis import (
    HoldingAnalysisRequest,
    analyze_holding,
)
from finscope_market_data.models import DailyBar, StockSymbol


def bar(day: str, close: float, high: float, low: float) -> DailyBar:
    return DailyBar(
        symbol=StockSymbol(market="SH", code="603618"),
        trade_date=day,
        open=close,
        high=high,
        low=low,
        close=close,
        volume=1_000_000,
        adjustment="QFQ",
    )


def test_analyzes_real_holding_path_from_entry_without_changing_ledger_profit() -> None:
    request = HoldingAnalysisRequest.model_validate(
        {
            "instrumentCode": "603618.SH",
            "entryDate": "2026-07-15",
            "costBasis": 32.49,
            "quantity": 100,
            "marketPrice": 39.25,
        }
    )
    bars = [
        bar("2026-07-14", 30.0, 31.0, 29.0),
        bar("2026-07-15", 32.0, 33.0, 31.0),
        bar("2026-07-16", 36.0, 37.0, 34.0),
        bar("2026-07-17", 33.0, 35.0, 30.0),
        bar("2026-07-20", 40.0, 41.0, 38.0),
    ]

    result = analyze_holding(request, bars, source_code="CACHE")

    assert result.latest_price == 39.25
    assert result.market_value == 3925
    assert result.unrealized_profit == 676
    assert round(result.holding_return, 6) == round(39.25 / 32.49 - 1, 6)
    assert result.observed_trading_days == 4
    assert result.series[0].trade_date == "2026-07-15"
    assert result.maximum_favorable_excursion > 0.20
    assert result.maximum_adverse_excursion < 0
    assert result.maximum_drawdown < 0
    assert result.maximum_drawdown_days == 4
    assert result.source_code == "CACHE"
    assert "QFQ_NORMALIZED" in result.method


def test_reports_partial_history_when_entry_predates_available_bars() -> None:
    request = HoldingAnalysisRequest.model_validate(
        {
            "instrumentCode": "603618.SH",
            "entryDate": "2025-01-01",
            "costBasis": 20,
            "quantity": 100,
            "marketPrice": 30,
        }
    )

    result = analyze_holding(
        request,
        [bar("2026-07-15", 30.0, 31.0, 29.0)],
        source_code="CACHE",
    )

    assert result.quality_status == "PARTIAL_HISTORY"
    assert result.warnings
