from dataclasses import replace

import numpy as np

from test_joint_training import dataset
from finscope_market_data.forecast.joint_dataset import ObservableRow
from finscope_market_data.forecast.observable_direction import rolling_panel_direction
from finscope_market_data.forecast.joint_training import PARAMETERS


def fixture():
    data = dataset(days=100, stocks=4)
    observable = tuple(ObservableRow(r.code, r.sample.signal_date, r.sample.features) for r in data.rows)
    return replace(data, observable_rows=observable)


def test_removing_future_label_does_not_remove_prediction_or_change_that_batch():
    data = fixture()
    cutoff = data.observable_rows[80*4].signal_date
    start = data.observable_rows[70*4].signal_date
    x = np.array([r.features for r in data.observable_rows])
    options = dict(parameters={**PARAMETERS, 'n_estimators': 5})
    first = rolling_panel_direction(data, x, start, **options)
    other = replace(data, rows=tuple(r for r in data.rows if not (r.code == '0' and r.sample.signal_date == cutoff)))
    second = rolling_panel_direction(other, x, start, **options)
    assert first['keys'] == second['keys']
    i = second['keys'].index(('0', cutoff))
    assert second['labels'][i] == -1
    prior = first['dates'] <= cutoff
    for mode in first['probabilities']:
        np.testing.assert_array_equal(first['probabilities'][mode][prior], second['probabilities'][mode][prior])
    assert np.isfinite(second['probabilities']['INTERCEPT'][i])
    assert all(b['trainingThrough'] < b['startDate'] for b in first['batches'])


def test_future_target_changes_do_not_change_earlier_raw_or_calibrated_forecasts():
    data = fixture()
    cutoff = data.observable_rows[90*4].signal_date
    start = data.observable_rows[70*4].signal_date
    x = np.array([r.features for r in data.observable_rows])
    altered = replace(data, rows=tuple(replace(r, sample=replace(r.sample, net_return=-r.sample.net_return))
        if r.sample.signal_date >= cutoff else r for r in data.rows))
    options = dict(parameters={**PARAMETERS, 'n_estimators': 5})
    a = rolling_panel_direction(data,x,start,**options)
    b = rolling_panel_direction(altered,x,start,**options)
    for mode in a['probabilities']:
        np.testing.assert_array_equal(a['probabilities'][mode][a['dates']<=cutoff], b['probabilities'][mode][b['dates']<=cutoff])


def test_appending_future_panel_and_labels_preserves_existing_forecasts():
    data = fixture()
    later = dataset(days=105,stocks=4)
    extended = replace(later,observable_rows=tuple(ObservableRow(r.code,r.sample.signal_date,r.sample.features) for r in later.rows))
    start = data.observable_rows[70*4].signal_date
    options = dict(parameters={**PARAMETERS,'n_estimators':5})
    a = rolling_panel_direction(data,np.array([r.features for r in data.observable_rows]),start,**options)
    b = rolling_panel_direction(extended,np.array([r.features for r in extended.observable_rows]),start,**options)
    for mode in a['probabilities']:
        np.testing.assert_array_equal(a['probabilities'][mode],b['probabilities'][mode][:len(a['keys'])])
