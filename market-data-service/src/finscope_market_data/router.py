from __future__ import annotations

import asyncio
from datetime import UTC, datetime
from time import perf_counter
from typing import Any

from finscope_market_data.health import ProviderHealthRegistry
from finscope_market_data.models import (
    CapitalFlowData,
    DataCapability,
    DataEnvelope,
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
    ) -> None:
        self.providers = providers
        self.snapshots = snapshots
        self.health = health
        self.max_retries = max_retries
        self.retry_delay_seconds = retry_delay_seconds
        self._family_locks: dict[str, asyncio.Lock] = {}

    async def fetch(
        self,
        capability: DataCapability,
        symbol: StockSymbol,
        **kwargs: Any,
    ) -> DataEnvelope[Any]:
        supported = sorted(
            (provider for provider in self.providers if provider.supports(capability, symbol)),
            key=lambda provider: provider.priority,
        )
        candidates = [
            provider for provider in supported if self.health.is_available(provider, capability)
        ]
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
                    now = datetime.now(UTC)
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
                    self.snapshots.save(envelope)
                    return envelope
                except Exception as error:
                    retryable = not isinstance(error, ProviderError) or error.retryable
                    if retryable and retry_count < self.max_retries:
                        retry_count += 1
                        if self.retry_delay_seconds:
                            await asyncio.sleep(self.retry_delay_seconds * retry_count)
                        continue
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

        stored = self.snapshots.load(capability, symbol)
        now = datetime.now(UTC)
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

    @staticmethod
    def _as_of(data: Any, fallback: datetime) -> datetime:
        if isinstance(data, StockQuote):
            return data.observed_at
        if isinstance(data, CapitalFlowData):
            points = data.minute_points + data.daily_points
            return max((point.observed_at for point in points), default=fallback)
        return fallback
