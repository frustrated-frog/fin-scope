from dataclasses import replace

import pytest
from threadpoolctl import threadpool_limits

from test_next_session import bars
from finscope_market_data.forecast.local_prediction import fit_local
from finscope_market_data.forecast.multi_horizon import horizon_samples, run_multi_horizon


def test_horizons_use_reference_calendar_not_next_available_stock_bar():
    data = bars(400)
    calendar = [row.trade_date for row in data]
    one = horizon_samples(data, calendar, 1)
    five = horizon_samples(data, calendar, 5)
    assert one[0].exit_date == calendar[61]
    assert five[0].exit_date == calendar[65]
    assert five[0].net_return == pytest.approx(data[65].close/data[60].close-1)
    missing = horizon_samples([row for row in data if row.trade_date != calendar[65]], calendar, 5)
    assert calendar[60] not in {row.signal_date for row in missing}


def test_fit_purges_all_boundaries_and_ignores_future_labels():
    data = bars(500)
    samples = horizon_samples(data, [row.trade_date for row in data], 5)
    cutoff = samples[-30].signal_date
    with threadpool_limits(limits=1):
        fit = fit_local(samples, cutoff)
        changed = fit_local([replace(row, net_return=2) if row.exit_date >= cutoff else row for row in samples], cutoff)
    audit = fit.audit
    assert fit.training_through < audit['selectionStart']
    assert audit['selectionThrough'] < fit.calibration_start
    assert audit['calibrationFitThrough'] < audit['calibrationCheckStart']
    assert audit['calibrationCheckThrough'] < cutoff
    assert fit.predict(samples[-30].features) == pytest.approx(changed.predict(samples[-30].features))
    assert set(audit['candidates']) == {'LOGISTIC', 'HISTOGRAM_GB', 'LIGHTGBM'}


def test_multi_horizon_uses_common_test_dates_and_keeps_all_predictions():
    data = bars(500)
    with threadpool_limits(limits=1):
        result = run_multi_horizon(data, [row.trade_date for row in data], instrument_code='000001.SZ')
    one, five = result['horizons']['1'], result['horizons']['5']
    assert len(one['observations']) == len(five['observations']) == 60
    assert [r['signalDate'] for r in one['observations']] == [r['signalDate'] for r in five['observations']]
    assert len(one['refits']) == len(five['refits']) == 3
    assert set(one['evaluation']['comparisons']) == {'PRIOR', 'MOMENTUM', 'LEGACY'}
    assert one['current']['targetDate'] == '2026-09-07'
    assert five['current']['targetDate'] == '2026-09-11'
    assert one['current']['lowerReturn'] <= one['current']['expectedReturn'] <= one['current']['upperReturn']


def test_frozen_outcomes_do_not_retrain_or_use_future_prices():
    import hashlib
    import json
    from finscope_market_data.forecast.prediction_outcomes import settle_predictions
    data = bars(100)
    original = dict(code='000001.SZ', bars=[bar.model_dump(mode='json') for bar in data[:-5]],
                    calendar=[bar.trade_date for bar in data])
    digest = hashlib.sha256(json.dumps({key: original[key] for key in ('bars', 'calendar')}, sort_keys=True).encode()).hexdigest()
    signal, target = data[-6].trade_date, data[-1].trade_date
    report = dict(modelVersion='test', instrumentCode='000001.SZ', inputFingerprint=digest,
                  horizons={'5': {'current': dict(asOf=signal, targetDate=target, upProbability=.7,
                  modelCode='FROZEN', expectedReturn=.01, lowerReturn=-.1, upperReturn=.1)}})
    frozen = json.dumps(report)
    pending = settle_predictions(report, original, data, signal)
    assert pending['outcomes'][0]['status'] == 'PENDING'
    settled = settle_predictions(report, original, data, target)
    assert settled['outcomes'][0]['actualReturn'] == pytest.approx(data[-1].close/data[-6].close-1)
    assert json.dumps(report) == frozen
    assert settled['outcomes'][0]['probability'] == .7
    original['bars'][0]['close'] += 1
    with pytest.raises(ValueError, match='指纹'):
        settle_predictions(report, original, data, target)
