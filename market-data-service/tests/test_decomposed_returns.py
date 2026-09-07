from dataclasses import replace
import numpy as np
from finscope_market_data.forecast.decomposed_returns import residual_targets, ReturnModel
from test_joint_training import dataset


def test_relative_target_removes_market_beta_and_normalizes_by_past_volatility():
    rows = dataset(days=2).rows
    def row(r):
        x = [0.] * 20
        x[5], x[17] = .02, 1.5
        return replace(r, market_return=.01, sample=replace(r.sample, features=tuple(x), net_return=.025))
    enriched = tuple(row(r) for r in rows)
    np.testing.assert_allclose(residual_targets(enriched), .5)


def test_reconstruction_adds_market_component_back_to_absolute_return():
    class Constant:
        def __init__(self, value): self.value = value
        def predict(self, x): return np.full(len(x), self.value)
    x = np.zeros((2, 20)); x[:, 5] = .02; x[:, 17] = 1.5
    model = ReturnModel('MARKET_RESIDUAL', Constant(.5), Constant(.01))
    np.testing.assert_allclose(model.predict(x), .025)
