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
from finscope_market_data.discovery.ranking import (
    rank_deep_candidates,
    rank_lightweight_candidates,
)
from finscope_market_data.discovery.schemas import (
    DeepCandidateEvidence,
    DiscoveryCandidate,
    DiscoveryFunnel,
    DiscoveryReport,
    DiscoveryRequest,
    DiscoverySector,
)
from finscope_market_data.forecast.service import build_forecast
from finscope_market_data.forecast.features import build_samples
from finscope_market_data.forecast.panel import (
    PanelArtifact,
    PanelArtifactStore,
    train_panel_artifact,
)
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

    def constituents(self, sector: DiscoverySector) -> list[tuple[str, str, str]]:
        raise RuntimeError("snapshot provider does not fetch online constituents")


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
        self._uses_default_forecast_builder = forecast_builder is None

    async def discover(self, request: DiscoveryRequest) -> DiscoveryReport:
        started = time.monotonic()
        sectors, provider, members, warnings = await self._universe(
            request.sector_limit
        )
        candidates, bars_by_code = await self._admit(members, request, warnings)
        panel_artifact = self._train_panel_artifact(
            bars_by_code,
            request.horizon_days,
            warnings,
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
            quality_status=(
                "STALE_FALLBACK"
                if isinstance(provider, SnapshotHotSectorProvider)
                else "FRESH_PRIMARY"
                if not warnings
                else "FRESH_FALLBACK"
            ),
            retrieved_at=datetime.now().isoformat(),
            data_fingerprint=fingerprint,
            budget=request.budget,
            sectors=sectors,
            candidates=ordered_candidates,
            deep_evidence=deep,
            final_candidates=final,
            funnel=DiscoveryFunnel(
                constituent_count=len(members),
                admitted_count=sum(item.admitted for item in candidates),
                quantified_count=len(lightweight),
                deep_review_count=len(deep),
                final_count=len(final),
            ),
            warnings=warnings,
            duration_ms=round((time.monotonic() - started) * 1000),
        )

    async def _universe(
        self, limit: int
    ) -> tuple[
        list[DiscoverySector],
        HotSectorProvider,
        dict[str, tuple[str, str, set[str], set[str]]],
        list[str],
    ]:
        warnings: list[str] = []
        diagnostics: list[str] = []
        for provider in self.providers:
            for attempt in range(1, self.provider_attempts + 1):
                try:
                    sectors = await asyncio.to_thread(provider.sectors, limit)
                    if not sectors:
                        raise RuntimeError("热门板块榜单为空")
                    members = await self._members(provider, sectors, warnings)
                    if not members:
                        raise RuntimeError("板块成分为空")
                    constituent_family = getattr(
                        provider, "constituent_source_family", provider.source_family
                    )
                    if constituent_family != provider.source_family:
                        warnings.append(
                            f"{provider.source_family} 提供热门榜单，"
                            f"{constituent_family} 提供板块成分关系"
                        )
                    self._save_universe_snapshot(sectors, provider, members, warnings)
                    return sectors, provider, members, warnings
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
        raise RuntimeError(f"所有热门板块或成分股数据源均不可用：{reason}")

    def _save_universe_snapshot(
        self,
        sectors: Sequence[DiscoverySector],
        provider: HotSectorProvider,
        members: dict[str, tuple[str, str, set[str], set[str]]],
        warnings: list[str],
    ) -> None:
        if self.universe_snapshot_path is None:
            return
        payload = {
            "snapshot_at": datetime.now().isoformat(),
            "source_code": provider.source_code,
            "source_family": provider.source_family,
            "sectors": [item.model_dump(mode="json") for item in sectors],
            "members": {
                code: [market, name, sorted(sector_codes), sorted(sector_names)]
                for code, (market, name, sector_codes, sector_names) in members.items()
            },
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
            warnings.append(f"热门板块快照保存失败：{_safe(error)}")

    def _load_universe_snapshot(
        self, warnings: list[str]
    ) -> tuple[
        list[DiscoverySector],
        HotSectorProvider,
        dict[str, tuple[str, str, set[str], set[str]]],
        list[str],
    ] | None:
        if self.universe_snapshot_path is None or not self.universe_snapshot_path.exists():
            return None
        try:
            payload = json.loads(
                self.universe_snapshot_path.read_text(encoding="utf-8")
            )
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
            warnings.append("在线热门板块源均不可用，已使用最近一次本地热门板块快照")
            return sectors, provider, members, warnings
        except (OSError, KeyError, TypeError, ValueError, json.JSONDecodeError) as error:
            warnings.append(f"本地热门板块快照不可用：{_safe(error)}")
            return None

    async def _members(
        self,
        provider: HotSectorProvider,
        sectors: Sequence[DiscoverySector],
        warnings: list[str],
    ) -> dict[str, tuple[str, str, set[str], set[str]]]:
        semaphore = asyncio.Semaphore(self.network_concurrency)

        async def fetch(sector: DiscoverySector):
            async with semaphore:
                try:
                    return sector, await asyncio.to_thread(provider.constituents, sector)
                except Exception as error:
                    warnings.append(f"{sector.name} 成分股不可用：{_safe(error)}")
                    return sector, []

        result: dict[str, tuple[str, str, set[str], set[str]]] = {}
        for sector, values in await asyncio.gather(*(fetch(item) for item in sectors)):
            for code, market, name in values:
                if market not in {"SH", "SZ"}:
                    continue
                current = result.get(code)
                if current is None:
                    current = (market, name, set(), set())
                    result[code] = current
                current[2].add(sector.code)
                current[3].add(sector.name)
        return result

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
    ) -> PanelArtifact | None:
        existing = self.panel_store.load(horizon_days) if self.panel_store else None
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
                )
                for code, bars in histories
            }
            artifact = train_panel_artifact(
                samples_by_code,
                horizon_days=horizon_days,
            )
            self.panel_store.save(artifact)
            return artifact
        except (OSError, TypeError, ValueError) as error:
            warnings.append(f"联合模型未更新，继续使用个股模型或上一版产物：{_safe(error)}")
            return existing


def _forecast(
    candidate: DiscoveryCandidate,
    bars: Sequence[DailyBar],
    request: DiscoveryRequest,
    panel_artifact: PanelArtifact | None = None,
) -> dict[str, object]:
    report = build_forecast(
        bars,
        instrument_code=f"{candidate.code}.{candidate.market}",
        source_code="STOCK_DISCOVERY_CACHE",
        source_family="LOCAL",
        quality_status="FRESH_PRIMARY",
        warnings=[],
        horizon_days=request.horizon_days,
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
    momentum_60 = closes[-1] / closes[-61] - 1.0
    return {
        "relative_momentum_20": momentum_20,
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
