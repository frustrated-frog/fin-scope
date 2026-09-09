from dataclasses import replace

import numpy as np

from test_joint_training import dataset
from finscope_market_data.forecast.conditional_experiment import conditional_rollout, select_conditional_method
from finscope_market_data.forecast.joint_training import PARAMETERS


def test_conditional_rollout_purges_all_child_and_meta_training_labels():
    data = dataset(days=140, stocks=4)
    start = data.rows[70*4].sample.signal_date
    output = [r for r in data.rows if r.sample.signal_date >= start]
    base = np.full(len(output), .5)
    result = conditional_rollout(data.rows, data.feature_codes, base, start,
        parameters={**PARAMETERS, 'n_estimators': 5}, market_columns=(0, 1))
    assert len(result['batches']) == 14
    assert len(result['probabilities']) == 9
    for batch in result['batches']:
        assert batch['childTrainingThrough'] < batch['startDate']
        assert batch['marketTrainingDayCount'] < batch['childTrainingSampleCount']
        if batch['metaTrainingThrough']:
            assert batch['metaTrainingThrough'] < batch['startDate']
    assert np.array_equal(result['probabilities']['JOINT_0.01'][:30*4], base[:30*4])


def test_future_targets_cannot_change_prior_conditional_predictions():
    data = dataset(days=140, stocks=4)
    start = data.rows[70*4].sample.signal_date
    cutoff = data.rows[130*4].sample.signal_date
    base = np.full(70*4, .5)
    kwargs = dict(parameters={**PARAMETERS, 'n_estimators': 5}, market_columns=(0, 1))
    result = conditional_rollout(data.rows, data.feature_codes, base, start, **kwargs)
    changed = tuple(replace(r, sample=replace(r.sample, net_return=-r.sample.net_return))
                    if r.sample.signal_date >= cutoff else r for r in data.rows)
    other = conditional_rollout(changed, data.feature_codes, base, start, **kwargs)
    for key in result['probabilities']:
        assert np.array_equal(result['probabilities'][key][:60*4], other['probabilities'][key][:60*4])
    assert np.array_equal(result['marketProbability'][:60*4], other['marketProbability'][:60*4])
    assert select_conditional_method(result, start, cutoff) == select_conditional_method(other, start, cutoff)
