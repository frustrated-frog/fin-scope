from __future__ import annotations

import math

import pytest

from finscope_market_data.forecast.selection_bias import audit_selection_bias


def _returns(edge: float, phase: float, count: int = 260) -> list[float]:
    return [
        edge + math.sin(index / 7.0 + phase) * 0.008
        + math.cos(index / 19.0 + phase) * 0.004
        for index in range(count)
    ]


def test_deflated_sharpe_penalizes_more_trials() -> None:
    strategy = _returns(0.0008, 0.0)

    single = audit_selection_bias([strategy], evaluated_trial_count=1)
    searched = audit_selection_bias([strategy], evaluated_trial_count=80)

    assert single.status == "AVAILABLE"
    assert searched.status == "AVAILABLE"
    assert searched.deflated_sharpe_probability < single.deflated_sharpe_probability
    assert searched.expected_maximum_sharpe > single.expected_maximum_sharpe
    assert searched.minimum_track_record_length >= 2


def test_selection_bias_reports_bounded_pbo_for_multiple_trials() -> None:
    trials = [_returns(0.0002 + index * 0.00003, index) for index in range(6)]

    first = audit_selection_bias(trials, evaluated_trial_count=12)
    second = audit_selection_bias(trials, evaluated_trial_count=12)

    assert first == second
    assert 0 <= first.probability_of_backtest_overfitting <= 1
    assert first.combination_count > 0
    assert first.trial_count == 12
    assert first.verdict in {"PASS", "CAUTION", "HIGH_RISK"}


def test_selection_bias_returns_explicit_insufficient_state() -> None:
    result = audit_selection_bias([[0.01, -0.01]], evaluated_trial_count=1)

    assert result.status == "INSUFFICIENT_DATA"
    assert result.reason is not None


def test_selection_bias_rejects_non_finite_returns() -> None:
    with pytest.raises(ValueError, match="有限"):
        audit_selection_bias(
            [[0.01 for _ in range(50)], [float("nan") for _ in range(50)]],
            evaluated_trial_count=2,
        )
