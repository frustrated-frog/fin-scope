from dataclasses import replace

import numpy as np

from test_joint_training import dataset
from finscope_market_data.forecast.adaptive_classifiers import fit_candidates
from finscope_market_data.forecast.joint_training import PARAMETERS
from finscope_market_data.forecast.rolling_direction import rolling_forecasts, select_direction_method


def test_fit_only_requested_candidate_without_changing_predictions():
    data = dataset(days=65)
    selected = fit_candidates(data.rows, data.feature_codes, PARAMETERS, codes=('BASE_TREE',))
    all_models = fit_candidates(data.rows, data.feature_codes, PARAMETERS)
    assert set(selected) == {'BASE_TREE'}
    x = np.array([r.sample.features for r in data.rows[:10]])
    assert np.array_equal(selected['BASE_TREE'].predict_proba(x), all_models['BASE_TREE'].predict_proba(x))


def test_rolling_predictions_use_only_mature_training_and_oof_calibration():
    data = dataset(days=110, stocks=4)
    start = data.rows[70 * 4].sample.signal_date
    result = rolling_forecasts(data.rows, data.feature_codes, {**PARAMETERS, 'n_estimators': 5}, start,
                               model_codes=('BASE_TREE',))
    assert len(result['batches']) == 8
    for batch in result['batches']:
        assert batch['trainingThrough'] < batch['startDate']
        assert batch['predictionDayCount'] == 5
        assert batch['modelAgeDaysMax'] == 4
        if batch['calibrationThrough'] is not None:
            assert batch['calibrationThrough'] < batch['startDate']
        assert batch['calibrationSource'] == 'PAST_OUT_OF_FOLD_SAME_MODEL_FAMILY'
    assert result['batches'][0]['calibrationDayCount'] == 0
    assert result['batches'][-1]['calibrationDayCount'] > 0


def test_future_labels_cannot_change_prior_forecasts_or_selection():
    data = dataset(days=110, stocks=4)
    start = data.rows[70 * 4].sample.signal_date
    cutoff = data.rows[100 * 4].sample.signal_date
    changed = replace(data, rows=tuple(replace(r, sample=replace(r.sample, net_return=-r.sample.net_return))
                                     if r.sample.signal_date >= cutoff else r for r in data.rows))
    args = (data.feature_codes, {**PARAMETERS, 'n_estimators': 5}, start)
    first = rolling_forecasts(data.rows, *args, model_codes=('BASE_TREE',))
    second = rolling_forecasts(changed.rows, *args, model_codes=('BASE_TREE',))
    mask = np.array([r.sample.signal_date < cutoff for r in first['rows']])
    for key in first['probabilities']:
        assert np.array_equal(first['probabilities'][key][mask], second['probabilities'][key][mask])
    assert select_direction_method(first, start, cutoff) == select_direction_method(second, start, cutoff)
