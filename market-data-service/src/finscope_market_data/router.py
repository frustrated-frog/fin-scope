from __future__ import annotations

import asyncio
from datetime import UTC, date, datetime, time, timedelta
from time import perf_counter
from typing import Any, Callable
from zoneinfo import ZoneInfo

from finscope_market_data.health import ProviderHealthRegistry
from finscope_market_data.models import (
    CapitalFlowData,
    DailyBar,
    DataCapability,
    DataEnvelope,
    FinancialStatementsData,
    ProviderAttempt,
    QualityStatus,
    StockQuote,
    StockSymbol,
)
from finscope_market_data.providers.base import MarketDataProvider, ProviderError
from finscope_market_data.snapshot_store import SnapshotStore


class ProviderRouter:
    def __init__(
        self,
        providers: list[MarketDataProvider],
        snapshots: SnapshotStore,
        health: ProviderHealthRegistry,
        max_retries: int = 1,
        retry_delay_seconds: float = 0.15,
        daily_bar_retry_cooldown_seconds: int = 21600,
        now_provider: Callable[[], datetime] | None = None,
    ) -> None:
        self.providers = providers
        self.snapshots = snapshots
        self.health = health
        self.max_retries = max_retries
        self.retry_delay_seconds = retry_delay_seconds
        self.daily_bar_retry_cooldown_seconds = daily_bar_retry_cooldown_seconds
        self._now_provider = now_provider or (lambda: datetime.now(UTC))
        self._family_locks: dict[str, asyncio.Lock] = {}
        self._daily_bar_locks: dict[str, asyncio.Lock] = {}

    async def aclose(self) -> None:
        closed: set[int] = set()
        for provider in self.providers:
            client = getattr(provider, "http", None)
            if client is None or not hasattr(client, "aclose") or id(client) in closed:
                continue
            closed.add(id(client))
            await client.aclose()

    async def fetch(
        self,
        capability: DataCapability,
        symbol: StockSymbol,
        *,
        provider_mode: bool = False,
        provider_family: str | None = None,
        **kwargs: Any,
    ) -> DataEnvelope[Any]:
        if capability is DataCapability.DAILY_BARS and not provider_mode:
            requested_limit = min(max(int(kwargs.pop("limit", 250)), 1), 1000)
            force_refresh = bool(kwargs.pop("force_refresh", False))
            return await self._fetch_daily_bars(
                symbol,
                requested_limit=requested_limit,
                force_refresh=force_refresh,
                provider_family=provider_family,
                **kwargs,
            )
        return await self._fetch_online(
            capability,
            symbol,
            provider_mode=provider_mode,
            provider_family=provider_family,
            **kwargs,
        )

    async def _fetch_daily_bars(
        self,
        symbol: StockSymbol,
        *,
        requested_limit: int,
        force_refresh: bool,
        provider_family: str | None,
        **kwargs: Any,
    ) -> DataEnvelope[Any]:
        if not force_refresh:
            cached = self._fresh_daily_bar_snapshot(symbol)
            if cached is not None:
                return self._slice_daily_bars(cached, requested_limit)

        lock = self._daily_bar_locks.setdefault(symbol.cache_key, asyncio.Lock())
        async with lock:
            if not force_refresh:
                cached = self._fresh_daily_bar_snapshot(symbol)
                if cached is not None:
                    return self._slice_daily_bars(cached, requested_limit)
            result = await self._fetch_online(
                DataCapability.DAILY_BARS,
                symbol,
                provider_family=provider_family,
                limit=max(250, requested_limit),
                **kwargs,
            )
            return self._slice_daily_bars(result, requested_limit)

    async def _fetch_online(
        self,
        capability: DataCapability,
        symbol: StockSymbol,
        *,
        provider_mode: bool = False,
        provider_family: str | None = None,
        **kwargs: Any,
    ) -> DataEnvelope[Any]:
        supported = sorted(
            (
                provider
                for provider in self.providers
                if provider.supports(capability, symbol)
                and (
                    provider_family is None
                    or provider.provider_family.upper() == provider_family.strip().upper()
                )
            ),
            key=lambda provider: self._priority(provider, capability),
        )
        candidates = (
            supported[:1]
            if provider_mode
            else [provider for provider in supported if self.health.is_available(provider, capability)]
        )
        primary_code = supported[0].provider_code if supported else None
        attempts: list[ProviderAttempt] = []
        for provider in candidates:
            started = perf_counter()
            retry_count = 0
            while True:
                try:
                    family_lock = self._family_locks.setdefault(
                        provider.provider_family,
                        asyncio.Lock(),
                    )
                    async with family_lock:
                        data = await provider.fetch(capability, symbol, **kwargs)
                    if data is None or data == []:
                        raise ProviderError("EMPTY_DATA", "provider returned no data", True)
                    if (
                        capability is DataCapability.CAPITAL_FLOW
                        and kwargs.get("require_minute")
                        and isinstance(data, CapitalFlowData)
                        and not data.minute_points
                    ):
                        raise ProviderError(
                            "MINUTE_DATA_UNAVAILABLE",
                            "provider returned no intraday capital flow",
                            False,
                        )
                    if not provider_mode:
                        self.health.record_success(provider, capability)
                    attempts.append(
                        ProviderAttempt(
                            provider_code=provider.provider_code,
                            provider_family=provider.provider_family,
                            success=True,
                            duration_ms=int((perf_counter() - started) * 1000),
                            retry_count=retry_count,
                        )
                    )
                    now = self._now()
                    is_primary = provider.provider_code == primary_code
                    envelope = DataEnvelope[Any](
                        capability=capability,
                        symbol=symbol,
                        quality_status=(
                            QualityStatus.FRESH_PRIMARY if is_primary else QualityStatus.FRESH_FALLBACK
                        ),
                        source_code=provider.provider_code,
                        source_family=provider.provider_family,
                        as_of=self._as_of(data, now),
                        retrieved_at=now,
                        warnings=([] if is_primary else ["首选数据源不可用，已自动使用备用数据源"]),
                        attempts=attempts,
                        data=data,
                    )
                    if not provider_mode:
                        self.snapshots.save(envelope)
                    return envelope
                except Exception as error:
                    retryable = not isinstance(error, ProviderError) or error.retryable
                    if not provider_mode and retryable and retry_count < self.max_retries:
                        retry_count += 1
                        if self.retry_delay_seconds:
                            await asyncio.sleep(self.retry_delay_seconds * retry_count)
                        continue
                    if not provider_mode:
                        self.health.record_failure(provider, capability, error)
                    attempts.append(
                        ProviderAttempt(
                            provider_code=provider.provider_code,
                            provider_family=provider.provider_family,
                            success=False,
                            duration_ms=int((perf_counter() - started) * 1000),
                            error_type=(error.error_type if isinstance(error, ProviderError) else "UNEXPECTED_ERROR"),
                            error_message=str(error),
                            retry_count=retry_count,
                        )
                    )
                    break

        stored = None if provider_mode else self.snapshots.load(capability, symbol)
        now = self._now()
        if stored is not None and stored.data is not None:
            age = max(0, int((now - stored.retrieved_at).total_seconds()))
            reasons = "；".join(
                attempt.error_message or attempt.error_type or "unknown"
                for attempt in attempts
                if not attempt.success
            )
            warning = "在线数据源不可用，已返回最近一次成功快照"
            if reasons:
                warning += f"：{reasons}"
            return stored.model_copy(
                update={
                    "quality_status": QualityStatus.STALE_FALLBACK,
                    "retrieved_at": now,
                    "stale_age_seconds": age,
                    "warnings": [warning],
                    "attempts": attempts,
                }
            )

        warning = (
            "没有健康且支持该能力的数据源，且不存在历史快照"
            if not candidates
            else "在线数据源均不可用，且不存在历史快照"
        )
        return DataEnvelope[Any](
            capability=capability,
            symbol=symbol,
            quality_status=QualityStatus.UNAVAILABLE,
            retrieved_at=now,
            warnings=[warning],
            attempts=attempts,
            data=None,
        )

    def _fresh_daily_bar_snapshot(self, symbol: StockSymbol) -> DataEnvelope[Any] | None:
        stored = self.snapshots.load(DataCapability.DAILY_BARS, symbol)
        if stored is None or not isinstance(stored.data, list) or not stored.data:
            return None
        now = self._now()
        age_seconds = max(0, int((now - stored.retrieved_at).total_seconds()))
        try:
            latest_trade_date = max(date.fromisoformat(item.trade_date) for item in stored.data)
        except (AttributeError, TypeError, ValueError):
            return None
        expected_trade_date = self._expected_daily_bar_date(now)
        if latest_trade_date >= expected_trade_date:
            return stored
        refresh_boundary = datetime.combine(
            expected_trade_date,
            time(hour=15, minute=15),
            tzinfo=ZoneInfo("Asia/Shanghai"),
        )
        if (
            stored.retrieved_at >= refresh_boundary
            and age_seconds <= self.daily_bar_retry_cooldown_seconds
        ):
            return stored
        return None

    @staticmethod
    def _slice_daily_bars(envelope: DataEnvelope[Any], limit: int) -> DataEnvelope[Any]:
        if not isinstance(envelope.data, list):
            return envelope
        return envelope.model_copy(update={"data": envelope.data[-limit:]})

    def _now(self) -> datetime:
        value = self._now_provider()
        return value if value.tzinfo is not None else value.replace(tzinfo=UTC)

    @staticmethod
    def _expected_daily_bar_date(now: datetime) -> date:
        local = now.astimezone(ZoneInfo("Asia/Shanghai"))
        expected = local.date()
        if local.weekday() >= 5 or local.time() < time(hour=15, minute=15):
            expected -= timedelta(days=1)
        while expected.weekday() >= 5:
            expected -= timedelta(days=1)
        return expected

    @staticmethod
    def _priority(provider: MarketDataProvider, capability: DataCapability) -> int:
        resolver = getattr(provider, "priority_for", None)
        return resolver(capability) if callable(resolver) else provider.priority

    @staticmethod
    def _as_of(data: Any, fallback: datetime) -> datetime:
        if isinstance(data, StockQuote):
            return data.observed_at
        if isinstance(data, CapitalFlowData):
            points = data.minute_points + data.daily_points
            return max((point.observed_at for point in points), default=fallback)
        if isinstance(data, FinancialStatementsData):
            return data.report.published_at or fallback
        if isinstance(data, list) and data and all(isinstance(item, DailyBar) for item in data):
            last_trade_date = max(date.fromisoformat(item.trade_date) for item in data)
            return datetime.combine(
                last_trade_date,
                time(hour=15),
                tzinfo=ZoneInfo("Asia/Shanghai"),
            )
        return fallback
