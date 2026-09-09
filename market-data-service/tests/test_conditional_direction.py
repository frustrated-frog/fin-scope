import numpy as np
import pytest

from finscope_market_data.forecast.conditional_direction import (
    ResidualDirectionModel, fit_residual_direction, daily_market_data, conditional_features,
)


def test_zero_residual_preserves_base_probability_exactly():
    model = ResidualDirectionModel(np.zeros(2), np.ones(2), np.zeros(3))
    base = np.array([.01, .47, .51, .99])
    assert np.array_equal(model.predict(base, np.ones((4, 2))), base)


def test_residual_learner_finds_conditional_signal_without_changing_base_contract():
    x = np.tile(np.array([[-1.], [1.]]), (100, 1))
    base = np.full(200, .5)
    labels = (x[:, 0] > 0).astype(float)
    model = fit_residual_direction(base, x, labels, np.ones(200), .01)
    assert np.mean((model.predict(base, x) >= .5) == labels) == 1
    assert model.predict(np.array([.5]), np.array([[2.]]))[0] > .5
    assert model.mean[0] == 0
    before = model.mean.copy()
    model.predict(np.array([.5]), np.array([[1000.]]))
    assert np.array_equal(before, model.mean)


def test_market_target_has_one_observation_per_date_not_per_stock():
    x = np.array([[.1, .2], [.1, .2], [.3, .4]])
    days, features, targets = daily_market_data(x, [0, 1, 1], ['a', 'a', 'b'])
    assert list(days) == ['a', 'b']
    assert features.shape == (2, 2)
    assert np.array_equal(targets, [.5, 1.])


def test_rank_feature_is_invariant_to_daily_affine_score_scale():
    p = np.array([.4, .5, .6, .3, .7])
    q = np.array([.45] * 3 + [.55] * 2)
    ranks = np.array([1., 2., 3., -1., 1.])
    dates = ['a'] * 3 + ['b'] * 2
    first = conditional_features(p, q, ranks, dates, 'JOINT')
    second = conditional_features(p, q, ranks * 100 + 900, dates, 'JOINT')
    assert np.allclose(first, second)
    assert first.shape[1] == 5


def test_residual_rejects_nonfinite_or_misaligned_inputs():
    with pytest.raises(ValueError):
        fit_residual_direction([.5], [[float('nan')]], [1], [1], .1)
