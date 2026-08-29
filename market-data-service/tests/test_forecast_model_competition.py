from __future__ import annotations

from datetime import date, timedelta
import math

import pytest

from finscope_market_data.forecast.features import ForecastSample
from finscope_market_data.forecast.model_competition import (
    ConstrainedStackingModel,
    RegimeAwareLogisticModel,
    run_model_competition,
)


def samples(count: int) -> list[ForecastSample]:
    first = date(2010, 1, 1)
    result: list[ForecastSample] = []
    for index in range(count):
        signal = math.sin(index / 9.0)
        nonlinear = signal * signal
        positive = nonlinear > 0.45
        result.append(ForecastSample(
            signal_date=(first + timedelta(days=index)).isoformat(),
            entry_date=(first + timedelta(days=index + 1)).isoformat(),
            exit_date=(first + timedelta(days=index + 6)).isoformat(),
            features=(signal, nonlinear, math.cos(index / 13.0)),
            net_return=0.02 if positive else -0.02,
        ))
    return result


def test_model_competition_selects_only_from_development_validation() -> None:
    history = samples(600)

    result = run_model_competition(history, independent_stride_days=5)

    assert len(result.candidates) == 6
    assert {item.code for item in result.candidates} == {
        "LOGISTIC",
        "BOOSTED_STUMPS",
        "HISTOGRAM_GB",
        "REGIME_LOGISTIC",
        "STACKED",
        "RULE_BASELINE",
    }
    assert sum(item.selected for item in result.candidates) == 1
    assert result.selected_model in {item.code for item in result.candidates}
    assert all(item.selection_sample_count > 0 for item in result.candidates)
    assert all(item.validation_fold_count >= 3 for item in result.candidates)
    assert all(item.brier_std >= 0 for item in result.candidates)
    assert result.selection_end_date < result.calibration_start_date
    assert result.calibration_start_date == history[int(len(history) * 0.60)].signal_date
    selected_validation = history[
        max(80, int(int(len(history) * 0.60) * 0.75)) : int(len(history) * 0.60) : 5
    ]
    expected_last_mature = max(
        item.signal_date
        for item in selected_validation
        if item.exit_date < result.calibration_start_date
    )
    assert result.selection_end_date == expected_last_mature
    assert "锁定测试" in result.selection_rule


def test_model_competition_is_deterministic_and_bounded() -> None:
    first = run_model_competition(samples(600), independent_stride_days=5)
    second = run_model_competition(samples(600), independent_stride_days=5)

    assert first == second
    assert all(0 <= item.brier_score <= 1 for item in first.candidates)
    assert all(item.log_loss >= 0 for item in first.candidates)


def test_regime_model_falls_back_to_global_probability_for_small_regimes() -> None:
    history = samples(180)

    model = RegimeAwareLogisticModel.fit(history, minimum_regime_samples=500)

    assert model.regime_models == ()
    assert model.predict(history[-1].features) == model.global_model.predict(
        history[-1].features
    )


def test_each_competition_fold_purges_labels_that_exit_after_validation_starts() -> None:
    result = run_model_competition(samples(600), independent_stride_days=5)

    assert len(result.fold_audits) >= 3
    assert all(
        audit.training_last_exit_date < audit.validation_start_date
        for audit in result.fold_audits
    )
    assert all(audit.validation_sample_count > 0 for audit in result.fold_audits)


def test_constrained_stacking_weights_are_non_negative_bounded_and_normalized() -> None:
    model = ConstrainedStackingModel.fit(samples(360))

    weights = [weight for _, _, weight in model.weighted_models]

    assert sum(weights) == pytest.approx(1.0)
    assert all(0 <= weight <= 0.60 for weight in weights)
    assert 0.01 <= model.predict(samples(1)[0].features) <= 0.99
