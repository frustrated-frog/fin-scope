import numpy as np
import pytest

from finscope_market_data.forecast.direction_calibration import fit_direction_calibration
from finscope_market_data.forecast.direction_evaluation import probability_diagnostics


def test_identity_regularization_preserves_already_calibrated_weak_direction():
    p = np.repeat([.47, .51], 5000)
    y = np.r_[np.ones(2350), np.zeros(2650), np.ones(2550), np.zeros(2450)]
    result = fit_direction_calibration(p, y, np.ones(len(p)), 'PLATT_0.01')
    assert result.calibrate(.47) == pytest.approx(.47, abs=1e-7)
    assert result.calibrate(.51) == pytest.approx(.51, abs=1e-7)
    assert result.calibrated_log_loss <= result.raw_log_loss + 1e-12


def test_date_weights_are_invariant_to_duplicating_stocks_within_date():
    p = np.array([.2, .7] * 20)
    y = np.array([0, 1, 1, 0] * 10)
    original = fit_direction_calibration(p, y, np.ones(40), 'PLATT_0.01')
    indices = np.r_[np.arange(20), np.tile(np.arange(20, 40), 4)]
    weights = np.r_[np.ones(20), np.full(80, .25)]
    duplicated = fit_direction_calibration(p[indices], y[indices], weights, 'PLATT_0.01')
    assert duplicated.slope == pytest.approx(original.slope, abs=1e-8)
    assert duplicated.intercept == pytest.approx(original.intercept, abs=1e-8)


def test_modes_remain_monotonic_and_intercept_does_not_change_slope():
    p = [.1, .9] * 30
    y = [1, 0] * 30
    for mode in ('RAW', 'INTERCEPT', 'PLATT_0.001', 'PLATT_0.01', 'PLATT_0.1'):
        result = fit_direction_calibration(p, y, np.ones(60), mode)
        assert result.slope >= 0
        assert result.calibrate(.1) <= result.calibrate(.9)
        if mode in ('RAW', 'INTERCEPT'):
            assert result.slope == 1


def test_calibration_rejects_invalid_weights_and_falls_back_on_one_class():
    with pytest.raises(ValueError):
        fit_direction_calibration([.4] * 20, [0] * 20, [-1] * 20, 'RAW')
    with pytest.raises(ValueError):
        fit_direction_calibration([float('nan')] * 20, [0] * 20, [1] * 20, 'RAW')
    result = fit_direction_calibration([.4] * 20, [0] * 20, [1] * 20, 'PLATT_0.01')
    assert result.status == 'NOT_FITTED'
    assert result.calibrate(.4) == .4


def test_probability_audit_separates_market_timing_from_cross_section():
    p = [.1, .1, .9, .9, .8, .8]
    y = [0, 0, 1, 1, 0, 1]
    result = probability_diagnostics(p, y, ['a', 'a', 'b', 'b', 'c', 'c'])
    assert result['auc'] > .5
    assert result['crossSectionAuc'] == .5
    assert result['crossSectionAucDayCount'] == 1
    assert result['predictedUpRate'] == pytest.approx(2 / 3)
    assert result['probabilityQuantiles']['p50'] == .8


def test_single_class_auc_is_missing_instead_of_fabricated_half():
    result = probability_diagnostics([.4, .6], [1, 1], ['a', 'b'])
    assert result['auc'] is None
    assert result['crossSectionAuc'] is None
