"""Date-weighted next-session calibration candidates; legacy Platt stays reproducible."""
import numpy as np
from scipy.optimize import minimize
from scipy.special import expit, logit

from finscope_market_data.forecast.calibration import CalibrationResult, PROBABILITY_EPSILON

CALIBRATION_MODES = ('RAW', 'INTERCEPT', 'PLATT_0.001', 'PLATT_0.01', 'PLATT_0.1')


def fit_direction_calibration(probabilities, labels, weights, mode):
    p, y, w = (np.asarray(values, dtype=float) for values in (probabilities, labels, weights))
    if (p.ndim != 1 or p.shape != y.shape or p.shape != w.shape or not len(p)
            or not np.all(np.isfinite(p)) or np.any((p < 0) | (p > 1))
            or not np.all(np.isin(y, [0, 1])) or not np.all(np.isfinite(w))
            or np.any(w < 0) or w.sum() <= 0 or mode not in CALIBRATION_MODES):
        raise ValueError('方向校准需要有效等长概率、二值标签、非负权重及已定义模式')
    positive_weight = w > 0
    p, y, w = p[positive_weight], y[positive_weight], w[positive_weight]
    w = w / w.sum()
    p = np.clip(p, PROBABILITY_EPSILON, 1 - PROBABILITY_EPSILON)
    x = logit(p)
    raw_loss = float(w @ (np.logaddexp(0, x) - y * x))

    def fallback(reason):
        return CalibrationResult('NOT_FITTED', 1., 0., len(p), int(y.sum()), raw_loss, raw_loss, reason)

    if mode == 'RAW':
        return fallback('保持原始概率')
    if len(p) < 15 or min(y.sum(), len(y) - y.sum()) < 5:
        return fallback('校准样本不足或正负标签不足 5 个')
    strength = float(mode.split('_')[1]) if mode.startswith('PLATT_') else .01
    center = np.array([1., 0.])
    ridge = np.array([strength, .002])
    design = np.column_stack((x, np.ones(len(x))))

    def objective(parameters):
        z = design @ parameters
        delta = parameters - center
        loss = w @ (np.logaddexp(0, z) - y * z) + .5 * (ridge * delta) @ delta
        gradient = design.T @ (w * (expit(z) - y)) + ridge * delta
        return float(loss), gradient

    bounds = [(1., 1.) if mode == 'INTERCEPT' else (0., None), (None, None)]
    fit = minimize(objective, center, jac=True, bounds=bounds, method='L-BFGS-B',
                   options={'maxiter': 200, 'ftol': 1e-14, 'gtol': 1e-10})
    if not fit.success or not np.all(np.isfinite(fit.x)):
        return fallback('校准求解未收敛')
    # Score the same bounded probabilities that consumers receive.
    calibrated = np.clip(expit(design @ fit.x), PROBABILITY_EPSILON, 1 - PROBABILITY_EPSILON)
    loss = float(-w @ (y * np.log(calibrated) + (1 - y) * np.log1p(-calibrated)))
    if loss > raw_loss + 1e-12:
        return fallback('校准区加权 Log Loss 退化')
    return CalibrationResult('FITTED', float(fit.x[0]), float(fit.x[1]), len(p), int(y.sum()), raw_loss, loss)
