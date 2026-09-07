"""Separate absolute price returns from beta-adjusted, volatility-scaled stock strength."""
from dataclasses import dataclass
from typing import Sequence
import numpy as np
from lightgbm import LGBMRegressor
from sklearn.linear_model import Ridge
from sklearn.pipeline import make_pipeline
from sklearn.preprocessing import StandardScaler
from finscope_market_data.forecast.joint_dataset import JointRow


def market_targets_available(rows: Sequence[JointRow]) -> bool:
    return bool(rows) and all(row.market_return is not None and len(row.sample.features) >= 18 for row in rows)


def _scale_beta(x):
    return np.maximum(x[:, 5], .005), np.clip(x[:, 17], 0., 2.)


def residual_targets(rows: Sequence[JointRow]) -> np.ndarray:
    if not market_targets_available(rows):
        raise ValueError('相对收益目标需要完整的同日市场收益与历史波动率')
    x = np.asarray([row.sample.features for row in rows])
    scale, beta = _scale_beta(x)
    return (np.array([row.sample.net_return for row in rows])
            - beta * np.array([row.market_return for row in rows])) / scale


def _predict(model, x):
    return model.booster_.predict(x) if isinstance(model, LGBMRegressor) else model.predict(x)


@dataclass
class ReturnModel:
    code: str
    stock_model: object
    market_model: object | None = None

    def predict(self, x: np.ndarray) -> np.ndarray:
        prediction = _predict(self.stock_model, x)
        if self.code == 'ABSOLUTE':
            return prediction
        scale, beta = _scale_beta(x)
        return prediction * scale + beta * self.market_model.predict(x[:, [13, 14, 16]])


def fit_return_model(code: str, rows: Sequence[JointRow], parameters: dict) -> ReturnModel:
    x = np.asarray([row.sample.features for row in rows])
    if code == 'ABSOLUTE':
        target = np.array([row.sample.net_return for row in rows])
        return ReturnModel(code, LGBMRegressor(**parameters).fit(x, target))
    if code != 'MARKET_RESIDUAL':
        raise ValueError('未知收益目标')
    target = residual_targets(rows)
    # One market observation per date: a day with more stocks must not be counted repeatedly.
    by_day = {}
    for index, row in enumerate(rows):
        by_day.setdefault(row.sample.signal_date, index)
    indices = list(by_day.values())
    market = make_pipeline(StandardScaler(), Ridge(alpha=10.)).fit(
        x[indices][:, [13, 14, 16]], [rows[index].market_return for index in indices],
    )
    return ReturnModel(code, LGBMRegressor(**parameters).fit(x, target), market)
