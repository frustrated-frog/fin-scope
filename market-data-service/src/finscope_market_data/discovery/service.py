from __future__ import annotations

import asyncio
from dataclasses import dataclass
from datetime import date, datetime
import hashlib
import json
import math
from pathlib import Path
import time
from typing import Awaitable, Callable, Mapping, Protocol, Sequence

from finscope_market_data.discovery.providers import HotSectorProvider
from finscope_market_data.discovery.constituents import (
    ConstituentBatch,
    ConstituentSnapshotStore,
)
from finscope_market_data.discovery.trading_scope import TradingScopePolicy
from finscope_market_data.discovery.ranking import (
    rank_deep_candidates,
    rank_lightweight_candidates,
)
from finscope_market_data.discovery.context_factors import enrich_context_factors
from finscope_market_data.discovery.schemas import (
    DeepCandidateEvidence,
    DiscoveryCandidate,
    DiscoveryFunnel,
    DiscoveryReport,
    DiscoveryRequest,
    DiscoverySector,
)
from finscope_market_data.forecast.service import build_forecast
from finscope_market_data.forecast.context import build_aligned_context
from finscope_market_data.forecast.features import (
    FEATURE_CODES,
    build_samples,
    current_features,
)
from finscope_market_data.forecast.panel import (
    PanelArtifact,
    PanelArtifactStore,
    train_panel_artifact,
)
from finscope_market_data.forecast.panel_features import augment_cross_sectional_features
from finscope_market_data.models import DailyBar


class DiscoveryMarket(Protocol):
    async def bars(
        self, market: str, code: str
    ) -> Sequence[DailyBar] | DiscoveryBarsSnapshot: ...


@dataclass(frozen=True)
class DiscoveryBarsSnapshot:
    bars: Sequence[DailyBar]
    quality_status: str
    stale_age_seconds: int | None = None
    warnings: tuple[str, ...] = ()


@dataclass(frozen=True)
class SnapshotHotSectorProvider:
    source_code: str
    source_family: str

    def sectors(self, limit: int) -> list[DiscoverySector]:
        raise RuntimeError("snapshot provider does not fetch online sectors")


@dataclass(frozen=True)
class DiscoveryUniverse:
    sectors: list[DiscoverySector]
    provider: HotSectorProvider
    members: dict[str, tuple[str, str, set[str], set[str]]]
    warnings: list[str]
    raw_constituent_count: int
    scope_exclusions: Mapping[str, int]
    constituent_sources: tuple[str, ...]
    constituent_quality_status: str


ForecastBuilder = Callable[
    [DiscoveryCandidate, Sequence[DailyBar], DiscoveryRequest],
    dict[str, object],
]


class StockDiscoveryService:
    def __init__(
        self,
        providers: Sequence[HotSectorProvider],
        market: DiscoveryMarket,
        forecast_builder: ForecastBuilder | None = None,
        network_concurrency: int = 6,
        deep_concurrency: int = 2,
        universe_snapshot_path: str | Path | None = None,
        provider_attempts: int = 2,
        provider_retry_delay_seconds: float = 0.2,
        panel_store: PanelArtifactStore | None = None,
        constituent_providers: Sequence[object] | None = None,
        constituent_snapshot_path: str | Path | None = None,
        trading_scope: TradingScopePolicy | None = None,
    ) -> None:
        self.providers = tuple(providers)
        self.market = market
        self.forecast_builder = forecast_builder or _forecast
        self.network_concurrency = network_concurrency
        self.deep_concurrency = deep_concurrency
        self.provider_attempts = max(1, provider_attempts)
        self.provider_retry_delay_seconds = max(0.0, provider_retry_delay_seconds)
        self.universe_snapshot_path = (
            Path(universe_snapshot_path) if universe_snapshot_path else None
        )
        self.panel_store = panel_store
        self.constituent_providers = tuple(constituent_providers or providers)
        self.constituent_snapshots = (
            ConstituentSnapshotStore(constituent_snapshot_path)
            if constituent_snapshot_path else None
        )
        self.trading_scope = trading_scope or TradingScopePolicy()
        self._uses_default_forecast_builder = forecast_builder is None

    async def discover(self, request: DiscoveryRequest) -> DiscoveryReport:
        started = time.monotonic()
        universe = await self._universe(request.sector_limit)
        sectors = universe.sectors
        provider = universe.provider
        members = universe.members
        warnings = universe.warnings
        market_bars = await self._market_context(request, warnings)
        candidates, bars_by_code = await self._admit(members, request, warnings)
        candidates = enrich_context_factors(candidates, sectors)
        panel_artifact = self._train_panel_artifact(
            bars_by_code,
            request.horizon_days,
            warnings,
            market_bars,
        )
        lightweight = rank_lightweight_candidates(candidates)
        by_code = {item.code: item for item in candidates}
        for ranked in lightweight:
            by_code[ranked.code] = ranked
        deep_targets = lightweight[: request.deep_limit]
        deep = await self._deep(
            deep_targets,
            bars_by_code,
            request,
            warnings,
            panel_artifact,
            market_bars,
        )
        final = rank_deep_candidates(deep, request.final_limit)
        as_of = max(
            (bars[-1].trade_date for bars in bars_by_code.values() if bars),
            default=request.business_date or datetime.now().date().isoformat(),
        )
        ordered_candidates = sorted(
            by_code.values(),
            key=lambda item: (
                not item.admitted,
                item.lightweight_rank or 999999,
                item.code,
            ),
        )
        fingerprint_payload = {
            "as_of": as_of,
            "sectors": [
                item.model_dump(mode="json", exclude={"retrieved_at"})
                for item in sectors
            ],
            "candidates": [item.model_dump(mode="json") for item in ordered_candidates],
        }
        fingerprint = hashlib.sha256(
            json.dumps(fingerprint_payload, sort_keys=True, ensure_ascii=False).encode()
        ).hexdigest()
        return DiscoveryReport(
            policy_version=request.policy_version,
            as_of_date=as_of,
            source_code=provider.source_code,
            source_family=provider.source_family,
            quality_status=self._quality_status(universe),
            retrieved_at=datetime.now().isoformat(),
            data_fingerprint=fingerprint,
            budget=request.budget,
            constituent_source_families=list(universe.constituent_sources),
            constituent_quality_status=universe.constituent_quality_status,
            sectors=sectors,
            candidates=ordered_candidates,
            deep_evidence=deep,
            final_candidates=final,
            funnel=DiscoveryFunnel(
                raw_constituent_count=universe.raw_constituent_count,
                scope_excluded_count=sum(universe.scope_exclusions.values()),
                star_market_excluded_count=universe.scope_exclusions.get(
                    "NO_STAR_MARKET_PERMISSION", 0
                ),
                beijing_market_excluded_count=universe.scope_exclusions.get(
                    "NO_BEIJING_MARKET_PERMISSION", 0
                ),
                unsupported_scope_excluded_count=universe.scope_exclusions.get(
                    "UNSUPPORTED_SECURITY_SCOPE", 0
                ),
                constituent_count=len(members),
                admitted_count=sum(item.admitted for item in candidates),
                quantified_count=len(lightweight),
                deep_review_count=len(deep),
                final_count=len(final),
            ),
            warnings=warnings,
            duration_ms=round((time.monotonic() - started) * 1000),
        )

    async def _market_context(
        self,
        request: DiscoveryRequest,
        warnings: list[str],
    ) -> Sequence[DailyBar]:
        try:
            market_result = await self.market.bars("SH", "000300")
            bars = (
                market_result.bars
                if isinstance(market_result, DiscoveryBarsSnapshot)
                else market_result
            )
            if request.business_date:
                bars = tuple(
                    item for item in bars
                    if item.trade_date <= request.business_date
                )
            if not bars:
                warnings.append("沪深300历史上下文为空，市场因子已按中性值降级")
                return ()
            if isinstance(market_result, DiscoveryBarsSnapshot):
                warnings.extend(
                    f"沪深300行情说明：{item}"
                    for item in market_result.warnings
                )
            return tuple(bars)
        except Exception as error:
            warnings.append(f"沪深300历史上下文不可用，市场因子已降级：{_safe(error)}")
            return ()

    async def _universe(self, limit: int) -> DiscoveryUniverse:
        warnings: list[str] = []
        diagnostics: list[str] = []
        for provider in self.providers:
            if provider.source_family != "TONGHUASHUN":
                detail = f"{provider.source_family} 不是允许的同花顺热门排名源"
                diagnostics.append(detail)
                warnings.append(detail)
                continue
            for attempt in range(1, self.provider_attempts + 1):
                try:
                    sectors = await asyncio.to_thread(provider.sectors, limit)
                    if not sectors:
                        raise RuntimeError("热门板块榜单为空")
                    if any(item.source_family != "TONGHUASHUN" for item in sectors):
                        raise RuntimeError("热门板块响应混入非同花顺来源")
                    universe = await self._members(provider, sectors, warnings)
                    if not universe.members:
                        raise RuntimeError("板块成分为空")
                    self._save_universe_snapshot(universe)
                    return universe
                except Exception as error:
                    detail = (
                        f"{provider.source_family}[{attempt}/{self.provider_attempts}] "
                        f"{_safe(error)}"
                    )
                    diagnostics.append(detail)
                    warnings.append(
                        f"{provider.source_family} 热门板块第{attempt}/"
                        f"{self.provider_attempts}次获取失败：{_safe(error)}"
                    )
                    if attempt < self.provider_attempts:
                        await asyncio.sleep(self.provider_retry_delay_seconds)
        snapshot = self._load_universe_snapshot(warnings)
        if snapshot is not None:
            return snapshot
        reason = "；".join(diagnostics)
        raise RuntimeError(f"同花顺热门板块或完整成分股不可用：{reason}")

    def _save_universe_snapshot(
        self,
        universe: DiscoveryUniverse,
    ) -> None:
        if self.universe_snapshot_path is None:
            return
        payload = {
            "snapshot_at": datetime.now().isoformat(),
            "source_code": universe.provider.source_code,
            "source_family": universe.provider.source_family,
            "sectors": [item.model_dump(mode="json") for item in universe.sectors],
            "members": {
                code: [market, name, sorted(sector_codes), sorted(sector_names)]
                for code, (market, name, sector_codes, sector_names)
                in universe.members.items()
            },
            "raw_constituent_count": universe.raw_constituent_count,
            "scope_exclusions": dict(universe.scope_exclusions),
            "constituent_sources": list(universe.constituent_sources),
            "constituent_quality_status": universe.constituent_quality_status,
        }
        try:
            self.universe_snapshot_path.parent.mkdir(parents=True, exist_ok=True)
            temporary = self.universe_snapshot_path.with_suffix(".tmp")
            temporary.write_text(
                json.dumps(payload, ensure_ascii=False, sort_keys=True),
                encoding="utf-8",
            )
            temporary.replace(self.universe_snapshot_path)
        except OSError as error:
            universe.warnings.append(f"热门板块快照保存失败：{_safe(error)}")

    def _load_universe_snapshot(
        self, warnings: list[str]
    ) -> DiscoveryUniverse | None:
        if self.universe_snapshot_path is None or not self.universe_snapshot_path.exists():
            return None
        try:
            payload = json.loads(
                self.universe_snapshot_path.read_text(encoding="utf-8")
            )
            if str(payload.get("source_family")) != "TONGHUASHUN":
                warnings.append("本地热门板块快照不是同花顺来源，拒绝使用")
                return None
            snapshot_at = datetime.fromisoformat(str(payload["snapshot_at"]))
            if (datetime.now() - snapshot_at).total_seconds() > 4 * 24 * 60 * 60:
                warnings.append("本地热门板块快照已超过 4 天有效期，拒绝继续使用")
                return None
            sectors = [DiscoverySector.model_validate(item) for item in payload["sectors"]]
            members = {
                code: (value[0], value[1], set(value[2]), set(value[3]))
                for code, value in payload["members"].items()
            }
            provider = SnapshotHotSectorProvider(
                source_code=str(payload["source_code"]),
                source_family=str(payload["source_family"]),
            )
            if not sectors or not members:
                return None
            warnings.append("在线同花顺热榜不可用，已使用最近一次同花顺热门板块快照")
            return DiscoveryUniverse(
                sectors=sectors,
                provider=provider,
                members=members,
                warnings=warnings,
                raw_constituent_count=int(
                    payload.get("raw_constituent_count", len(members))
                ),
                scope_exclusions={
                    str(key): int(value)
                    for key, value in payload.get("scope_exclusions", {}).items()
                },
                constituent_sources=tuple(payload.get("constituent_sources", [])),
                constituent_quality_status=str(
                    payload.get("constituent_quality_status", "CACHED_COMPLETE")
                ),
            )
        except (OSError, KeyError, TypeError, ValueError, json.JSONDecodeError) as error:
            warnings.append(f"本地热门板块快照不可用：{_safe(error)}")
            return None

    async def _members(
        self,
        provider: HotSectorProvider,
        sectors: Sequence[DiscoverySector],
        warnings: list[str],
    ) -> DiscoveryUniverse:
        semaphore = asyncio.Semaphore(self.network_concurrency)

        async def fetch(sector: DiscoverySector):
            async with semaphore:
                return await self._resolve_sector(sector, warnings)

        raw: dict[str, tuple[str, str, set[str], set[str]]] = {}
        resolved_sectors: list[DiscoverySector] = []
        sources: set[str] = set()
        for sector, batch in await asyncio.gather(*(fetch(item) for item in sectors)):
            resolved_sectors.append(sector)
            if batch is None:
                continue
            sources.add(batch.source_family)
            values = batch.values
            for code, market, name in values:
                current = raw.get(code)
                if current is None:
                    current = (market, name, set(), set())
                    raw[code] = current
                current[2].add(sector.code)
                current[3].add(sector.name)
        result: dict[str, tuple[str, str, set[str], set[str]]] = {}
        exclusions: dict[str, int] = {}
        for code, value in raw.items():
            decision = self.trading_scope.classify(code)
            if not decision.allowed or decision.market is None:
                reason = decision.reason or "UNSUPPORTED_SECURITY_SCOPE"
                exclusions[reason] = exclusions.get(reason, 0) + 1
                continue
            result[code] = (decision.market, value[1], value[2], value[3])
        statuses = {item.constituent_quality_status for item in resolved_sectors}
        quality = (
            "PARTIAL" if "PARTIAL" in statuses
            else "CACHED_COMPLETE" if "CACHED_COMPLETE" in statuses
            else "MIXED_COMPLETE" if "SUPPLEMENTED_COMPLETE" in statuses
            else "COMPLETE"
        )
        return DiscoveryUniverse(
            sectors=resolved_sectors,
            provider=provider,
            members=result,
            warnings=warnings,
            raw_constituent_count=len(raw),
            scope_exclusions=exclusions,
            constituent_sources=tuple(sorted(sources)),
            constituent_quality_status=quality,
        )

    async def _resolve_sector(
        self, sector: DiscoverySector, warnings: list[str]
    ) -> tuple[DiscoverySector, ConstituentBatch | None]:
        providers = self.constituent_providers
        first_batch: ConstituentBatch | None = None
        if providers:
            first_batch = await self._fetch_constituent_batch(
                providers[0], sector, warnings
            )
            if first_batch is not None and first_batch.quality_status == "COMPLETE":
                self._save_constituents(sector, first_batch, warnings)
                return self._resolved_sector(sector, first_batch, "COMPLETE"), first_batch
        for current in providers[1:]:
            batch = await self._fetch_constituent_batch(current, sector, warnings)
            if batch is not None and batch.quality_status == "COMPLETE":
                self._save_constituents(sector, batch, warnings)
                return (
                    self._resolved_sector(sector, batch, "SUPPLEMENTED_COMPLETE"),
                    batch,
                )
        cached = (
            self.constituent_snapshots.load(sector)
            if self.constituent_snapshots else None
        )
        if cached is not None:
            return self._resolved_sector(sector, cached, "CACHED_COMPLETE"), cached
        partial = first_batch
        warnings.append(f"{sector.name} 未取得完整成分，已跳过该板块")
        return self._resolved_sector(sector, partial, "PARTIAL"), None

    def close(self) -> None:
        for provider in self.constituent_providers:
            close = getattr(provider, "close", None)
            if not callable(close):
                continue
            try:
                close()
            except Exception:
                continue

    async def _fetch_constituent_batch(
        self, current: object, sector: DiscoverySector, warnings: list[str]
    ) -> ConstituentBatch | None:
        try:
            values = await asyncio.to_thread(current.constituents, sector)
            if isinstance(values, ConstituentBatch):
                if values.recovery_used:
                    path = " -> ".join(
                        attempt.mode.value
                        for attempt in values.acquisition_attempts
                    )
                    warnings.append(
                        f"{sector.name}：{values.source_family} 成分采集已恢复，"
                        f"路径 {path}，最终模式 {values.acquisition_mode}"
                    )
                if values.quality_status == "PARTIAL" and values.warning:
                    warnings.append(f"{sector.name}：{values.warning}")
                return values
            normalized = tuple(values)
            return ConstituentBatch(
                sector_code=sector.code,
                sector_name=sector.name,
                source_family=str(getattr(current, "source_family", "UNKNOWN")),
                values=normalized,
                expected_count=sector.expected_constituent_count or len(normalized),
                retrieved_count=len(normalized),
                quality_status="COMPLETE" if normalized else "PARTIAL",
                coverage=1.0 if normalized else 0.0,
                retrieved_at=datetime.now().isoformat(),
                warning="" if normalized else "成分股为空",
            )
        except Exception as error:
            warnings.append(
                f"{sector.name} 成分来源 {getattr(current, 'source_family', 'UNKNOWN')} "
                f"不可用：{_safe(error)}"
            )
            return None

    def _save_constituents(
        self,
        sector: DiscoverySector,
        batch: ConstituentBatch,
        warnings: list[str],
    ) -> None:
        if self.constituent_snapshots is None:
            return
        try:
            self.constituent_snapshots.save(sector, batch)
        except OSError as error:
            warnings.append(f"{sector.name} 完整成分快照保存失败：{_safe(error)}")

    def _resolved_sector(
        self,
        sector: DiscoverySector,
        batch: ConstituentBatch | None,
        status: str,
    ) -> DiscoverySector:
        return sector.model_copy(update={
            "resolved_constituent_count": batch.retrieved_count if batch else 0,
            "constituent_source_family": batch.source_family if batch else None,
            "constituent_quality_status": status,
            "constituent_coverage": batch.coverage if batch else 0.0,
        })

    def _quality_status(self, universe: DiscoveryUniverse) -> str:
        if isinstance(universe.provider, SnapshotHotSectorProvider):
            return "STALE_FALLBACK"
        if universe.constituent_quality_status == "PARTIAL":
            return "PARTIAL_FRESH"
        if universe.constituent_quality_status != "COMPLETE":
            return "FRESH_FALLBACK"
        return "FRESH_PRIMARY"

    async def _admit(
        self,
        members: dict[str, tuple[str, str, set[str], set[str]]],
        request: DiscoveryRequest,
        warnings: list[str],
    ) -> tuple[list[DiscoveryCandidate], dict[str, Sequence[DailyBar]]]:
        semaphore = asyncio.Semaphore(self.network_concurrency)
        bars_by_code: dict[str, Sequence[DailyBar]] = {}

        async def inspect(code: str, value: tuple[str, str, set[str], set[str]]):
            market, name, sector_codes, sector_names = value
            reasons: list[str] = []
            if "ST" in name.upper() or "退" in name:
                reasons.append("SPECIAL_TREATMENT")
            bars: Sequence[DailyBar] = ()
            if not reasons:
                try:
                    async with semaphore:
                        market_result = await self.market.bars(market, code)
                    if isinstance(market_result, DiscoveryBarsSnapshot):
                        bars = market_result.bars
                        warnings.extend(
                            f"{code} 行情说明：{item}"
                            for item in market_result.warnings
                        )
                        if market_result.quality_status == "UNAVAILABLE":
                            reasons.append("MARKET_DATA_UNAVAILABLE")
                        if (
                            market_result.stale_age_seconds is not None
                            and market_result.stale_age_seconds > 4 * 24 * 60 * 60
                        ):
                            reasons.append("STALE_MARKET_DATA")
                    else:
                        bars = market_result
                    if request.business_date:
                        bars = tuple(
                            item
                            for item in bars
                            if item.trade_date <= request.business_date
                        )
                except Exception as error:
                    warnings.append(f"{code} 行情不可用：{_safe(error)}")
                    reasons.append("MARKET_DATA_UNAVAILABLE")
            if bars and (len(bars) < 750 or any(item.adjustment != "QFQ" for item in bars)):
                reasons.append("INSUFFICIENT_QFQ_HISTORY")
            if not bars and "MARKET_DATA_UNAVAILABLE" not in reasons:
                reasons.append("MARKET_DATA_UNAVAILABLE")
            if bars and request.business_date:
                latest = date.fromisoformat(bars[-1].trade_date)
                expected = date.fromisoformat(request.business_date)
                if latest != expected:
                    reasons.append("STALE_OR_SUSPENDED_MARKET_DATA")
            price = float(bars[-1].close) if bars else 0.01
            lot_cost = price * 100 + max(5.0, price * 100 * 0.0003)
            if lot_cost > request.budget:
                reasons.append("OVER_BUDGET")
            if bars:
                amounts = [float(item.amount or 0.0) for item in bars[-20:]]
                if statistics_mean(amounts) < 5_000_000:
                    reasons.append("LOW_LIQUIDITY")
                bars_by_code[code] = bars
            candidate = DiscoveryCandidate(
                code=code,
                market=market,
                name=name,
                price=price,
                lot_cost=lot_cost,
                budget_eligible=bool(bars) and "OVER_BUDGET" not in reasons,
                admitted=not reasons,
                rejection_reasons=reasons,
                sector_codes=sorted(sector_codes),
                sector_names=sorted(sector_names),
                factors=_factors(bars) if len(bars) >= 61 else {},
            )
            return candidate

        candidates = await asyncio.gather(
            *(inspect(code, value) for code, value in sorted(members.items()))
        )
        return list(candidates), bars_by_code

    async def _deep(
        self,
        candidates: Sequence[DiscoveryCandidate],
        bars_by_code: dict[str, Sequence[DailyBar]],
        request: DiscoveryRequest,
        warnings: list[str],
        panel_artifact: PanelArtifact | None = None,
        market_bars: Sequence[DailyBar] = (),
    ) -> list[DeepCandidateEvidence]:
        semaphore = asyncio.Semaphore(self.deep_concurrency)

        async def evaluate(candidate: DiscoveryCandidate):
            try:
                async with semaphore:
                    if self._uses_default_forecast_builder:
                        payload = await asyncio.to_thread(
                            _forecast,
                            candidate,
                            bars_by_code[candidate.code],
                            request,
                            panel_artifact,
                            market_bars,
                        )
                    else:
                        payload = await asyncio.to_thread(
                            self.forecast_builder,
                            candidate,
                            bars_by_code[candidate.code],
                            request,
                        )
                return DeepCandidateEvidence(code=candidate.code, **payload)
            except Exception as error:
                warnings.append(f"{candidate.code} 深度预测失败：{_safe(error)}")
                return None

        values = await asyncio.gather(*(evaluate(item) for item in candidates))
        return [item for item in values if item is not None]

    def _train_panel_artifact(
        self,
        bars_by_code: Mapping[str, Sequence[DailyBar]],
        horizon_days: int,
        warnings: list[str],
        market_bars: Sequence[DailyBar] = (),
    ) -> PanelArtifact | None:
        existing = (
            self.panel_store.load(horizon_days, mode="PANEL_FULL")
            or self.panel_store.load(horizon_days, mode="PANEL_CORE")
            if self.panel_store
            else None
        )
        if self.panel_store is None:
            return existing
        histories = [
            (code, bars)
            for code, bars in sorted(bars_by_code.items())
            if len(bars) >= 750
        ][:300]
        try:
            samples_by_code = {
                f"{code}.{bars[0].symbol.market}": build_samples(
                    bars,
                    transaction_cost_rate=0.0015,
                    horizon_days=horizon_days,
                    context=build_aligned_context(bars, market_bars=market_bars),
                )
                for code, bars in histories
            }
            core = train_panel_artifact(
                samples_by_code,
                horizon_days=horizon_days,
            )
            self.panel_store.save(core)
            current_by_code = {
                f"{code}.{bars[0].symbol.market}": current_features(
                    bars,
                    context=build_aligned_context(bars, market_bars=market_bars),
                )
                for code, bars in histories
            }
            augmented, augmented_current, cross_codes = augment_cross_sectional_features(
                samples_by_code,
                current_by_code,
            )
            full = train_panel_artifact(
                augmented,
                horizon_days=horizon_days,
                mode="PANEL_FULL",
                feature_codes=(*FEATURE_CODES, *cross_codes),
                current_features_by_code=augmented_current,
            )
            self.panel_store.save(full)
            return full
        except (OSError, TypeError, ValueError) as error:
            warnings.append(f"联合模型未更新，继续使用个股模型或上一版产物：{_safe(error)}")
            return existing


def _forecast(
    candidate: DiscoveryCandidate,
    bars: Sequence[DailyBar],
    request: DiscoveryRequest,
    panel_artifact: PanelArtifact | None = None,
    market_bars: Sequence[DailyBar] = (),
) -> dict[str, object]:
    context = (
        build_aligned_context(bars, market_bars=market_bars)
        if bars else None
    )
    report = build_forecast(
        bars,
        instrument_code=f"{candidate.code}.{candidate.market}",
        source_code="STOCK_DISCOVERY_CACHE",
        source_family="LOCAL",
        quality_status="FRESH_PRIMARY",
        warnings=[],
        horizon_days=request.horizon_days,
        context=context,
        panel_artifact=panel_artifact,
    )
    qualification = report.qualification
    metrics = qualification.locked_test.calibrated_metrics if qualification else None
    performance_report = report.performance
    performance = performance_report.strategy if performance_report else None
    stability = report.parameter_stability
    backtest_audit = getattr(report, "backtest_audit", None)
    interval = report.probability_interval
    qualified = bool(
        report.status in {"ROBUST", "CONDITIONAL"}
        and report.decision == "UP"
        and qualification
        and qualification.status in {"QUALIFIED", "CONDITIONAL"}
        and report.up_probability is not None
        and performance_report is not None
        and performance_report.excess_return > 0
        and performance_report.strategy.sharpe_ratio
        >= performance_report.benchmark.sharpe_ratio
    )
    conclusion = (
        "ROBUST"
        if report.status == "ROBUST"
        else "CONDITIONALLY_EFFECTIVE"
        if report.status == "CONDITIONAL"
        else "INSUFFICIENT_DATA"
        if report.status == "INSUFFICIENT_DATA"
        else "NO_CLEAR_ADVANTAGE"
    )
    return {
        "qualified": qualified,
        "conclusion": conclusion,
        "calibrated_probability": report.up_probability or 0.5,
        "probability_lower_bound": interval.lower if interval and interval.lower else 0.0,
        "brier_skill_score": metrics.brier_skill_score if metrics else -1.0,
        "locked_accuracy": metrics.accuracy if metrics else 0.0,
        "locked_log_loss": metrics.log_loss if metrics else 99.0,
        "risk_adjusted_return": performance.sharpe_ratio if performance else -1.0,
        "max_drawdown": performance.max_drawdown if performance else -1.0,
        "stability_score": stability.positive_excess_ratio if stability else 0.0,
        "backtest_audit_status": backtest_audit.status if backtest_audit else None,
        "backtest_entry_date_agreement_rate": (
            backtest_audit.entry_date_agreement_rate if backtest_audit else None
        ),
        "backtest_return_delta": backtest_audit.return_delta if backtest_audit else None,
        "health_status": "HEALTHY" if qualified else "DEGRADED",
        "evidence": [report.decision_reason],
        "risks": list(report.warnings[:3]),
        "forecast_report": report.model_dump(mode="json", by_alias=True),
    }


def _factors(bars: Sequence[DailyBar]) -> dict[str, float]:
    closes = [float(item.close) for item in bars]
    amounts = [float(item.amount or 0.0) for item in bars]
    returns = [closes[index] / closes[index - 1] - 1 for index in range(1, len(closes))]
    recent = returns[-20:]
    volatility = math.sqrt(sum(item * item for item in recent) / max(1, len(recent)))
    downside = math.sqrt(
        sum(min(0.0, item) ** 2 for item in recent) / max(1, len(recent))
    )
    high = max(closes[-60:])
    running_high = closes[-60]
    max_drawdown = 0.0
    for close in closes[-60:]:
        running_high = max(running_high, close)
        max_drawdown = min(max_drawdown, close / running_high - 1.0)
    momentum_20 = closes[-1] / closes[-21] - 1.0
    momentum_5 = closes[-1] / closes[-6] - 1.0
    momentum_60 = closes[-1] / closes[-61] - 1.0
    return {
        "relative_momentum_20": momentum_20,
        "momentum_5": momentum_5,
        "momentum_60": momentum_60,
        "trend_consistency": sum(item > 0 for item in recent) / len(recent),
        "liquidity": math.log1p(statistics_mean(amounts[-20:])),
        "volatility_20": volatility,
        "downside_volatility_20": downside,
        "drawdown_60": max_drawdown,
        "chase_risk": max(0.0, closes[-1] / high - 0.98),
    }


def statistics_mean(values: Sequence[float]) -> float:
    return sum(values) / len(values) if values else 0.0


def _safe(error: Exception) -> str:
    return str(error).replace("\n", " ").replace("\r", " ")[:240]
