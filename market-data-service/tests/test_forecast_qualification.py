from __future__ import annotations

from dataclasses import replace
from datetime import date, timedelta

import pytest

from finscope_market_data.forecast.features import ForecastSample
from finscope_market_data.forecast.qualification import (
    assess_qualification_status,
    evaluate_probability_metrics,
    mature_training_samples,
    optimize_selective_thresholds,
    qualify_model,
    reliability_bins,
    selective_metrics,
    split_qualification_samples,
)


def samples(count: int) -> list[ForecastSample]:
    first = date(2010, 1, 1)
    return [
        ForecastSample(
            signal_date=(first + timedelta(days=index)).isoformat(),
            entry_date=(first + timedelta(days=index + 1)).isoformat(),
            exit_date=(first + timedelta(days=index + 20)).isoformat(),
            features=(float(index),),
            net_return=0.01 if index % 2 else -0.01,
        )
        for index in range(count)
    ]


def test_qualification_split_is_chronological_and_disjoint() -> None:
    split = split_qualification_samples(samples(100))

    assert len(split.development) == 60
    assert len(split.calibration) == 20
    assert len(split.locked_test) == 20
    assert split.development[-1].signal_date < split.calibration[0].signal_date
    assert split.calibration[-1].signal_date < split.locked_test[0].signal_date
    assert set(split.development).isdisjoint(split.calibration)
    assert set(split.calibration).isdisjoint(split.locked_test)
    assert split.audit.development.start_date == split.development[0].signal_date
    assert split.audit.locked_test.end_date == split.locked_test[-1].signal_date


def test_training_samples_only_include_labels_matured_before_prediction() -> None:
    ordered = samples(100)
    prediction_date = ordered[75].signal_date

    matured = mature_training_samples(ordered[:60], prediction_date)

    assert matured
    assert all(item.exit_date < prediction_date for item in matured)
    assert ordered[59] not in matured


@pytest.mark.parametrize("model_code", ["LOGISTIC", "BOOSTED_STUMPS", "RULE_BASELINE"])
def test_qualification_exposes_the_exact_model_its_calibrator_was_fitted_for(
    model_code: str,
) -> None:
    history = samples(600)

    result = qualify_model(history, independent_stride_days=5, model_code=model_code)
    split = split_qualification_samples(history, independent_stride_days=5)
    anchors = mature_training_samples(split.calibration, split.locked_test[0].signal_date)[::5]

    assert tuple(result.model.predict(item.features) for item in anchors) == pytest.approx(
        result.calibration_raw_probabilities
    )


def test_split_rejects_duplicate_signal_dates() -> None:
    ordered = samples(20)

    try:
        split_qualification_samples([*ordered, ordered[-1]])
    except ValueError as error:
        assert "日期" in str(error)
    else:
        raise AssertionError("duplicate signal dates must be rejected")


def test_probability_metrics_match_hand_calculated_values() -> None:
    probabilities = [0.1, 0.4, 0.6, 0.9]
    labels = [False, True, False, True]

    metrics = evaluate_probability_metrics(probabilities, labels, 0.5)

    assert metrics.sample_count == 4
    assert metrics.accuracy == 0.5
    assert abs(metrics.brier_score - 0.185) < 1e-12
    assert abs(metrics.baseline_brier_score - 0.25) < 1e-12
    assert abs(metrics.brier_skill_score - 0.26) < 1e-12
    assert metrics.log_loss > 0
    assert metrics.expected_calibration_error >= 0


def test_reliability_bins_keep_empty_fixed_ranges_explicit() -> None:
    bins = reliability_bins([0.1, 0.65, 0.9], [False, True, True])

    assert len(bins) == 5
    assert sum(item.count for item in bins) == 3
    assert bins[1].count == 0
    assert bins[1].mean_probability is None
    assert bins[1].observed_up_rate is None
    assert bins[-1].upper_bound == 1.0


def test_qualification_fails_when_locked_probability_quality_is_worse() -> None:
    labels = [False, True] * 8
    raw = evaluate_probability_metrics([0.45, 0.55] * 8, labels, 0.5)
    degraded = evaluate_probability_metrics([0.9, 0.1] * 8, labels, 0.5)

    status = assess_qualification_status(
        enough_samples=True,
        calibration_status="FITTED",
        raw_metrics=raw,
        calibrated_metrics=degraded,
    )

    assert status == "FAILED"


def test_selective_metrics_report_accuracy_and_coverage_together() -> None:
    metrics = selective_metrics(
        [0.2, 0.45, 0.62, 0.9],
        [False, True, True, True],
        lower_threshold=0.4,
        upper_threshold=0.6,
    )

    assert metrics.covered_count == 3
    assert metrics.coverage == 0.75
    assert metrics.covered_accuracy == 1.0
    assert metrics.abstain_rate == 0.25


def test_independent_audit_uses_requested_horizon_stride() -> None:
    split = split_qualification_samples(samples(100), independent_stride_days=5)

    assert split.audit.development.independent_sample_count == 12
    assert split.audit.calibration.independent_sample_count == 4
    assert split.audit.locked_test.independent_sample_count == 4


def test_selective_thresholds_are_learned_from_calibration_quality_and_coverage() -> None:
    probabilities = [0.08, 0.18, 0.32, 0.46, 0.54, 0.68, 0.82, 0.92]
    labels = [False, False, False, True, False, True, True, True]

    policy = optimize_selective_thresholds(
        probabilities,
        labels,
        minimum_coverage=0.30,
    )
    metrics = selective_metrics(
        probabilities,
        labels,
        lower_threshold=policy.lower_threshold,
        upper_threshold=policy.upper_threshold,
    )

    assert 0 < policy.lower_threshold < 0.5 < policy.upper_threshold < 1
    assert metrics.coverage >= 0.30
    assert metrics.covered_accuracy >= 0.80


@pytest.mark.parametrize("horizon_days", [1, 5, 20])
@pytest.mark.parametrize("boundary_only", [False, True])
def test_calibration_ignores_labels_not_mature_before_locked_start(
    horizon_days: int, boundary_only: bool,
) -> None:
    history = [
        replace(
            item,
            exit_date=(date.fromisoformat(item.signal_date)
                       + timedelta(days=horizon_days)).isoformat(),
            net_return=0.01 if (index // horizon_days) % 2 else -0.01,
        )
        for index, item in enumerate(samples(2000))
    ]
    split = split_qualification_samples(history, independent_stride_days=horizon_days)
    cutoff = split.locked_test[0].signal_date
    changed = [
        replace(item, net_return=-item.net_return)
        if (item.exit_date == cutoff if boundary_only else item.exit_date >= cutoff)
        else item
        for item in history
    ]
    original = qualify_model(history, independent_stride_days=horizon_days)
    flipped = qualify_model(changed, independent_stride_days=horizon_days)

    assert original.calibration.status == "FITTED"
    assert original.calibration == flipped.calibration
    assert original.locked_test.raw_probabilities == flipped.locked_test.raw_probabilities
    assert original.locked_test.calibrated_probabilities == flipped.locked_test.calibrated_probabilities
    assert original.split_audit.calibration.purged_count == horizon_days
    matured = mature_training_samples(split.calibration, cutoff)
    assert original.calibration.sample_count == len(matured[::horizon_days])
    assert original.split_audit.calibration.independent_sample_count == original.calibration.sample_count
