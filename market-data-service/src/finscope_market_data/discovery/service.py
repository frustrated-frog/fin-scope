from __future__ import annotations

import asyncio
from datetime import datetime
import hashlib
import json
import math
import time
from typing import Awaitable, Callable, Protocol, Sequence

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
from finscope_market_data.models import DailyBar


class DiscoveryMarket(Protocol):
    async def bars(self, market: str, code: str) -> Sequence[DailyBar]: ...


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
    ) -> None:
        self.providers = tuple(providers)
        self.market = market
        self.forecast_builder = forecast_builder or _forecast
        self.network_concurrency = network_concurrency
        self.deep_concurrency = deep_concurrency

    async def discover(self, request: DiscoveryRequest) -> DiscoveryReport:
        started = time.monotonic()
        sectors, provider, warnings = await self._sectors(request.sector_limit)
        members = await self._members(provider, sectors, warnings)
        candidates, bars_by_code = await self._admit(members, request, warnings)
        lightweight = rank_lightweight_candidates(candidates)
        by_code = {item.code: item for item in candidates}
        for ranked in lightweight:
            by_code[ranked.code] = ranked
        deep_targets = lightweight[: request.deep_limit]
        deep = await self._deep(deep_targets, bars_by_code, request, warnings)
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
            "sectors": [item.model_dump(mode="json") for item in sectors],
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
            quality_status="FRESH_PRIMARY" if not warnings else "FRESH_FALLBACK",
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

    async def _sectors(
        self, limit: int
    ) -> tuple[list[DiscoverySector], HotSectorProvider, list[str]]:
        warnings: list[str] = []
        for provider in self.providers:
            try:
                sectors = await asyncio.to_thread(provider.sectors, limit)
                if sectors:
                    return sectors, provider, warnings
            except Exception as error:
                warnings.append(
                    f"{provider.source_family} 热门板块不可用：{_safe(error)}"
                )
        raise RuntimeError("所有热门板块数据源均不可用")

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
                        bars = await self.market.bars(market, code)
                except Exception as error:
                    warnings.append(f"{code} 行情不可用：{_safe(error)}")
                    reasons.append("MARKET_DATA_UNAVAILABLE")
            if bars and (len(bars) < 750 or any(item.adjustment != "QFQ" for item in bars)):
                reasons.append("INSUFFICIENT_QFQ_HISTORY")
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
                budget_eligible="OVER_BUDGET" not in reasons,
                admitted=not reasons,
                rejection_reasons=reasons,
                sector_codes=sorted(sector_codes),
                sector_names=sorted(sector_names),
                factors=_factors(bars) if bars else {},
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
    ) -> list[DeepCandidateEvidence]:
        semaphore = asyncio.Semaphore(self.deep_concurrency)

        async def evaluate(candidate: DiscoveryCandidate):
            try:
                async with semaphore:
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


def _forecast(
    candidate: DiscoveryCandidate,
    bars: Sequence[DailyBar],
    request: DiscoveryRequest,
) -> dict[str, object]:
    report = build_forecast(
        bars,
        instrument_code=f"{candidate.code}.{candidate.market}",
        source_code="STOCK_DISCOVERY_CACHE",
        source_family="LOCAL",
        quality_status="FRESH_PRIMARY",
        warnings=[],
        horizon_days=request.horizon_days,
    )
    qualification = report.qualification
    metrics = qualification.locked_test.calibrated_metrics if qualification else None
    performance = report.performance.strategy if report.performance else None
    stability = report.parameter_stability
    interval = report.probability_interval
    qualified = bool(
        report.status in {"ROBUST", "CONDITIONAL"}
        and qualification
        and qualification.status in {"QUALIFIED", "CONDITIONAL"}
        and report.up_probability is not None
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
