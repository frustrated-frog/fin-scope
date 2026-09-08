import numpy as np
from test_joint_training import dataset
from finscope_market_data.forecast.adaptive_classifiers import date_weights, fit_candidates, select_adaptive_candidate
from finscope_market_data.forecast.joint_training import PARAMETERS, temporal_split


def test_decay_weights_preserve_equal_date_mass_before_recency_adjustment():
    rows = dataset(days=4, stocks=3).rows
    weights = date_weights(rows)
    assert weights[:3].sum() == weights[-3:].sum()
    decay = date_weights(rows, half_life=126)
    assert decay[-3:].sum() > decay[:3].sum()


def test_base_candidate_cannot_see_market_state_columns():
    data = dataset(days=160)
    models = fit_candidates(data.rows, ('SIGNAL', 'MOMENTUM', 'STATE_TEST'), PARAMETERS)
    x = np.asarray([r.sample.features for r in data.rows[:20]])
    before = models['BASE_TREE'].predict_proba(x)
    x[:, 2] = 10000
    assert np.array_equal(before, models['BASE_TREE'].predict_proba(x))
    assert set(models) == {'BASE_TREE','FULL_TREE','RECENT_TREE','DECAY_TREE','POOLED_LOGISTIC','EQUAL_ENSEMBLE'}


def test_selection_evidence_has_multiple_purged_periods():
    data = dataset(days=360)
    parts = temporal_split(data.rows)
    chosen, evidence = select_adaptive_candidate(parts['train'], parts['selection'], data.feature_codes, PARAMETERS)
    assert chosen in evidence['candidates']
    assert evidence['periodCount'] == 4
    for period in evidence['periods']:
        assert period['trainingThrough'] < period['calibrationStart']
        assert period['calibrationThrough'] < period['startDate']
    assert evidence['selectionEnd'] < parts['test'][0].sample.signal_date
