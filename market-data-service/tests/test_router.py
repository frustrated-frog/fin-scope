from __future__ import annotations

from collections import deque
from datetime import UTC, datetime
from pathlib import Path
from typing import Any
import asyncio

import pytest

from finscope_market_data.health import ProviderHealthRegistry
from finscope_market_data.models import (
    DailyBar,
    DataCapability,
    QualityStatus,
    StockProfile,
    StockQuote,
    StockSymbol,
)
from finscope_market_data.providers.base import ProviderError
from finscope_market_data.router import ProviderRouter
from finscope_market_data.snapshot_store import SnapshotStore


class FakeProvider:
    def __init__(self, code: str, family: str, priority: int, outcomes: list[Any]) -> None:
        self.provider_code = code
        self.provider_family = family
        self.priority = priority
        self.capabilities = {DataCapability.QUOTE}
        self.outcomes = deque(outcomes)
        self.calls = 0

    def supports(self, capability: DataCapability, symbol: StockSymbol) -> bool:
        return capability in self.capabilities

    async def fetch(self, capability: DataCapability, symbol: StockSymbol, **kwargs: Any) -> Any:
        self.calls += 1
        outcome = self.outcomes.popleft()
        if isinstance(outcome, Exception):
            raise outcome
        return outcome


def quote(symbol: StockSymbol, price: float) -> StockQuote:
    return StockQuote(
        symbol=symbol,
        name="贵州茅台",
        price=price,
        observed_at=datetime(2026, 7, 16, 10, 30, tzinfo=UTC),
    )


def make_router(
    tmp_path: Path,
    providers: list[FakeProvider],
    max_retries: int = 0,
) -> ProviderRouter:
    return ProviderRouter(
        providers=providers,
        snapshots=SnapshotStore(tmp_path / "snapshots.db"),
        health=ProviderHealthRegistry(failure_threshold=2, open_seconds=60),
        max_retries=max_retries,
        retry_delay_seconds=0,
    )


@pytest.mark.asyncio
async def test_router_returns_primary_and_persists_last_good_snapshot(tmp_path: Path) -> None:
    symbol = StockSymbol(market="SH", code="600519")
    primary = FakeProvider("PRIMARY", "A", 10, [quote(symbol, 1480.5)])
    service = make_router(tmp_path, [primary])

    result = await service.fetch(DataCapability.QUOTE, symbol)

    assert result.quality_status is QualityStatus.FRESH_PRIMARY
    assert result.source_code == "PRIMARY"
    assert result.data.price == 1480.5
    stored = service.snapshots.load(DataCapability.QUOTE, symbol)
    assert stored is not None
    assert stored.data.price == 1480.5


@pytest.mark.asyncio
async def test_router_switches_to_backup_provider(tmp_path: Path) -> None:
    symbol = StockSymbol(market="SH", code="600519")
    primary = FakeProvider("PRIMARY", "A", 10, [ProviderError("TIMEOUT", "timeout")])
    backup = FakeProvider("BACKUP", "B", 20, [quote(symbol, 1481.0)])

    result = await make_router(tmp_path, [primary, backup]).fetch(DataCapability.QUOTE, symbol)

    assert result.quality_status is QualityStatus.FRESH_FALLBACK
    assert result.source_code == "BACKUP"
    assert [attempt.success for attempt in result.attempts] == [False, True]


@pytest.mark.asyncio
async def test_router_returns_stale_snapshot_when_all_online_sources_fail(tmp_path: Path) -> None:
    symbol = StockSymbol(market="SH", code="600519")
    service = make_router(tmp_path, [FakeProvider("PRIMARY", "A", 10, [quote(symbol, 1480.5)])])
    await service.fetch(DataCapability.QUOTE, symbol)

    service.providers = [FakeProvider("PRIMARY", "A", 10, [ProviderError("HTTP_503", "down")])]
    result = await service.fetch(DataCapability.QUOTE, symbol)

    assert result.quality_status is QualityStatus.STALE_FALLBACK
    assert result.data.price == 1480.5
    assert result.stale_age_seconds is not None
    assert "在线数据源不可用" in result.warnings[0]


@pytest.mark.asyncio
async def test_router_returns_unavailable_without_snapshot(tmp_path: Path) -> None:
    symbol = StockSymbol(market="SZ", code="000001")
    provider = FakeProvider("PRIMARY", "A", 10, [ProviderError("SCHEMA_DRIFT", "changed", False)])

    result = await make_router(tmp_path, [provider]).fetch(DataCapability.QUOTE, symbol)

    assert result.quality_status is QualityStatus.UNAVAILABLE
    assert result.data is None
    assert result.attempts[0].error_type == "SCHEMA_DRIFT"


@pytest.mark.asyncio
async def test_router_retries_retryable_failure_once(tmp_path: Path) -> None:
    symbol = StockSymbol(market="SZ", code="000001")
    provider = FakeProvider(
        "PRIMARY",
        "A",
        10,
        [ProviderError("TIMEOUT", "slow"), quote(symbol, 11.23)],
    )

    result = await make_router(tmp_path, [provider], max_retries=1).fetch(DataCapability.QUOTE, symbol)

    assert result.quality_status is QualityStatus.FRESH_PRIMARY
    assert provider.calls == 2
    assert result.attempts[0].retry_count == 1


@pytest.mark.asyncio
async def test_router_skips_provider_after_circuit_opens(tmp_path: Path) -> None:
    symbol = StockSymbol(market="BJ", code="920002")
    failed = FakeProvider(
        "PRIMARY",
        "A",
        10,
        [ProviderError("TIMEOUT", "slow"), ProviderError("TIMEOUT", "slow")],
    )
    service = make_router(tmp_path, [failed])

    await service.fetch(DataCapability.QUOTE, symbol)
    await service.fetch(DataCapability.QUOTE, symbol)
    third = await service.fetch(DataCapability.QUOTE, symbol)

    assert failed.calls == 2
    assert third.quality_status is QualityStatus.UNAVAILABLE
    assert third.warnings == ["没有健康且支持该能力的数据源，且不存在历史快照"]


@pytest.mark.asyncio
async def test_router_keeps_fallback_quality_when_primary_circuit_is_open(tmp_path: Path) -> None:
    symbol = StockSymbol(market="SH", code="600519")
    primary = FakeProvider(
        "PRIMARY",
        "A",
        10,
        [ProviderError("TIMEOUT", "slow"), ProviderError("TIMEOUT", "slow")],
    )
    backup = FakeProvider(
        "BACKUP",
        "B",
        20,
        [quote(symbol, 1480.0), quote(symbol, 1481.0), quote(symbol, 1482.0)],
    )
    service = make_router(tmp_path, [primary, backup])

    await service.fetch(DataCapability.QUOTE, symbol)
    await service.fetch(DataCapability.QUOTE, symbol)
    third = await service.fetch(DataCapability.QUOTE, symbol)

    assert primary.calls == 2
    assert third.source_code == "BACKUP"
    assert third.quality_status is QualityStatus.FRESH_FALLBACK
    assert third.warnings == ["首选数据源不可用，已自动使用备用数据源"]


def test_health_circuit_is_isolated_by_provider_capability() -> None:
    symbol = StockSymbol(market="SH", code="600519")
    provider = FakeProvider("PRIMARY", "A", 10, [])
    provider.capabilities = {DataCapability.QUOTE, DataCapability.PROFILE}
    health = ProviderHealthRegistry(failure_threshold=2, open_seconds=60)

    health.record_failure(provider, DataCapability.QUOTE, ProviderError("TIMEOUT", "slow"))
    health.record_failure(provider, DataCapability.QUOTE, ProviderError("TIMEOUT", "slow"))

    assert health.is_available(provider, DataCapability.QUOTE) is False
    assert health.is_available(provider, DataCapability.PROFILE) is True
    assert provider.supports(DataCapability.QUOTE, symbol)


@pytest.mark.asyncio
async def test_router_serializes_requests_within_same_provider_family(tmp_path: Path) -> None:
    symbol = StockSymbol(market="SH", code="600519")

    class FamilyProvider:
        provider_code = "MULTI"
        provider_family = "SHARED_UPSTREAM"
        priority = 10
        capabilities = {DataCapability.QUOTE, DataCapability.PROFILE}

        def __init__(self) -> None:
            self.active = 0
            self.max_active = 0

        def supports(self, capability: DataCapability, stock: StockSymbol) -> bool:
            return capability in self.capabilities

        async def fetch(self, capability: DataCapability, stock: StockSymbol, **kwargs: Any) -> Any:
            self.active += 1
            self.max_active = max(self.max_active, self.active)
            await asyncio.sleep(0.01)
            self.active -= 1
            if capability is DataCapability.QUOTE:
                return quote(stock, 1480.5)
            return StockProfile(symbol=stock, name="贵州茅台")

    provider = FamilyProvider()
    service = ProviderRouter(
        providers=[provider],
        snapshots=SnapshotStore(tmp_path / "snapshots.db"),
        health=ProviderHealthRegistry(),
        max_retries=0,
    )

    await asyncio.gather(
        service.fetch(DataCapability.QUOTE, symbol),
        service.fetch(DataCapability.PROFILE, symbol),
    )

    assert provider.max_active == 1


@pytest.mark.asyncio
async def test_daily_bar_fact_time_uses_last_trade_date_instead_of_retrieval_time(
    tmp_path: Path,
) -> None:
    symbol = StockSymbol(market="SH", code="600519")
    bars = [
        DailyBar(
            symbol=symbol,
            trade_date="2026-07-16",
            open=1475,
            high=1490,
            low=1470,
            close=1480.5,
            volume=1000,
            amount=1_480_500,
            adjustment="QFQ",
        )
    ]
    provider = FakeProvider("PRIMARY", "EASTMONEY", 10, [bars])
    provider.capabilities = {DataCapability.DAILY_BARS}

    result = await make_router(tmp_path, [provider]).fetch(
        DataCapability.DAILY_BARS,
        symbol,
    )

    assert result.as_of is not None
    assert result.as_of.isoformat() == "2026-07-16T15:00:00+08:00"
