from __future__ import annotations

from datetime import date, timedelta
import math

from finscope_market_data.discovery.pairwise_ranker import (
    PairwiseRankingObservation,
    evaluate_pairwise_ranker,
)


def _observations(days: int = 60) -> list[PairwiseRankingObservation]:
    first = date(2025, 1, 1)
    result: list[PairwiseRankingObservation] = []
    for day in range(days):
        business_date = (first + timedelta(days=day)).isoformat()
        for index in range(6):
            quality = (index - 2.5) / 3.0
            result.append(PairwiseRankingObservation(
                group_id=business_date,
                instrument_code=f"{600000 + index}.SH",
                features=(quality, math.sin(day / 9.0) + quality * 0.2),
                actual_net_return=quality * 0.025 + math.sin(day / 7.0) * 0.003,
            ))
    return result


def test_pairwise_ranker_is_order_invariant_and_uses_date_groups() -> None:
    observations = _observations()

    first = evaluate_pairwise_ranker(observations, top_k=3)
    second = evaluate_pairwise_ranker(list(reversed(observations)), top_k=3)

    assert first == second
    assert first.status == "PROMOTION_REVIEW"
    assert first.training_date_count > 0
    assert first.locked_date_count > 0
    assert first.pair_count > 0
    assert first.rank_ic > 0
    assert first.top_k_excess_return > 0
    assert len(first.feature_weights) == 2


def test_pairwise_ranker_accumulates_when_independent_dates_are_insufficient() -> None:
    result = evaluate_pairwise_ranker(_observations(8), top_k=3)

    assert result.status == "SHADOW_ACCUMULATING"
    assert result.reason is not None
    assert result.pair_count == 0
