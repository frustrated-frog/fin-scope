from __future__ import annotations

import pytest

from finscope_market_data.discovery.context_factors import enrich_context_factors
from finscope_market_data.discovery.schemas import DiscoveryCandidate, DiscoverySector


def _candidate(
    code: str,
    sector_code: str,
    momentum_5: float,
    momentum_20: float,
    liquidity: float,
) -> DiscoveryCandidate:
    return DiscoveryCandidate(
        code=code,
        market="SZ",
        name=code,
        price=10,
        lot_cost=1005,
        budget_eligible=True,
        admitted=True,
        sector_codes=[sector_code],
        sector_names=[sector_code],
        factors={
            "momentum_5": momentum_5,
            "relative_momentum_20": momentum_20,
            "liquidity": liquidity,
        },
    )


def _sector(code: str, rank: int) -> DiscoverySector:
    return DiscoverySector(
        code=code,
        name=code,
        category="INDUSTRY",
        source_code="THS",
        source_family="TONGHUASHUN",
        source_rank=rank,
        retrieved_at="2026-08-20T15:30:00",
    )


def test_context_factors_use_only_current_cross_section_and_sector_membership() -> None:
    values = [
        _candidate("000001", "A", 0.10, 0.20, 20),
        _candidate("000002", "A", -0.02, 0.00, 10),
        _candidate("000003", "B", 0.03, 0.10, 30),
        _candidate("000004", "B", -0.04, -0.10, 40),
    ]

    enriched = enrich_context_factors(values, [_sector("A", 1), _sector("B", 2)])
    by_code = {item.code: item for item in enriched}

    assert by_code["000001"].factors["universe_breadth_5"] == pytest.approx(0.5)
    assert by_code["000001"].factors["universe_breadth_20"] == pytest.approx(0.5)
    assert by_code["000001"].factors["sector_breadth_20"] == pytest.approx(0.5)
    assert by_code["000001"].factors["relative_momentum_20_sector"] == pytest.approx(0.10)
    assert by_code["000001"].factors["sector_flow_rank_quality"] == 1.0
    assert by_code["000004"].factors["sector_flow_rank_quality"] == 0.0
    assert by_code["000004"].factors["cross_activity_rank"] == 1.0
    assert by_code["000002"].factors["cross_activity_rank"] == 0.0


def test_context_factors_assign_midpoint_rank_to_equal_activity() -> None:
    values = [
        _candidate("000001", "A", 0.1, 0.1, 20),
        _candidate("000002", "A", 0.1, 0.1, 20),
        _candidate("000003", "A", 0.1, 0.1, 30),
    ]

    enriched = enrich_context_factors(values, [_sector("A", 1)])

    assert enriched[0].factors["cross_activity_rank"] == pytest.approx(0.25)
    assert enriched[1].factors["cross_activity_rank"] == pytest.approx(0.25)
    assert enriched[2].factors["cross_activity_rank"] == 1.0
