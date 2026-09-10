import numpy as np

from finscope_market_data.forecast.calibration_gate import select_calibration_outputs


def test_harmful_calibration_cannot_replace_raw():
    dates=np.repeat(np.arange(60).astype(str),4)
    y=np.tile([0,0,1,1],60)
    result=select_calibration_outputs(np.tile([.2,.4,.6,.8],60),np.full(240,.2),y,dates)
    assert result['directionSource']=='RAW'
    assert result['probabilitySource']=='RAW'


def test_consistent_out_of_fold_improvement_is_adopted():
    dates=np.repeat([f'2026-{i:03d}' for i in range(60)],4)
    y=np.tile([0,0,1,1],60)
    result=select_calibration_outputs(np.full(240,.4),np.tile([.2,.3,.7,.8],60),y,dates)
    assert result['directionSource']=='INTERCEPT'
    assert result['probabilitySource']=='INTERCEPT'


def test_direction_and_probability_have_separate_objectives():
    dates=np.repeat([f'2026-{i:03d}' for i in range(60)],4)
    y=np.tile([0,0,1,1],60)
    result=select_calibration_outputs(np.tile([.4,.4,.6,.6],60),np.tile([.2,.2,.8,.8],60),y,dates)
    assert result['directionSource']=='RAW'
    assert result['probabilitySource']=='INTERCEPT'


def test_short_history_defaults_to_raw():
    result=select_calibration_outputs([.4,.6],[.2,.8],[0,1],['2026-01-01']*2)
    assert result['reason']=='INSUFFICIENT_MATURE_DAYS'
    assert result['directionSource']==result['probabilitySource']=='RAW'


def test_future_labels_cannot_change_prior_gate_decisions():
    from finscope_market_data.forecast.calibration_gate import replay_calibration_gate
    dates = np.repeat([f'2026-{i:03d}' for i in range(90)], 4)
    exits = np.repeat([f'2026-{i+1:03d}' for i in range(90)], 4)
    labels = np.tile([0, 0, 1, 1], 90)
    raw, calibrated = np.full(360, .4), np.tile([.2, .3, .7, .8], 90)
    batch_ids = np.repeat(np.arange(18), 20)
    a = replay_calibration_gate(raw, calibrated, labels, dates, exits, batch_ids)
    changed = labels.copy()
    changed[dates >= '2026-075'] = 1 - changed[dates >= '2026-075']
    b = replay_calibration_gate(raw, calibrated, changed, dates, exits, batch_ids)
    np.testing.assert_array_equal(a['direction'][dates <= '2026-075'], b['direction'][dates <= '2026-075'])
    assert all(d['maturityThrough'] is None or d['maturityThrough'] < d['startDate'] for d in a['decisions'])
    assert a['decisions'][12]['directionSource'] == 'RAW'
    assert a['decisions'][13]['directionSource'] == 'INTERCEPT'
