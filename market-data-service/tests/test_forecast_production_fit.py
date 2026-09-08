from dataclasses import replace
import pytest
from test_forecast_service import samples
from finscope_market_data.forecast.production_fit import fit_production_model


def test_recent_fit_purges_calibration_boundary_and_ignores_future_labels():
    history = samples(1600)
    cutoff = history[1400].exit_date
    fit = fit_production_model(history, cutoff=cutoff, horizon_days=20, model_code='LOGISTIC')
    assert fit.training_through < fit.calibration_start
    assert fit.calibration_through <= cutoff
    assert fit.training_count <= 504
    assert 15 <= fit.calibration_count <= 60
    assert fit.training_through > history[840].exit_date
    altered = [replace(x, net_return=-x.net_return * 100) if x.exit_date > cutoff else x for x in history]
    other = fit_production_model(altered, cutoff=cutoff, horizon_days=20, model_code='LOGISTIC')
    assert fit.predict(history[1400].features) == other.predict(history[1400].features)


def test_recent_fit_requires_enough_independent_labels():
    with pytest.raises(ValueError, match='独立'):
        fit_production_model(samples(100), cutoff='2030-01-01', horizon_days=20, model_code='LOGISTIC')


@pytest.mark.parametrize('baseline_probability, passed', [(.99, False), (.5, True)])
def test_recent_candidate_gate_uses_an_untouched_comparison_window(monkeypatch, baseline_probability, passed):
    from types import SimpleNamespace
    from finscope_market_data.forecast.production_fit import evaluate_recent_candidate
    history = [replace(row, net_return=.02) for row in samples(800)]
    cutoffs = []
    def fit(*args, **kwargs):
        cutoffs.append(kwargs['cutoff'])
        return SimpleNamespace(predict=lambda features: (.9, .9), calibration_through=kwargs['cutoff'])
    monkeypatch.setattr('finscope_market_data.forecast.production_fit.fit_production_model', fit)
    baseline = SimpleNamespace(model=SimpleNamespace(predict=lambda features: baseline_probability),
        calibration=SimpleNamespace(calibrate=lambda value: value),
        split_audit=SimpleNamespace(locked_test=SimpleNamespace(start_date=history[600].signal_date)))
    gate = evaluate_recent_candidate(history, cutoff=history[-1].exit_date, horizon_days=5,
                                     model_code='LOGISTIC', baseline=baseline)
    assert gate['passed'] is passed
    assert gate['sampleCount'] == 30
    assert cutoffs[0] < gate['startDate']
    assert gate['candidateCalibrationThrough'] < gate['startDate']
