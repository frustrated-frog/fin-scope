from __future__ import annotations

from datetime import date, timedelta

from finscope_market_data.forecast.features import ForecastSample
from finscope_market_data.forecast.qualification import (
    evaluate_probability_metrics,
    mature_training_samples,
    reliability_bins,
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
