from __future__ import annotations

from datetime import UTC, datetime
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
    StockProfile,
    StockQuote,
    StockSymbol,
)
from finscope_market_data.router import ProviderRouter
from finscope_market_data.snapshot_store import SnapshotStore


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
        return StockProfile(symbol=symbol, name="贵州茅台", industry="白酒")


def client(tmp_path: Path, providers: list[Any]) -> TestClient:
    router = ProviderRouter(
        providers=providers,
        snapshots=SnapshotStore(tmp_path / "snapshots.db"),
        health=ProviderHealthRegistry(),
        max_retries=0,
    )
    return TestClient(create_app(router))


def test_health_and_provider_health_endpoints(tmp_path: Path) -> None:
    api = client(tmp_path, [MultiCapabilityProvider()])

    assert api.get("/health").json()["status"] == "UP"
    providers = api.get("/v1/providers/health").json()
    assert providers[0]["provider_code"] == "FIXTURE"
    assert set(providers[0]["capabilities"]) == {item.value for item in DataCapability}


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

