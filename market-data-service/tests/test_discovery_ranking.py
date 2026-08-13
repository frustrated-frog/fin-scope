from __future__ import annotations

from finscope_market_data.discovery.ranking import (
    rank_deep_candidates,
    rank_lightweight_candidates,
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
