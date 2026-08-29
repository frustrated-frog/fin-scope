from __future__ import annotations

from datetime import date, timedelta
import math

import pytest
from pydantic import ValidationError

from finscope_market_data.discovery.evaluation import evaluate_discovery_outcomes
from finscope_market_data.discovery.schemas import (
    DiscoveryEvaluationRequest,
    DiscoveryModelObservation,
    DiscoveryOutcomeObservation,
)


def _outcome(
    run_id: int,
    code: str,
    actual_return: float,
    *,
    rank: int | None,
    probability: float | None,
    as_of_date: str,
    sector: str = "半导体",
) -> DiscoveryOutcomeObservation:
    return DiscoveryOutcomeObservation(
        run_id=run_id,
        instrument_code=f"{code}.SZ",
        as_of_date=as_of_date,
        horizon_days=5,
        admitted=True,
        final_rank=rank,
        calibrated_probability=probability,
        actual_net_return=actual_return,
        actual_direction="UP" if actual_return > 0 else "DOWN",
        sector_names=[sector],
    )


def test_evaluation_rejects_duplicate_frozen_candidate_observations() -> None:
    observation = _outcome(
        1, "000001", 0.02, rank=1, probability=0.7, as_of_date="2026-08-01"
    )

    with pytest.raises(ValueError, match="重复"):
        evaluate_discovery_outcomes(
            DiscoveryEvaluationRequest(observations=[observation, observation])
        )


def test_evaluation_reports_probability_quality_top_k_and_rolling_windows() -> None:
    observations = [
        _outcome(1, "000001", 0.10, rank=1, probability=0.80, as_of_date="2026-06-01"),
        _outcome(1, "000002", 0.05, rank=2, probability=0.70, as_of_date="2026-06-01"),
        _outcome(1, "000003", -0.02, rank=3, probability=0.40, as_of_date="2026-06-01"),
        _outcome(1, "000004", -0.04, rank=None, probability=None, as_of_date="2026-06-01"),
        _outcome(2, "000005", -0.01, rank=1, probability=0.60, as_of_date="2026-08-18"),
        _outcome(2, "000006", 0.08, rank=2, probability=0.75, as_of_date="2026-08-18"),
        _outcome(2, "000007", 0.02, rank=3, probability=0.65, as_of_date="2026-08-18"),
        _outcome(2, "000008", -0.03, rank=None, probability=None, as_of_date="2026-08-18"),
    ]

    report = evaluate_discovery_outcomes(
        DiscoveryEvaluationRequest(
            as_of_date="2026-08-20",
            pending_count=7,
            observations=observations,
        )
    )

    assert report.status == "ACCUMULATING"
    assert report.matured_run_count == 2
    assert report.matured_candidate_count == 8
    assert report.matured_final_count == 6
    assert report.pending_count == 7
    assert report.probability_quality.sample_count == 6
    assert math.isfinite(report.probability_quality.brier_score)
    top_one = next(item for item in report.selection_metrics if item.limit == 1)
    assert top_one.sample_count == 2
    assert top_one.hit_rate == pytest.approx(0.5)
    assert top_one.average_net_return == pytest.approx(0.045)
    assert top_one.average_excess_vs_admitted_pool == pytest.approx(0.02625)
    last_30 = next(item for item in report.windows if item.window_days == 30)
    assert last_30.matured_run_count == 1
    assert last_30.final_count == 3
    assert len(report.reliability_bins) == 5
    assert report.sector_performance[0].sector_name == "半导体"
    assert report.ranking_challenger.status == "SHADOW_ACCUMULATING"


def test_evaluation_runs_date_grouped_pairwise_ranking_challenger() -> None:
    start = date(2025, 1, 1)
    observations: list[DiscoveryOutcomeObservation] = []
    for day in range(60):
        as_of = (start + timedelta(days=day)).isoformat()
        for index in range(6):
            quality = (5 - index) / 5.0
            observations.append(_outcome(
                day + 1,
                f"{100000 + index:06d}",
                quality * 0.03 - 0.01,
                rank=index + 1 if index < 5 else None,
                probability=0.45 + quality * 0.4,
                as_of_date=as_of,
            ))

    report = evaluate_discovery_outcomes(
        DiscoveryEvaluationRequest(observations=observations)
    )

    assert report.ranking_challenger.status == "PROMOTION_REVIEW"
    assert report.ranking_challenger.locked_date_count >= 10
    assert report.ranking_challenger.rank_ic > 0
    assert report.ranking_challenger.top_k_excess_return > 0


def test_model_race_requires_real_paired_evidence_before_review() -> None:
    start = date(2026, 1, 1)
    models: list[DiscoveryModelObservation] = []
    for index in range(30):
        actual = "UP" if index % 2 == 0 else "DOWN"
        as_of = (start + timedelta(days=index)).isoformat()
        models.extend([
            DiscoveryModelObservation(
                run_id=index + 1,
                instrument_code=f"{index:06d}.SZ",
                as_of_date=as_of,
                horizon_days=5,
                model_code="LOGISTIC",
                model_name="正则逻辑回归",
                role="CHAMPION",
                calibrated_probability=0.60,
                shadow_decision="UP",
                qualification_status="QUALIFIED",
                actual_direction=actual,
            ),
            DiscoveryModelObservation(
                run_id=index + 1,
                instrument_code=f"{index:06d}.SZ",
                as_of_date=as_of,
                horizon_days=5,
                model_code="BOOSTED_STUMPS",
                model_name="轻量梯度提升树桩",
                role="CHALLENGER",
                calibrated_probability=0.80 if actual == "UP" else 0.20,
                shadow_decision=actual,
                qualification_status="QUALIFIED",
                actual_direction=actual,
            ),
        ])

    report = evaluate_discovery_outcomes(
        DiscoveryEvaluationRequest(model_observations=models)
    )

    assert report.model_race.status == "PROMOTION_REVIEW"
    assert report.model_race.champion_code == "LOGISTIC"
    assert report.model_race.promotion_candidate_code == "BOOSTED_STUMPS"
    challenger = next(
        item for item in report.model_race.candidates
        if item.model_code == "BOOSTED_STUMPS"
    )
    assert challenger.sample_count == 30
    assert challenger.covered_accuracy == 1
    assert challenger.promotion_eligible is True


def test_evaluation_schema_rejects_non_finite_returns() -> None:
    with pytest.raises(ValidationError):
        DiscoveryOutcomeObservation(
            run_id=1,
            instrument_code="000001.SZ",
            as_of_date="2026-08-01",
            horizon_days=5,
            admitted=True,
            actual_net_return=float("nan"),
            actual_direction="UP",
        )
