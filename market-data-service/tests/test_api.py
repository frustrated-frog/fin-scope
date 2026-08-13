from __future__ import annotations

from datetime import UTC, date, datetime, timedelta
from pathlib import Path
from typing import Any

from fastapi.testclient import TestClient

from finscope_market_data.app import create_app
from finscope_market_data.health import ProviderHealthRegistry
from finscope_market_data.models import (
    CapitalFlowData,
    CapitalFlowPoint,
    DailyBar,
    DataCapability,
    FinancialReportMeta,
    FinancialStatement,
    FinancialStatementType,
    FinancialStatementValue,
    FinancialStatementsData,
    StockProfile,
    StockQuote,
    StockSymbol,
)
from finscope_market_data.router import ProviderRouter
from finscope_market_data.snapshot_store import SnapshotStore
from finscope_market_data.discovery.schemas import DiscoveryFunnel, DiscoveryReport


class MultiCapabilityProvider:
    provider_code = "FIXTURE"
    provider_family = "FIXTURE"
    priority = 10
    capabilities = set(DataCapability)

    def supports(self, capability: DataCapability, symbol: StockSymbol) -> bool:
        return True

    async def fetch(self, capability: DataCapability, symbol: StockSymbol, **kwargs: Any) -> Any:
        observed = datetime(2026, 7, 16, 10, 30, tzinfo=UTC)
        if capability is DataCapability.QUOTE:
            return StockQuote(symbol=symbol, name="贵州茅台", price=1480.5, observed_at=observed)
        if capability is DataCapability.DAILY_BARS:
            return [
                DailyBar(
                    symbol=symbol,
                    trade_date="2026-07-16",
                    open=1475,
                    high=1490,
                    low=1470,
                    close=1480.5,
                    volume=1000,
                )
            ]
        if capability is DataCapability.CAPITAL_FLOW:
            point = CapitalFlowPoint(
                symbol=symbol,
                granularity="MINUTE_1",
                observed_at=observed,
                main_net_inflow=1_000_000,
            )
            return CapitalFlowData(minute_points=[point])
        if capability is DataCapability.FINANCIAL_STATEMENTS:
            return FinancialStatementsData(
                report=FinancialReportMeta(
                    symbol=symbol,
                    period_end="2026-06-30",
                    report_type="HALF_YEAR",
                    scope="CONSOLIDATED",
                    published_at=observed,
                    audited=False,
                    currency="CNY",
                ),
                statements=[
                    FinancialStatement(
                        statement_type=FinancialStatementType.INCOME,
                        values=[
                            FinancialStatementValue(
                                source_label="营业收入",
                                concept_code="REVENUE",
                                period_role="CURRENT_YTD",
                                value="1200000000.12",
                                source_field="TOTAL_OPERATE_INCOME",
                            )
                        ],
                    ),
                    FinancialStatement(
                        statement_type=FinancialStatementType.BALANCE_SHEET,
                        values=[],
                    ),
                    FinancialStatement(
                        statement_type=FinancialStatementType.CASH_FLOW,
                        values=[],
                    ),
                ],
            )
        return StockProfile(symbol=symbol, name="贵州茅台", industry="白酒")


class ForecastDailyBarProvider:
    provider_code = "FORECAST_FIXTURE"
    provider_family = "FIXTURE"
    priority = 10
    capabilities = {DataCapability.DAILY_BARS}

    def __init__(self, count: int = 400) -> None:
        self.count = count
        self.requests: list[tuple[StockSymbol, int]] = []

    def supports(self, capability: DataCapability, symbol: StockSymbol) -> bool:
        return capability is DataCapability.DAILY_BARS

    async def fetch(self, capability: DataCapability, symbol: StockSymbol, **kwargs: Any) -> Any:
        self.requests.append((symbol, int(kwargs["limit"])))
        first = date(2020, 1, 1)
        return [
            DailyBar(
                symbol=symbol,
                trade_date=(first + timedelta(days=index)).isoformat(),
                open=100 + index * 0.02,
                high=101 + index * 0.02,
                low=99 + index * 0.02,
                close=100.5 + index * 0.02,
                volume=100_000 + index,
                amount=(100_000 + index) * (100.5 + index * 0.02),
                adjustment="QFQ",
            )
            for index in range(self.count)
        ]


class LegacyThenAdjustedForecastProvider(ForecastDailyBarProvider):
    async def fetch(
        self, capability: DataCapability, symbol: StockSymbol, **kwargs: Any
    ) -> Any:
        bars = await super().fetch(capability, symbol, **kwargs)
        if len(self.requests) == 1:
            return [bar.model_copy(update={"adjustment": "NONE"}) for bar in bars]
        return bars


class EmptyForecastDailyBarProvider(ForecastDailyBarProvider):
    async def fetch(
        self, capability: DataCapability, symbol: StockSymbol, **kwargs: Any
    ) -> Any:
        self.requests.append((symbol, int(kwargs["limit"])))
        return []


def client(tmp_path: Path, providers: list[Any]) -> TestClient:
    router = ProviderRouter(
        providers=providers,
        snapshots=SnapshotStore(tmp_path / "snapshots.db"),
        health=ProviderHealthRegistry(),
        max_retries=0,
    )
    return TestClient(create_app(router))


class FakeDiscoveryService:
    async def discover(self, request):
        return DiscoveryReport(
            policy_version=request.policy_version,
            as_of_date="2026-08-14",
            source_code="FIXTURE_SECTORS",
            source_family="FIXTURE",
            quality_status="FRESH_PRIMARY",
            retrieved_at="2026-08-14T15:35:00",
            data_fingerprint="a" * 64,
            budget=request.budget,
            sectors=[],
            candidates=[],
            deep_evidence=[],
            final_candidates=[],
            funnel=DiscoveryFunnel(),
        )


def test_health_and_provider_health_endpoints(tmp_path: Path) -> None:
    api = client(tmp_path, [MultiCapabilityProvider()])

    assert api.get("/health").json()["status"] == "UP"
    providers = api.get("/v1/providers/health").json()
    assert providers[0]["provider_code"] == "FIXTURE"
    assert set(providers[0]["capabilities"]) == {item.value for item in DataCapability}


def test_stock_discovery_endpoint_returns_versioned_report(tmp_path: Path) -> None:
    router = ProviderRouter(
        providers=[MultiCapabilityProvider()],
        snapshots=SnapshotStore(tmp_path / "snapshots.db"),
        health=ProviderHealthRegistry(),
        max_retries=0,
    )
    api = TestClient(create_app(router, discovery=FakeDiscoveryService()))

    response = api.post("/v1/quant/stock-discoveries", json={"budget": 5000})

    assert response.status_code == 200
    assert response.json()["schema_version"] == "1.0.0"
    assert response.json()["budget"] == 5000


def test_readiness_requires_writable_snapshot_store_and_a_provider(tmp_path: Path) -> None:
    ready = client(tmp_path / "ready", [MultiCapabilityProvider()]).get("/ready")
    unavailable = client(tmp_path / "empty", []).get("/ready")

    assert ready.status_code == 200
    assert ready.json() == {
        "status": "READY",
        "checks": {"snapshot_store": "UP", "providers": "UP"},
    }
    assert unavailable.status_code == 503
    assert unavailable.json()["status"] == "NOT_READY"
    assert unavailable.json()["checks"]["providers"] == "NO_PROVIDERS"


def test_stock_data_endpoints_expose_normalized_rich_data(tmp_path: Path) -> None:
    api = client(tmp_path, [MultiCapabilityProvider()])

    quote = api.get("/v1/stocks/SH/600519/quote")
    bars = api.get("/v1/stocks/SH/600519/daily-bars?limit=20")
    capital = api.get("/v1/stocks/SH/600519/capital-flow")
    profile = api.get("/v1/stocks/SH/600519/profile")

    assert quote.status_code == 200
    assert quote.json()["data"]["price"] == 1480.5
    assert bars.json()["data"][0]["trade_date"] == "2026-07-16"
    assert capital.json()["data"]["minute_points"][0]["main_net_inflow"] == 1_000_000
    assert profile.json()["data"]["industry"] == "白酒"


def test_daily_bar_endpoint_accepts_single_stock_research_history_limit(tmp_path: Path) -> None:
    api = client(tmp_path, [MultiCapabilityProvider()])

    response = api.get("/v1/stocks/SH/600519/daily-bars?limit=5000")

    assert response.status_code == 200


def test_single_stock_forecast_endpoint_uses_full_qfq_history(tmp_path: Path) -> None:
    provider = ForecastDailyBarProvider()
    api = client(tmp_path, [provider])

    response = api.post(
        "/v1/quant/single-stock-forecasts", json={"code": "600519", "horizonDays": 1}
    )

    assert response.status_code == 200
    assert provider.requests == [
        (StockSymbol(market="SH", code="600519"), 5000),
        (StockSymbol(market="SH", code="000300"), 5000),
    ]
    body = response.json()
    assert body["instrumentCode"] == "600519.SH"
    assert body["status"] == "INSUFFICIENT_DATA"
    assert body["barCount"] == 400
    assert body["upProbability"] is None
    assert body["horizonDays"] == 1
    assert body["context"]["market"]["code"] == "000300.SH"
    assert body["context"]["market"]["coverage"] == 1.0
    assert body["context"]["industry"]["status"] == "UNAVAILABLE"
    assert body["qlibReference"]["runtimeDependency"] is False


def test_single_stock_forecast_endpoint_rejects_unregistered_horizon(tmp_path: Path) -> None:
    api = client(tmp_path, [ForecastDailyBarProvider()])

    response = api.post(
        "/v1/quant/single-stock-forecasts", json={"code": "600519", "horizonDays": 3}
    )

    assert response.status_code == 422


def test_single_stock_forecast_endpoint_returns_structured_error_for_empty_history(
    tmp_path: Path,
) -> None:
    api = client(tmp_path, [EmptyForecastDailyBarProvider()])

    response = api.post(
        "/v1/quant/single-stock-forecasts", json={"code": "600519", "horizonDays": 5}
    )

    assert response.status_code == 503
    assert response.json()["detail"] == "前复权历史行情当前不可用"


def test_single_stock_forecast_endpoint_refreshes_legacy_unadjusted_cache(
    tmp_path: Path,
) -> None:
    provider = LegacyThenAdjustedForecastProvider()
    api = client(tmp_path, [provider])

    response = api.post("/v1/quant/single-stock-forecasts", json={"code": "603618"})

    assert response.status_code == 200
    assert provider.requests == [
        (StockSymbol(market="SH", code="603618"), 5000),
        (StockSymbol(market="SH", code="603618"), 5000),
        (StockSymbol(market="SH", code="000300"), 5000),
    ]
    assert response.json()["barCount"] == 400


def test_single_stock_forecast_endpoint_validates_six_digit_code(tmp_path: Path) -> None:
    api = client(tmp_path, [ForecastDailyBarProvider()])

    response = api.post("/v1/quant/single-stock-forecasts", json={"code": "NVDA"})

    assert response.status_code == 422


def test_financial_statement_endpoint_exposes_three_normalized_statements(tmp_path: Path) -> None:
    api = client(tmp_path, [MultiCapabilityProvider()])

    response = api.get(
        "/v1/stocks/SH/600519/financial-statements"
        "?period_end=2026-06-30&report_type=HALF_YEAR"
    )

    assert response.status_code == 200
    body = response.json()
    assert body["capability"] == "FINANCIAL_STATEMENTS"
    assert body["data"]["report"]["period_end"] == "2026-06-30"
    assert {item["statement_type"] for item in body["data"]["statements"]} == {
        "INCOME",
        "BALANCE_SHEET",
        "CASH_FLOW",
    }
    assert body["data"]["statements"][0]["values"][0]["value"] == "1200000000.12"


def test_overview_keeps_each_dataset_quality_and_source(tmp_path: Path) -> None:
    api = client(tmp_path, [MultiCapabilityProvider()])

    response = api.get("/v1/stocks/SH/600519/overview")

    assert response.status_code == 200
    body = response.json()
    assert body["quality_status"] == "FRESH_PRIMARY"
    assert set(body["datasets"]) == {"quote", "daily_bars", "capital_flow", "profile"}
    assert body["datasets"]["capital_flow"]["source_code"] == "FIXTURE"


def test_unavailable_data_returns_structured_503(tmp_path: Path) -> None:
    api = client(tmp_path, [])

    response = api.get("/v1/stocks/SZ/000001/quote")

    assert response.status_code == 503
    assert response.json()["quality_status"] == "UNAVAILABLE"
    assert response.json()["data"] is None
