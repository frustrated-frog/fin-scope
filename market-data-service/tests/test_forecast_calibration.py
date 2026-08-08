from __future__ import annotations

import math

from finscope_market_data.forecast.calibration import PlattCalibrator


def test_platt_calibration_improves_overconfident_probabilities() -> None:
    raw: list[float] = []
    labels: list[bool] = []
    for bucket in range(1, 10):
        true_probability = bucket / 10
        overconfident = 1 / (1 + math.exp(-2.8 * math.log(true_probability / (1 - true_probability))))
        for index in range(20):
            raw.append(overconfident)
            labels.append(index < bucket * 2)

    result = PlattCalibrator.fit(raw, labels)

    assert result.status == "FITTED"
    assert result.calibrated_log_loss < result.raw_log_loss
    assert 0 < result.calibrate(0.2) < result.calibrate(0.8) < 1


def test_platt_calibration_is_deterministic() -> None:
    raw = [0.1, 0.2, 0.35, 0.55, 0.7, 0.85] * 5
    labels = [False, False, False, True, True, True] * 5

    first = PlattCalibrator.fit(raw, labels)
    second = PlattCalibrator.fit(raw, labels)

    assert first == second


def test_platt_calibration_falls_back_for_too_few_or_single_class_samples() -> None:
    too_few = PlattCalibrator.fit([0.4, 0.6] * 5, [False, True] * 5)
    one_class = PlattCalibrator.fit([0.4, 0.6] * 10, [True] * 20)

    assert too_few.status == "NOT_FITTED"
    assert "15" in (too_few.reason or "")
    assert one_class.status == "NOT_FITTED"
    assert "正负" in (one_class.reason or "")
    assert too_few.calibrate(0.63) == 0.63

