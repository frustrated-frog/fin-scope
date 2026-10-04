from copy import deepcopy
from datetime import date

import numpy as np
import pytest

from finscope_market_data.forecast.trading_calendar import next_session
from finscope_market_data.overnight.learning import evaluate, fit_at, forecast_target, MIN_SAMPLES


def samples(count=130):
    rng = np.random.default_rng(71)
    day = date(2026, 1, 5)
    rows = []
    for _ in range(count):
        following = next_session(day)
        features = rng.normal(size=8)
        rows.append({'signalDate': day.isoformat(), 'exitAt': f'{following}T15:00:00',
                     'features': features.tolist(), 'actualNetReturn': float(.006 * features[0] + rng.normal(0, .01))})
        day = following
    return rows


def test_training_calibration_and_prediction_are_separated_by_label_maturity():
    rows = samples()
    cutoff = f"{rows[-10]['signalDate']}T14:30:00"
    fitted = fit_at(rows, cutoff)
    assert fitted.audit['trainingThrough'] < fitted.audit['calibrationStart']
    assert fitted.audit['calibrationThrough'] < cutoff
    assert fitted.audit['calibrationCount'] == 20
    mutated = deepcopy(rows)
    for row in mutated:
        if row['exitAt'] >= cutoff:
            row['actualNetReturn'] = 10
            row['features'] = [999] * 8
    repeated = fit_at(mutated, cutoff)
    assert fitted.audit == repeated.audit
    assert fitted.predict([.1] * 8) == pytest.approx(repeated.predict([.1] * 8))


def test_calibration_labels_do_not_train_the_base_model():
    rows = samples()
    cutoff = '2026-09-30T14:30:00'
    fitted = fit_at(rows, cutoff)
    changed = deepcopy(rows)
    for row in changed:
        if row['signalDate'] >= fitted.audit['calibrationStart'][:10]:
            row['actualNetReturn'] *= -4
    repeated = fit_at(changed, cutoff)
    before, after = fitted.predict([.2] * 8), repeated.predict([.2] * 8)
    assert before[1:3] == pytest.approx(after[1:3])  # Raw model and expected return unchanged.
    assert before[3:] != pytest.approx(after[3:])  # Independent residual calibration changes.


def test_rolling_audit_freezes_baselines_and_intervals_before_each_test():
    rows = samples()
    result = forecast_target(rows, [.1] * 8, '2026-09-30T14:30:00')
    assert result['status'] == 'WATCH'
    assert result['validationCount'] == 60
    assert result['reliability']['count'] == 60
    assert 0 <= result['upProbability'] <= 1
    assert result['modelLowerNetReturn'] <= result['modelExpectedNetReturn'] <= result['modelUpperNetReturn']
    if result['probabilitySource'] == 'HISTORICAL_BASELINE':
        assert result['upProbability'] == result['baselineProbability']
        assert result['expectedNetReturn'] == result['baselineExpectedNetReturn']
        assert result['lowerNetReturn'] is None and result['upperNetReturn'] is None
    else:
        assert result['lowerNetReturn'] <= result['expectedNetReturn'] <= result['upperNetReturn']
    for check in result['validation']:
        assert check['trainingThrough'] < check['calibrationThrough'] < f"{check['signalDate']}T14:30:00"
        assert 0 <= check['prior'] <= 1
    assert result['reliability']['intervalCoverage'] == pytest.approx(np.mean([
        s['lower'] <= s['actual'] <= s['upper'] for s in result['validation']]))


def test_insufficient_or_single_class_history_never_creates_fake_validation():
    assert forecast_target(samples(60), [0] * 8, '2026-09-30T14:30:00') == {
        'status': 'INSUFFICIENT_DATA', 'sampleCount': 60, 'minimumSamples': MIN_SAMPLES, 'missingSamples': 22}
    rows = samples()
    for row in rows:
        row['actualNetReturn'] = .01
    result = forecast_target(rows, [0] * 8, '2026-09-30T14:30:00')
    assert result['calibrationStatus'] == 'NOT_FITTED'
    assert result['reliability']['brierSkill'] is None
    assert result['reliability']['status'] == 'BASELINE_NOT_BEATEN'


def checks(probabilities):
    return [{'signalDate': f'2026-06-{i + 1:02}', 'probability': p, 'rawProbability': p,
             'prior': .5, 'actual': .01, 'expected': .005, 'lower': -.03, 'upper': .03}
            for i, p in enumerate(probabilities)]


def test_reliability_distinguishes_baseline_failure_recent_drift_and_calibration_failure():
    assert evaluate(checks([.2] * 20), 'FITTED')['status'] == 'BASELINE_NOT_BEATEN'
    assert evaluate(checks([.9] * 20 + [.2] * 10), 'FITTED')['status'] == 'RECENT_DEGRADATION'
    assert evaluate(checks([.8] * 20), 'NOT_FITTED')['status'] == 'CALIBRATION_UNAVAILABLE'
    assert evaluate(checks([.8] * 20), 'FITTED')['status'] == 'HISTORICAL_EDGE'
    assert evaluate(checks([.8] * 10), 'FITTED')['status'] == 'INSUFFICIENT_VALIDATION'
    assert evaluate([], 'FITTED')['status'] == 'INSUFFICIENT_VALIDATION'


def test_narrow_intervals_are_flagged_using_held_out_outcomes():
    rows = checks([.8] * 20)
    for row in rows:
        row['upper'] = .001
    result = evaluate(rows, 'FITTED')
    assert result['intervalCoverage'] == 0
    assert result['status'] == 'INTERVAL_UNRELIABLE'


@pytest.mark.parametrize('status', ['BASELINE_NOT_BEATEN', 'RECENT_DEGRADATION', 'CALIBRATION_UNAVAILABLE',
                                   'INTERVAL_UNRELIABLE', 'INSUFFICIENT_VALIDATION', 'HISTORICAL_EDGE'])
def test_unproven_model_cannot_publish_its_probability_or_interval_as_the_main_reference(status):
    from types import SimpleNamespace
    from finscope_market_data.overnight.learning import select_reference
    fit = SimpleNamespace(baseline=.42, baseline_return=-.001)
    result = select_reference(.85, .05, -.01, .11, fit, {'status': status})
    assert result['modelUpProbability'] == .85
    if status == 'HISTORICAL_EDGE':
        assert result['probabilitySource'] == 'CALIBRATED_MODEL'
        assert result['upProbability'] == .85 and result['lowerNetReturn'] == -.01
    else:
        assert result['probabilitySource'] == 'HISTORICAL_BASELINE'
        assert result['upProbability'] == .42 and result['expectedNetReturn'] == -.001
        assert result['lowerNetReturn'] is None and result['upperNetReturn'] is None


def test_reference_selection_ignores_all_unmatured_labels_and_features():
    rows = samples(160)
    cutoff = f"{rows[-20]['signalDate']}T14:30:00"
    first = forecast_target(rows, [.1] * 8, cutoff)
    for row in rows:
        if row['exitAt'] >= cutoff:
            row['features'] = [999] * 8
            row['actualNetReturn'] = 100
    assert forecast_target(rows, [.1] * 8, cutoff) == first
