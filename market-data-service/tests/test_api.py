from __future__ import annotations

from datetime import UTC, date, datetime, timedelta
from pathlib import Path
from typing import Any

from fastapi.testclient import TestClient
import pytest

from finscope_market_data.app import build_router, create_app
from finscope_market_data.health import ProviderHealthRegistry
from finscope_market_data.models import (
    CapitalFlowData,
    CapitalFlowPoint,
    CorporateAction,
    CorporateActionsData,
    DailyBar,
    DataCapability,
    FinancialReportMeta,
    FinancialStatement,
    FinancialStatementType,
    FinancialStatementValue,
    FinancialStatementsData,
    MarketBreadthSnapshot,
    StockProfile,
    StockQuote,
    StockSymbol,
    StockValuationSnapshot,
)
from finscope_market_data.router import ProviderRouter
from finscope_market_data.snapshot_store import SnapshotStore
from finscope_market_data.settings import Settings
from finscope_market_data.discovery.schemas import DiscoveryFunnel, DiscoveryReport
from finscope_market_data.sectors import SectorEntry, SectorEnvelope
from finscope_market_data.sector_history import (
    SectorHistoryEntry,
    SectorHistoryEnvelope,
)


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
        if capability is DataCapability.VALUATION_SNAPSHOT:
            return StockValuationSnapshot(
                symbol=symbol,
                name="贵州茅台",
                pe_ttm=21.3,
                pe_mrq=20.8,
                pb_mrq=7.1,
                ps_ttm=10.3,
                pcf_ttm=19.7,
                observed_at=observed,
            )
        if capability is DataCapability.CORPORATE_ACTIONS:
            return CorporateActionsData(
                symbol=symbol,
                items=[
                    CorporateAction(
                        ex_date="2026-06-20",
                        event_types=["CASH_DIVIDEND"],
                        dividend_per_share=23.957,
                    )
                ],
            )
        return StockProfile(symbol=symbol, name="贵州茅台", industry="白酒")


class FakeSectorHistoryService:
    def fetch(self, business_date: date, window: int) -> SectorHistoryEnvelope:
        return SectorHistoryEnvelope(
            business_date=business_date.isoformat(),
            quality_status="FRESH_PRIMARY",
            retrieved_at="2026-08-23T18:00:00",
            requested_window=window,
            covered_trade_dates=["2026-08-20", "2026-08-21"],
            entries=[
                SectorHistoryEntry(
                    code="881121",
                    name="半导体",
                    last_trade_date="2026-08-21",
                    coverage_days=60,
                    return_1d=0.8,
                    return_5d=3.2,
                    return_20d=6.5,
                    positive_days_5=4,
                )
            ],
        )


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


class FakeMarketDumpClient:
    def __init__(self) -> None:
        self.kinds: list[str] = []

    async def download_url(self, kind: str) -> dict[str, Any]:
        self.kinds.append(kind)
        return {
            "kind": kind,
            "download_url": "https://storage.example/market.parquet?signature=fresh",
            "expires_in": 300,
        }


class FailingCloseMarketDumpClient(FakeMarketDumpClient):
    async def aclose(self) -> None:
        raise RuntimeError("dump close failed")


def client(tmp_path: Path, providers: list[Any]) -> TestClient:
    router = ProviderRouter(
        providers=providers,
        snapshots=SnapshotStore(tmp_path / "snapshots.db"),
        health=ProviderHealthRegistry(),
        max_retries=0,
    )
    return TestClient(create_app(router))


def test_default_lifespan_builds_stock_discovery_with_configured_data_dir(
    tmp_path: Path,
) -> None:
    router = ProviderRouter(
        providers=[MultiCapabilityProvider()],
        snapshots=SnapshotStore(tmp_path / "snapshots.db"),
        health=ProviderHealthRegistry(),
        max_retries=0,
    )
    application = create_app(
        router=router,
        settings=Settings(data_dir=tmp_path, fuyao_api_key=" "),
    )

    with TestClient(application) as api:
        assert api.get("/health").status_code == 200
        assert application.state.discovery is not None
        assert application.state.discovery.constituent_providers == ()


def test_fuyao_provider_is_registered_only_when_api_key_is_configured(tmp_path: Path) -> None:
    disabled = build_router(
        Settings(data_dir=tmp_path / "disabled", fuyao_api_key=" ")
    )
    enabled = build_router(
        Settings(data_dir=tmp_path / "enabled", fuyao_api_key="test-key")
    )
    stock = StockSymbol(market="SH", code="600519")

    assert all(provider.provider_family != "TONGHUASHUN" for provider in disabled.providers)
    assert enabled.providers[0].provider_code == "FUYAO_TONGHUASHUN_API"
    assert [
        provider.provider_code
        for provider in disabled.providers
        if provider.supports(DataCapability.DAILY_BARS, stock)
    ] == []
    assert [
        provider.provider_code
        for provider in enabled.providers
        if provider.supports(DataCapability.DAILY_BARS, stock)
    ] == ["FUYAO_TONGHUASHUN_API"]
    assert [
        provider.provider_code
        for provider in enabled.providers
        if provider.supports(DataCapability.FINANCIAL_STATEMENTS, stock)
    ] == ["FUYAO_TONGHUASHUN_API"]
    assert [
        provider.provider_code
        for provider in enabled.providers
        if provider.supports(DataCapability.VALUATION_SNAPSHOT, stock)
    ] == ["FUYAO_TONGHUASHUN_API"]
    assert [
        provider.provider_code
        for provider in enabled.providers
        if provider.supports(DataCapability.CORPORATE_ACTIONS, stock)
    ] == ["FUYAO_TONGHUASHUN_API"]
    assert any(
        provider.supports(DataCapability.QUOTE, stock)
        for provider in enabled.providers
    )
    assert any(
        provider.supports(DataCapability.CAPITAL_FLOW, stock)
        for provider in enabled.providers
    )
    assert any(
        provider.supports(DataCapability.PROFILE, stock)
        for provider in enabled.providers
    )


def test_valuation_and_corporate_action_endpoints_return_normalized_data(
    tmp_path: Path,
) -> None:
    with client(tmp_path, [MultiCapabilityProvider()]) as api:
        valuation = api.get("/v1/stocks/SH/600519/valuation")
        actions = api.get(
            "/v1/stocks/SH/600519/corporate-actions"
            "?from_date=2020-01-01&to_date=2026-08-29"
        )

    assert valuation.status_code == 200
    assert valuation.json()["capability"] == "VALUATION_SNAPSHOT"
    assert valuation.json()["data"]["pe_ttm"] == 21.3
    assert valuation.json()["data"]["pb_mrq"] == 7.1
    assert actions.status_code == 200
    assert actions.json()["capability"] == "CORPORATE_ACTIONS"
    assert actions.json()["data"]["items"][0]["event_types"] == [
        "CASH_DIVIDEND"
    ]


def test_fuyao_is_the_only_constituent_provider_when_configured(tmp_path: Path) -> None:
    router = ProviderRouter(
        providers=[MultiCapabilityProvider()],
        snapshots=SnapshotStore(tmp_path / "snapshots.db"),
        health=ProviderHealthRegistry(),
        max_retries=0,
    )
    application = create_app(
        router=router,
        settings=Settings(data_dir=tmp_path, fuyao_api_key="test-key"),
    )

    with TestClient(application):
        providers = application.state.discovery.constituent_providers
        assert [provider.source_family for provider in providers] == [
            "FUYAO_TONGHUASHUN"
        ]


def test_market_dump_endpoint_proxies_a_fresh_signed_url(tmp_path: Path) -> None:
    router = ProviderRouter(
        providers=[MultiCapabilityProvider()],
        snapshots=SnapshotStore(tmp_path / "snapshots.db"),
        health=ProviderHealthRegistry(),
        max_retries=0,
    )
    market_dumps = FakeMarketDumpClient()

    with TestClient(create_app(router=router, market_dumps=market_dumps)) as api:
        response = api.get("/v1/market-dumps/daily-k-10d/download-url")

    assert response.status_code == 200
    assert response.json()["kind"] == "daily-k-10d"
    assert response.json()["expires_in"] == 300
    assert response.headers["cache-control"] == "no-store, private"
    assert market_dumps.kinds == ["daily-k-10d"]


def test_market_dump_endpoint_is_unavailable_without_fuyao_key(tmp_path: Path) -> None:
    router = ProviderRouter(
        providers=[MultiCapabilityProvider()],
        snapshots=SnapshotStore(tmp_path / "snapshots.db"),
        health=ProviderHealthRegistry(),
        max_retries=0,
    )

    with TestClient(
        create_app(
            router=router,
            settings=Settings(data_dir=tmp_path, fuyao_api_key=" "),
        )
    ) as api:
        response = api.get("/v1/market-dumps/daily-k-10d/download-url")

    assert response.status_code == 503
    assert response.json()["detail"]["error_type"] == "PROVIDER_DISABLED"


def test_lifespan_closes_router_when_market_dump_close_fails(tmp_path: Path) -> None:
    router = ProviderRouter(
        providers=[MultiCapabilityProvider()],
        snapshots=SnapshotStore(tmp_path / "snapshots.db"),
        health=ProviderHealthRegistry(),
        max_retries=0,
    )
    router_closed = False
    original_close = router.aclose

    async def close_router() -> None:
        nonlocal router_closed
        router_closed = True
        await original_close()

    router.aclose = close_router  # type: ignore[method-assign]

    with pytest.raises(RuntimeError, match="dump close failed"):
        with TestClient(
            create_app(router=router, market_dumps=FailingCloseMarketDumpClient())
        ):
            pass

    assert router_closed is True


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


class CloseRecordingDiscovery(FakeDiscoveryService):
    def __init__(self) -> None:
        self.closed = False

    def close(self) -> None:
        self.closed = True


class FakeSectorService:
    def fetch(self, category: str) -> SectorEnvelope:
        return SectorEnvelope(
            category=category,
            retrieved_at="2026-08-19T09:45:00",
            entries=[
                SectorEntry(
                    code="881121",
                    name="半导体",
                    category="INDUSTRY",
                    source_rank=1,
                    change_pct=2.4,
                    main_net_inflow=1_200_000_000,
                    leader_stock_name="中芯国际",
                    advance_count=48,
                    decline_count=12,
                    flat_count=0,
                    breadth_ratio=0.8,
                )
            ],
        )


class FakeBreadthService:
    def latest_trade_date(self) -> date:
        return date(2026, 8, 21)

    def fetch(self, business_date: date) -> MarketBreadthSnapshot:
        return MarketBreadthSnapshot(
            business_date=business_date.isoformat(),
            source_code="FIXTURE_BREADTH",
            source_family="FIXTURE",
            quality_status="FRESH_PRIMARY",
            retrieved_at=datetime(2026, 8, 21, 15, 20, tzinfo=UTC),
            advance_count=3200,
            decline_count=1800,
            flat_count=100,
            valid_count=5100,
            advance_ratio=3200 / 5100,
            total_amount=2_300_000_000_000,
            limit_up_count=68,
            limit_down_count=4,
            median_change_pct=0.7,
        )


def test_lifespan_closes_custom_discovery_service(tmp_path: Path) -> None:
    router = ProviderRouter(
        providers=[MultiCapabilityProvider()],
        snapshots=SnapshotStore(tmp_path / "snapshots.db"),
        health=ProviderHealthRegistry(),
        max_retries=0,
    )
    discovery = CloseRecordingDiscovery()
    application = create_app(router=router, discovery=discovery)

    with TestClient(application) as api:
        assert api.get("/health").status_code == 200

    assert discovery.closed is True


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


def test_stock_discovery_evaluation_endpoint_returns_typed_empty_report(
    tmp_path: Path,
) -> None:
    api = client(tmp_path, [MultiCapabilityProvider()])

    response = api.post(
        "/v1/quant/stock-discovery-evaluations",
        json={"asOfDate": "2026-08-20", "observations": []},
    )

    assert response.status_code == 200
    assert response.json()["schema_version"] == "stock-discovery-evaluation-v1"
    assert response.json()["status"] == "ACCUMULATING"
    assert response.json()["matured_candidate_count"] == 0


def test_tonghuashun_sector_endpoint_returns_versioned_contract(tmp_path: Path) -> None:
    router = ProviderRouter(
        providers=[MultiCapabilityProvider()],
        snapshots=SnapshotStore(tmp_path / "snapshots.db"),
        health=ProviderHealthRegistry(),
        max_retries=0,
    )
    api = TestClient(create_app(router, sectors=FakeSectorService()))

    response = api.get("/v1/sectors/INDUSTRY")

    assert response.status_code == 200
    assert response.json() == {
        "schema_version": "sector-market-v1",
        "source_code": "AKSHARE_TONGHUASHUN_SECTOR",
        "source_family": "TONGHUASHUN",
        "category": "INDUSTRY",
        "retrieved_at": "2026-08-19T09:45:00",
        "entries": [
            {
                "code": "881121",
                "name": "半导体",
                "category": "INDUSTRY",
                "source_rank": 1,
                "change_pct": 2.4,
                "main_net_inflow": 1_200_000_000.0,
                "leader_stock_name": "中芯国际",
                "advance_count": 48,
                "decline_count": 12,
                "flat_count": 0,
                "breadth_ratio": 0.8,
            }
        ],
        "warnings": [],
    }


def test_market_breadth_endpoint_returns_versioned_contract(tmp_path: Path) -> None:
    router = ProviderRouter(
        providers=[MultiCapabilityProvider()],
        snapshots=SnapshotStore(tmp_path / "snapshots.db"),
        health=ProviderHealthRegistry(),
        max_retries=0,
    )
    api = TestClient(create_app(router, breadth=FakeBreadthService()))

    response = api.get("/v1/markets/CN-A/breadth?business_date=2026-08-21")

    assert response.status_code == 200
    assert response.json()["schema_version"] == "market-breadth-v2"
    assert response.json()["business_date"] == "2026-08-21"
    assert response.json()["advance_count"] == 3200
    assert response.json()["limit_up_count"] == 68


def test_market_breadth_endpoint_defaults_to_latest_actual_trade_date(
    tmp_path: Path,
) -> None:
    router = ProviderRouter(
        providers=[MultiCapabilityProvider()],
        snapshots=SnapshotStore(tmp_path / "snapshots.db"),
        health=ProviderHealthRegistry(),
        max_retries=0,
    )
    api = TestClient(create_app(router, breadth=FakeBreadthService()))

    response = api.get("/v1/markets/CN-A/breadth")

    assert response.status_code == 200
    assert response.json()["business_date"] == "2026-08-21"


def test_tonghuashun_sector_history_endpoint_returns_versioned_contract(
    tmp_path: Path,
) -> None:
    router = ProviderRouter(
        providers=[MultiCapabilityProvider()],
        snapshots=SnapshotStore(tmp_path / "snapshots.db"),
        health=ProviderHealthRegistry(),
        max_retries=0,
    )
    api = TestClient(
        create_app(router, sector_history=FakeSectorHistoryService())
    )

    response = api.get(
        "/v1/sectors/INDUSTRY/history?business_date=2026-08-21&window=60"
    )

    assert response.status_code == 200
    assert response.json()["schema_version"] == "sector-history-v1"
    assert response.json()["business_date"] == "2026-08-21"
    assert response.json()["entries"][0]["return_20d"] == 6.5


def test_tonghuashun_sector_endpoint_rejects_unknown_category(tmp_path: Path) -> None:
    router = ProviderRouter(
        providers=[MultiCapabilityProvider()],
        snapshots=SnapshotStore(tmp_path / "snapshots.db"),
        health=ProviderHealthRegistry(),
        max_retries=0,
    )
    api = TestClient(create_app(router, sectors=FakeSectorService()))

    response = api.get("/v1/sectors/ALL")

    assert response.status_code == 422


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
