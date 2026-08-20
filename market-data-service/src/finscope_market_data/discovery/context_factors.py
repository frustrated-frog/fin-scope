from __future__ import annotations

from collections import defaultdict
import statistics
from typing import Iterable, Sequence

from finscope_market_data.discovery.schemas import (
    DiscoveryCandidate,
    DiscoverySector,
)


def enrich_context_factors(
    candidates: Iterable[DiscoveryCandidate],
    sectors: Sequence[DiscoverySector],
) -> list[DiscoveryCandidate]:
    values = [item.model_copy(deep=True) for item in candidates]
    usable = [item for item in values if item.factors]
    if not usable:
        return values
    universe_breadth_5 = statistics.fmean(
        item.factors.get("momentum_5", 0.0) > 0.0 for item in usable
    )
    universe_breadth_20 = statistics.fmean(
        item.factors.get("relative_momentum_20", 0.0) > 0.0 for item in usable
    )
    sector_members: dict[str, list[DiscoveryCandidate]] = defaultdict(list)
    for item in usable:
        for sector_code in set(item.sector_codes):
            sector_members[sector_code].append(item)
    sector_momentum = {
        code: statistics.fmean(
            item.factors.get("relative_momentum_20", 0.0) for item in members
        )
        for code, members in sector_members.items()
        if members
    }
    sector_breadth = {
        code: statistics.fmean(
            item.factors.get("relative_momentum_20", 0.0) > 0.0
            for item in members
        )
        for code, members in sector_members.items()
        if members
    }
    maximum_rank = max((item.source_rank for item in sectors), default=1)
    rank_denominator = max(1, maximum_rank - 1)
    flow_quality = {
        item.code: 1.0 - (item.source_rank - 1) / rank_denominator
        for item in sectors
    }
    activity_ranks = _percentile_ranks({
        item.code: item.factors.get("liquidity", 0.0) for item in usable
    })
    for item in usable:
        linked = [code for code in item.sector_codes if code in sector_momentum]
        current_momentum = item.factors.get("relative_momentum_20", 0.0)
        item.factors.update({
            "universe_breadth_5": universe_breadth_5,
            "universe_breadth_20": universe_breadth_20,
            "sector_breadth_20": (
                statistics.fmean(sector_breadth[code] for code in linked)
                if linked else universe_breadth_20
            ),
            "relative_momentum_20_sector": (
                current_momentum
                - statistics.fmean(sector_momentum[code] for code in linked)
                if linked else 0.0
            ),
            "sector_flow_rank_quality": (
                statistics.fmean(flow_quality.get(code, 0.0) for code in linked)
                if linked else 0.0
            ),
            "cross_activity_rank": activity_ranks[item.code],
        })
    return values


def _percentile_ranks(values: dict[str, float]) -> dict[str, float]:
    ordered = sorted((value, code) for code, value in values.items())
    denominator = max(1, len(ordered) - 1)
    result: dict[str, float] = {}
    start = 0
    while start < len(ordered):
        end = start + 1
        while end < len(ordered) and ordered[end][0] == ordered[start][0]:
            end += 1
        midpoint = ((start + end - 1) / 2.0) / denominator
        for _, code in ordered[start:end]:
            result[code] = midpoint
        start = end
    return result
