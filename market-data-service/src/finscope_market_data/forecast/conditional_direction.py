"""Small conditional log-odds corrections trained exclusively on historical OOF scores."""
from dataclasses import dataclass

import numpy as np
from scipy.optimize import minimize
from scipy.special import expit, logit
from scipy.stats import rankdata

MARKET_FEATURE_CODES = (
    'MARKET_MOMENTUM_5', 'MARKET_MOMENTUM_20', 'MARKET_VOLATILITY_20',
    'STATE_UP_BREADTH_1', 'STATE_UP_BREADTH_5', 'STATE_UP_BREADTH_20',
    'STATE_EQUAL_RETURN_1', 'STATE_DISPERSION_1', 'STATE_MEAN_VOLATILITY_20',
    'STATE_ACTIVITY_SPREAD_5', 'STATE_TREND_AGREEMENT',
)
CONDITIONAL_MODES = ('INTERCEPT', 'MARKET', 'RANK', 'JOINT')


def daily_market_data(features, labels, dates):
    x, y, dates = np.asarray(features), np.asarray(labels), np.asarray(dates)
    if x.ndim != 2 or len(x) != len(y) or len(x) != len(dates) or not len(x):
        raise ValueError('市场观测需要等长非空输入')
    days, inverse, counts = np.unique(dates, return_inverse=True, return_counts=True)
    means = np.column_stack([np.bincount(inverse, weights=x[:, j]) / counts for j in range(x.shape[1])])
    return days, means, np.bincount(inverse, weights=y) / counts


def conditional_features(base, market, ranking, dates, mode):
    base, market, ranking, dates = (np.asarray(v) for v in (base, market, ranking, dates))
    if (base.ndim != 1 or base.shape != market.shape or base.shape != ranking.shape or base.shape != dates.shape
            or not len(base) or mode not in CONDITIONAL_MODES):
        raise ValueError('条件特征输入或模式无效')
    if not all(np.all(np.isfinite(v)) for v in (base, market, ranking)):
        raise ValueError('条件分数必须有限')
    relative, percentile, mean = np.zeros(len(base)), np.zeros(len(base)), np.zeros(len(base))
    z = logit(np.clip(base, 1e-6, 1-1e-6))
    for day in np.unique(dates):
        mask = dates == day
        percentile[mask] = (rankdata(ranking[mask]) - .5) / mask.sum() - .5
        mean[mask] = z[mask].mean()
        relative[mask] = z[mask] - mean[mask]
    q = logit(np.clip(market, .01, .99))
    values = {'INTERCEPT': [], 'MARKET': [q, mean], 'RANK': [percentile, relative],
              'JOINT': [q, mean, percentile, relative, q * percentile]}[mode]
    return np.column_stack(values) if values else np.empty((len(base), 0))


@dataclass
class ResidualDirectionModel:
    mean: np.ndarray
    scale: np.ndarray
    coefficients: np.ndarray
    status: str = 'FITTED'

    def predict(self, base, features):
        p, x = np.asarray(base, dtype=float), np.asarray(features, dtype=float)
        if (x.shape != (len(p), len(self.mean)) or not np.all(np.isfinite(x))
                or not np.all(np.isfinite(p)) or np.any((p <= 0) | (p >= 1))):
            raise ValueError('残差预测需要有效基础概率和同维度特征')
        correction = ((x - self.mean) / self.scale) @ self.coefficients[:-1] + self.coefficients[-1]
        # Exact identity for the fallback/zero-correction contract.
        return np.where(correction == 0, p, expit(logit(p) + correction))


def fit_residual_direction(base, features, labels, weights, strength):
    p, x, y, w = (np.asarray(v, dtype=float) for v in (base, features, labels, weights))
    if (p.ndim != 1 or x.ndim != 2 or len(x) != len(p) or p.shape != y.shape or p.shape != w.shape
            or not len(p) or not all(np.all(np.isfinite(v)) for v in (p, x, y, w))
            or np.any((p <= 0) | (p >= 1)) or not np.all(np.isin(y, [0, 1]))
            or np.any(w < 0) or w.sum() <= 0 or strength <= 0):
        raise ValueError('残差拟合输入无效')
    w = w / w.sum()
    mean = w @ x
    scale = np.sqrt(w @ (x - mean) ** 2)
    scale = np.where(scale < 1e-8, 1., scale)
    design = np.column_stack(((x-mean)/scale, np.ones(len(x))))
    offset = logit(p)
    initial = np.zeros(design.shape[1])

    def objective(beta):
        z = offset + design @ beta
        loss = w @ (np.logaddexp(0, z) - y*z) + .5 * strength * (beta @ beta)
        gradient = design.T @ (w * (expit(z)-y)) + strength * beta
        return float(loss), gradient

    fit = minimize(objective, initial, jac=True, method='L-BFGS-B',
                   options={'maxiter': 200, 'ftol': 1e-13, 'gtol': 1e-9})
    if not fit.success or not np.all(np.isfinite(fit.x)):
        return ResidualDirectionModel(mean, scale, initial, 'FALLBACK_NO_CONVERGENCE')
    return ResidualDirectionModel(mean, scale, fit.x)
