from __future__ import annotations

import statistics
from typing import Iterable

from finscope_market_data.discovery.schemas import (
    DeepCandidateEvidence,
    DiscoveryCandidate,
)


POSITIVE_FACTORS = (
    "relative_momentum_20",
    "momentum_60",
    "trend_consistency",
    "liquidity",
    "relative_momentum_20_sector",
    "sector_breadth_20",
    "sector_flow_rank_quality",
    "cross_activity_rank",
)
RISK_FACTORS = (
    "volatility_20",
    "downside_volatility_20",
    "chase_risk",
)


def rank_lightweight_candidates(
    candidates: Iterable[DiscoveryCandidate],
) -> list[DiscoveryCandidate]:
    admitted = [item.model_copy(deep=True) for item in candidates if item.admitted]
    if not admitted:
        return []
    standardized = {
        factor: _robust_z([item.factors.get(factor, 0.0) for item in admitted])
        for factor in (*POSITIVE_FACTORS, *RISK_FACTORS, "drawdown_60")
    }
    for index, item in enumerate(admitted):
        positive = statistics.fmean(
            standardized[factor][index] for factor in POSITIVE_FACTORS
        )
        risk = statistics.fmean(
            standardized[factor][index] for factor in RISK_FACTORS
        )
        drawdown_penalty = -standardized["drawdown_60"][index]
        item.lightweight_score = round(
            positive * 0.65 - risk * 0.25 - drawdown_penalty * 0.10,
            8,
        )
    admitted.sort(key=lambda item: (-(item.lightweight_score or 0.0), item.code))
    for rank, item in enumerate(admitted, start=1):
        item.lightweight_rank = rank
    return admitted


def rank_deep_candidates(
    candidates: Iterable[DeepCandidateEvidence], final_limit: int = 5
) -> list[DeepCandidateEvidence]:
    qualified = [
        item.model_copy(deep=True)
        for item in candidates
        if item.qualified
        and item.health_status == "HEALTHY"
        and item.conclusion not in {"NO_CLEAR_ADVANTAGE", "INSUFFICIENT_DATA"}
    ]
    for item in qualified:
        drawdown_quality = max(0.0, 1.0 + item.max_drawdown)
        item.deep_score = round(
            item.probability_lower_bound * 0.30
            + item.calibrated_probability * 0.10
            + item.brier_skill_score * 0.15
            + item.locked_accuracy * 0.10
            + max(0.0, 1.0 - item.locked_log_loss) * 0.05
            + item.risk_adjusted_return * 0.10
            + drawdown_quality * 0.05
            + item.stability_score * 0.15,
            8,
        )
    qualified.sort(key=lambda item: (-(item.deep_score or 0.0), item.code))
    selected = qualified[: max(0, final_limit)]
    for rank, item in enumerate(selected, start=1):
        item.final_rank = rank
    return selected


def _robust_z(values: list[float]) -> list[float]:
    if not values:
        return []
    median = statistics.median(values)
    deviations = [abs(value - median) for value in values]
    scale = statistics.median(deviations) * 1.4826
    if scale < 1e-12:
        return [0.0 for _ in values]
    return [max(-3.0, min(3.0, (value - median) / scale)) for value in values]
