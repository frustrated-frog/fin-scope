from __future__ import annotations

from datetime import datetime
import importlib
from typing import Any

import pytest

from finscope_market_data.discovery.schemas import DiscoverySector
from finscope_market_data.models import DataCapability, FinancialStatementType, StockSymbol
from finscope_market_data.providers.base import ProviderError


def _fuyao_module():
    return importlib.import_module("finscope_market_data.providers.fuyao")


class FakeAsyncApiClient:
    def __init__(self, responses: dict[str, dict[str, Any]]) -> None:
        self.responses = responses
        self.calls: list[tuple[str, dict[str, Any]]] = []

    async def get_data(self, path: str, params: dict[str, Any]) -> dict[str, Any]:
        self.calls.append((path, params))
        return self.responses[path]


class FakeSyncApiClient:
    def __init__(self, data: dict[str, Any]) -> None:
        self.data = data
        self.calls: list[tuple[str, dict[str, Any]]] = []

    def get_data(self, path: str, params: dict[str, Any]) -> dict[str, Any]:
        self.calls.append((path, params))
        return self.data

    def close(self) -> None:
        return None


@pytest.mark.asyncio
async def test_fuyao_daily_bars_map_forward_adjusted_contract() -> None:
    module = _fuyao_module()
    api = FakeAsyncApiClient(
        {
            "/api/a-share/prices/historical": {
                "timestamp": 1787673600000,
                "item": [
                    {
                        "date_ms": 1787587200000,
                        "open_price": 1298.0,
                        "high_price": 1312.5,
                        "low_price": 1291.2,
                        "close_price": 1304.0,
                        "volume": 3_100_000,
                        "turnover": 4_020_000_000,
                    }
                ],
            }
        }
    )
    provider = module.FuyaoMarketDataProvider(api=api)

    bars = await provider.fetch(
        DataCapability.DAILY_BARS,
        StockSymbol(market="SH", code="600519"),
        limit=120,
    )

    assert len(bars) == 1
    assert bars[0].trade_date == "2026-08-25"
    assert bars[0].close == 1304.0
    assert bars[0].amount == 4_020_000_000
    assert bars[0].adjustment == "QFQ"
    path, params = api.calls[0]
    assert path == "/api/a-share/prices/historical"
    assert params["thscode"] == "600519.SH"
    assert params["adjust"] == "forward"
    assert params["interval"] == "1d"


@pytest.mark.asyncio
async def test_fuyao_financials_map_three_statements_to_stable_concepts() -> None:
    module = _fuyao_module()
    common = {
        "thscode": "600519.SH",
        "period": "quarterly",
        "fiscal_year": 2026,
        "fiscal_period": "Q2",
        "report_date_ms": 1787241600000,
        "period_end_ms": 1782748800000,
        "currency": "CNY",
    }
    api = FakeAsyncApiClient(
        {
            "/api/a-share/financials/income-statements": {
                "item": [{**common, "operating_income": 1200, "parent_holder_net_profit": 210}]
            },
            "/api/a-share/financials/balance-sheets": {
                "item": [{**common, "assets_total": 3400, "total_debt": 1100}]
            },
            "/api/a-share/financials/cash-flow-statements": {
                "item": [{**common, "act_cash_flow_net": 180, "pay_fixed_assets_etc_cash": 90}]
            },
        }
    )
    provider = module.FuyaoMarketDataProvider(api=api)

    result = await provider.fetch(
        DataCapability.FINANCIAL_STATEMENTS,
        StockSymbol(market="SH", code="600519"),
        period_end="2026-06-30",
        report_type="HALF_YEAR",
        scope="CONSOLIDATED",
    )

    assert result.report.period_end == "2026-06-30"
    assert result.report.report_type == "HALF_YEAR"
    assert result.report.published_at == datetime.fromisoformat("2026-08-21T00:00:00+08:00")
    assert {item.statement_type for item in result.statements} == {
        FinancialStatementType.INCOME,
        FinancialStatementType.BALANCE_SHEET,
        FinancialStatementType.CASH_FLOW,
    }
    values = {
        value.concept_code: value.value
        for statement in result.statements
        for value in statement.values
    }
    assert values["REVENUE"] == "1200"
    assert values["NET_PROFIT_PARENT"] == "210"
    assert values["TOTAL_ASSETS"] == "3400"
    assert values["TOTAL_LIABILITIES"] == "1100"
    assert values["OPERATING_CASH_FLOW"] == "180"
    assert values["CAPITAL_EXPENDITURE"] == "90"


def test_fuyao_constituents_map_full_thscode_without_html_paging() -> None:
    module = _fuyao_module()
    api = FakeSyncApiClient(
        {
            "timestamp": 1787673600000,
            "item": [
                {"thscode": "600584.SH", "ticker": "600584", "name": "长电科技"},
                {"thscode": "002156.SZ", "ticker": "002156", "name": "通富微电"},
            ],
        }
    )
    provider = module.FuyaoConstituentProvider(api=api)
    sector = DiscoverySector(
        code="881121",
        name="半导体",
        category="INDUSTRY",
        source_code="AKSHARE_TONGHUASHUN_SECTOR_FLOW",
        source_family="TONGHUASHUN",
        source_rank=1,
        expected_constituent_count=2,
        retrieved_at="2026-08-26T10:00:00",
    )

    result = provider.constituents(sector)

    assert result.quality_status == "COMPLETE"
    assert result.source_family == "FUYAO_TONGHUASHUN"
    assert result.acquisition_mode == "REST_API"
    assert result.values == (
        ("600584", "SH", "长电科技"),
        ("002156", "SZ", "通富微电"),
    )
    assert api.calls == [
        (
            "/api/a-share-index/constituents/ths-stock-list",
            {"thscode": "881121.TI"},
        )
    ]


def test_fuyao_response_envelope_maps_business_rate_limit_to_retryable_error() -> None:
    module = _fuyao_module()

    with pytest.raises(ProviderError) as captured:
        module.unwrap_fuyao_response(
            {"code": 4001, "message": "Frequency limit exceeded", "data": None}
        )

    assert captured.value.error_type == "FUYAO_RATE_LIMITED"
    assert captured.value.retryable is True


def test_fuyao_response_envelope_rejects_success_without_object_data() -> None:
    module = _fuyao_module()

    with pytest.raises(ProviderError) as captured:
        module.unwrap_fuyao_response({"code": 0, "message": "success", "data": None})

    assert captured.value.error_type == "SCHEMA_DRIFT"
    assert captured.value.retryable is False
