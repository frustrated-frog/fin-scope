from __future__ import annotations

from finscope_market_data.holding_policy import (
    HoldingStrategyRequest,
    evaluate_holding_strategy,
)


def request(**overrides: object) -> HoldingStrategyRequest:
    values: dict[str, object] = {
        "instrumentCode": "600570.SH",
        "asOfDate": "2026-08-31",
        "horizonDays": 5,
        "marketPrice": 30.0,
        "quantity": 100.0,
        "cash": 7000.0,
        "totalEquity": 10000.0,
        "currentWeight": 0.30,
        "upProbability": 0.68,
        "p10Return": -0.035,
        "p50Return": 0.032,
        "p90Return": 0.09,
        "forecastStatus": "ROBUST",
        "modelHealthStatus": "HEALTHY",
        "quoteAgeDays": 0,
        "roundTripCostRate": 0.0015,
        "maxPositionWeight": 0.65,
        "cashBufferRate": 0.10,
        "minimumNetEdge": 0.005,
        "forecastRunId": 12,
        "modelVersion": "panel-logit-v10",
        "dataFingerprint": "sha256:abc",
    }
    values.update(overrides)
    return HoldingStrategyRequest.model_validate(values)


def test_abstains_when_quote_is_stale() -> None:
    result = evaluate_holding_strategy(request(quoteAgeDays=3))

    assert result.action == "ABSTAIN"
    assert "行情已过期" in result.blockers


def test_reduces_concentration_before_considering_model_add_signal() -> None:
    result = evaluate_holding_strategy(
        request(currentWeight=0.72, maxPositionWeight=0.60, quantity=300)
    )

    assert result.action == "REDUCE_CONCENTRATION"
    assert result.suggested_quantity == 100


def test_allows_one_board_lot_when_net_edge_and_risk_budget_are_sufficient() -> None:
    result = evaluate_holding_strategy(request())

    assert result.action == "ALLOW_ADD"
    assert result.suggested_quantity == 100
    assert result.expected_edge_after_cost == 0.0305


def test_exit_requires_joint_probability_and_downside_deterioration() -> None:
    result = evaluate_holding_strategy(
        request(upProbability=0.39, p10Return=-0.11, p50Return=-0.018)
    )

    assert result.action == "EXIT_TRIGGERED"
    assert result.suggested_quantity == 100


def test_holds_when_signal_is_positive_but_no_board_lot_is_affordable() -> None:
    result = evaluate_holding_strategy(
        request(cash=200, totalEquity=3200, currentWeight=0.94, maxPositionWeight=1.0)
    )

    assert result.action == "HOLD"
    assert "可用现金或集中度额度不足一手" in result.blockers


def test_cost_basis_is_explanatory_and_never_changes_action() -> None:
    low_cost = evaluate_holding_strategy(request(costBasis=10, unrealizedReturn=2.0))
    high_cost = evaluate_holding_strategy(request(costBasis=60, unrealizedReturn=-0.5))

    assert low_cost.action == high_cost.action
    assert low_cost.suggested_quantity == high_cost.suggested_quantity
