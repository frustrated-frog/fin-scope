from __future__ import annotations

import pytest
from pydantic import ValidationError

from finscope_market_data.discovery.ranking import (
    rank_deep_candidates,
    rank_lightweight_candidates,
    rank_relative_candidates,
)
from finscope_market_data.discovery.schemas import (
    DeepCandidateEvidence,
    DiscoveryCandidate,
    DiscoveryRequest,
)


def test_discovery_request_accepts_java_camel_case_contract() -> None:
    request = DiscoveryRequest.model_validate(
        {
            "businessDate": "2026-08-14",
            "budget": 5500,
            "sectorLimit": 4,
            "deepLimit": 10,
            "finalLimit": 3,
            "horizonDays": 1,
            "policyVersion": "stock-discovery-v2",
        }
    )

    assert request.business_date == "2026-08-14"
    assert request.sector_limit == 4
    assert request.deep_limit == 10
    assert request.final_limit == 3
    assert request.horizon_days == 1
    assert request.policy_version == "stock-discovery-v2"


@pytest.mark.parametrize(
    "payload",
    [
        {"businessDate": "2026-8-1"},
        {"businessDate": "not-a-date"},
        {"policyVersion": ""},
        {"unexpected": True},
    ],
)
def test_discovery_request_rejects_contract_drift(payload: dict[str, object]) -> None:
    with pytest.raises(ValidationError):
        DiscoveryRequest.model_validate(payload)


def test_deep_evidence_rejects_unknown_health_and_conclusion_states() -> None:
    with pytest.raises(ValidationError):
        DeepCandidateEvidence(
            code="000001",
            qualified=True,
            conclusion="MAYBE",
            calibrated_probability=0.6,
            probability_lower_bound=0.5,
            brier_skill_score=0.1,
            locked_accuracy=0.55,
            locked_log_loss=0.65,
            risk_adjusted_return=0.3,
            max_drawdown=-0.1,
            stability_score=0.7,
            health_status="UNKNOWN",
        )


def candidate(code: str, price: float, momentum: float, risk: float = 0.1) -> DiscoveryCandidate:
    return DiscoveryCandidate(
        code=code,
        market="SH" if code.startswith("6") else "SZ",
        name=f"股票{code}",
        price=price,
        lot_cost=price * 100,
        budget_eligible=True,
        admitted=True,
        sector_codes=["BK0001"],
        sector_names=["测试板块"],
        factors={
            "relative_momentum_20": momentum,
            "momentum_60": momentum * 0.7,
            "trend_consistency": 0.8,
            "liquidity": 0.6,
            "volatility_20": risk,
            "downside_volatility_20": risk,
            "drawdown_60": -risk,
            "chase_risk": 0.0,
        },
    )


def test_budget_is_an_admission_rule_but_does_not_improve_lightweight_score() -> None:
    cheap = candidate("000001", price=8.0, momentum=0.12)
    expensive = candidate("600001", price=58.0, momentum=0.12)

    ranked = rank_lightweight_candidates([cheap, expensive])

    assert ranked[0].lightweight_score == ranked[1].lightweight_score
    assert {item.code for item in ranked} == {"000001", "600001"}


def test_lightweight_ranking_is_deterministic_for_shuffled_input() -> None:
    values = [
        candidate("000001", 10.0, 0.04, 0.16),
        candidate("000002", 12.0, 0.18, 0.10),
        candidate("600001", 20.0, 0.09, 0.12),
    ]

    forward = rank_lightweight_candidates(values)
    reverse = rank_lightweight_candidates(list(reversed(values)))

    assert [item.code for item in forward] == [item.code for item in reverse]
    assert [item.lightweight_score for item in forward] == [
        item.lightweight_score for item in reverse
    ]


def test_lightweight_ranking_rewards_point_in_time_sector_strength() -> None:
    strong = candidate("000001", price=10.0, momentum=0.10)
    weak = candidate("600001", price=10.0, momentum=0.10)
    strong.factors.update({
        "relative_momentum_20_sector": 0.08,
        "sector_breadth_20": 0.8,
        "sector_flow_rank_quality": 1.0,
        "cross_activity_rank": 0.9,
    })
    weak.factors.update({
        "relative_momentum_20_sector": -0.08,
        "sector_breadth_20": 0.2,
        "sector_flow_rank_quality": 0.0,
        "cross_activity_rank": 0.1,
    })

    ranked = rank_lightweight_candidates([weak, strong])

    assert [item.code for item in ranked] == ["000001", "600001"]


def test_final_ranking_returns_only_candidates_that_pass_deep_gates() -> None:
    evidence = [
        DeepCandidateEvidence(
            code="000001",
            qualified=True,
            conclusion="CONDITIONALLY_EFFECTIVE",
            calibrated_probability=0.61,
            probability_lower_bound=0.54,
            brier_skill_score=0.08,
            locked_accuracy=0.57,
            locked_log_loss=0.66,
            risk_adjusted_return=0.42,
            max_drawdown=-0.12,
            stability_score=0.73,
            health_status="HEALTHY",
        ),
        DeepCandidateEvidence(
            code="000002",
            qualified=False,
            conclusion="NO_CLEAR_ADVANTAGE",
            calibrated_probability=0.68,
            probability_lower_bound=0.57,
            brier_skill_score=-0.02,
            locked_accuracy=0.51,
            locked_log_loss=0.72,
            risk_adjusted_return=0.30,
            max_drawdown=-0.16,
            stability_score=0.35,
            health_status="DEGRADED",
        ),
        DeepCandidateEvidence(
            code="600001",
            qualified=True,
            conclusion="ROBUST",
            calibrated_probability=0.64,
            probability_lower_bound=0.56,
            brier_skill_score=0.11,
            locked_accuracy=0.59,
            locked_log_loss=0.63,
            risk_adjusted_return=0.55,
            max_drawdown=-0.10,
            stability_score=0.82,
            health_status="HEALTHY",
        ),
    ]

    ranked = rank_deep_candidates(evidence, final_limit=5)

    assert [item.code for item in ranked] == ["600001", "000001"]
    assert [item.final_rank for item in ranked] == [1, 2]
    assert all(item.qualified for item in ranked)


def test_relative_ranking_returns_best_research_candidates_without_faking_qualification() -> None:
    evidence = [
        DeepCandidateEvidence(
            code=f"00000{index}",
            qualified=False,
            conclusion="NO_CLEAR_ADVANTAGE",
            calibrated_probability=0.50 + index * 0.02,
            probability_lower_bound=0.42 + index * 0.02,
            brier_skill_score=-0.08 + index * 0.01,
            locked_accuracy=0.48 + index * 0.01,
            locked_log_loss=0.76 - index * 0.01,
            risk_adjusted_return=-0.20 + index * 0.05,
            max_drawdown=-0.22 + index * 0.01,
            stability_score=0.35 + index * 0.05,
            health_status="HEALTHY",
        )
        for index in range(1, 7)
    ]

    ranked = rank_relative_candidates(evidence, limit=5)

    assert len(ranked) == 5
    assert [item.relative_rank for item in ranked] == [1, 2, 3, 4, 5]
    assert ranked[0].code == "000006"
    assert all(not item.qualified for item in ranked)
    assert all(item.research_tier in {"CONDITIONAL", "WATCH"} for item in ranked)


def test_relative_list_prioritizes_verified_next_session_edge_without_changing_trade_gate():
    def evidence(code, status, expected):
        return DeepCandidateEvidence(code=code, qualified=False, conclusion="NO_CLEAR_ADVANTAGE",
            calibrated_probability=.6, probability_lower_bound=.4, brier_skill_score=-.1,
            locked_accuracy=.5, locked_log_loss=.7, risk_adjusted_return=0,
            max_drawdown=-.1, stability_score=.5, health_status="DEGRADED",
            forecast_report={"nextSession": {"status": status, "upProbability": .65,
                "expectedReturn": expected, "lowerReturn": -.02, "upperReturn": .03}})
    ranked = rank_relative_candidates([evidence("000001", "WATCH", .04),
                                       evidence("000002", "READY", .01)])
    assert ranked[0].code == "000002"
    assert ranked[0].next_session_score is not None
    assert not ranked[0].qualified
