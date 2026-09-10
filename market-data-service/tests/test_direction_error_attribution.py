import numpy as np
import pytest

from finscope_market_data.forecast.direction_error_attribution import attribute_direction_errors


def test_daily_correction_identity_uses_date_then_stock_weights():
    # Day one: one correction / two stocks. Day two: one break / one stock.
    base = np.array([.4, .6, .6])
    p = np.array([.6, .6, .4])
    y = np.array([1, 1, 1])
    dates = ['2026-01-01', '2026-01-01', '2026-01-02']
    result = attribute_direction_errors(p, base, y, dates)
    assert result['correctedCount'] == 1
    assert result['brokenCount'] == 1
    assert result['unchangedCount'] == 1
    assert result['accuracyDifference'] == pytest.approx(-.25)
    assert result['correctedRate'] == pytest.approx(.25)
    assert result['brokenRate'] == pytest.approx(.5)
    assert result['days'][0]['nonUpToUpCount'] == 1
    assert result['days'][1]['upToNonUpCount'] == 1


def test_brier_decomposition_is_exact_for_heterogeneous_predictions_and_labels():
    p, base, y = [.2, .9, .4, .8, .7], [.6]*5, [0, 1, 1, 0, 1]
    result = attribute_direction_errors(p, base, y, ['a', 'a', 'b', 'b', 'b'])
    for key in ('candidate', 'baseline'):
        values = result['brierDecomposition'][key]
        assert values['brierScore'] == pytest.approx(values['dailyMeanError'] + values['centeredError'])
    assert result['accuracyDifference'] == pytest.approx(result['correctedRate'] - result['brokenRate'])


def test_invalid_or_unaligned_arrays_are_rejected():
    for p in ([.5], [np.nan, .5], [1.1, .5]):
        with pytest.raises(ValueError):
            attribute_direction_errors(p, [.5, .5], [0, 1], ['a', 'a'])
