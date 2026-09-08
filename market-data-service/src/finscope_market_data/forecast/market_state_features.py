"""Generic observable market state, computed before future-label filtering."""
import numpy as np

MARKET_STATE_FEATURE_CODES = (
    'STATE_UP_BREADTH_1', 'STATE_UP_BREADTH_5', 'STATE_UP_BREADTH_20',
    'STATE_EQUAL_RETURN_1', 'STATE_DISPERSION_1', 'STATE_MEAN_VOLATILITY_20',
    'STATE_ACTIVITY_SPREAD_5', 'STATE_TREND_AGREEMENT',
    'STATE_STOCK_RELATIVE_1', 'STATE_STOCK_RELATIVE_5',
    'STATE_MOMENTUM_BREADTH_INTERACTION', 'STATE_REVERSAL_DISPERSION_INTERACTION',
)


def market_state_features(features_by_code):
    if not features_by_code:
        return {}
    codes = sorted(features_by_code)
    x = np.asarray([features_by_code[code] for code in codes], dtype=float)
    if x.ndim != 2 or x.shape[1] < 9 or not np.all(np.isfinite(x)):
        raise ValueError('市场状态需要完整的基础价量特征')
    ret1 = (1 + x[:, 7]) * (1 + x[:, 8]) - 1
    activity = x[:, 6] >= np.median(x[:, 6])
    spread = float(np.mean(x[activity, 0]) - np.mean(x[~activity, 0])) if np.any(~activity) else 0.
    dispersion = float(np.std(ret1))
    common = (float(np.mean(ret1 > 0)), float(np.mean(x[:, 0] > 0)), float(np.mean(x[:, 1] > 0)),
              float(np.mean(ret1)), dispersion, float(np.mean(x[:, 5])), spread,
              float(np.mean((x[:, 0] > 0) == (x[:, 1] > 0))))
    return {code: (*common, float(ret1[i] - np.mean(ret1)), float(x[i, 0] - np.mean(x[:, 0])),
                   float(x[i, 1] * (2 * common[2] - 1)), float(-ret1[i] * dispersion))
            for i, code in enumerate(codes)}
