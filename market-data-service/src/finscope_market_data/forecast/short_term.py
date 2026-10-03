"""Recent, regularized next-close learner; features require only signal-day bars.

This complements the slower local model. It does not consume intraday bars or
claim that a large daily move is an exchange-verified limit-up/down event.
"""
from __future__ import annotations

import math
from typing import Sequence

import numpy as np
from sklearn.linear_model import LogisticRegression
from sklearn.preprocessing import StandardScaler

from finscope_market_data.models import DailyBar
from finscope_market_data.forecast.context import AlignedForecastContext
from finscope_market_data.forecast.features import ForecastSample

SHORT_TERM_FEATURE_CODES = (
    'SCALED_RETURN_1', 'SCALED_RETURN_2', 'SCALED_RETURN_5', 'SCALED_RETURN_20',
    'CLOSE_POSITION', 'UPPER_WICK_SHARE', 'LOWER_WICK_SHARE',
    'SCALED_GAP', 'SCALED_BODY', 'AMOUNT_SURPRISE', 'SCALED_RANGE',
    'MARKET_RETURN_1', 'SCALED_EXCESS_RETURN_1',
    'LARGE_UP_AT_HIGH', 'LARGE_DOWN_AT_LOW',
)
TRAIN_WINDOW = 504
HALF_LIFE = 126
REGULARIZATION_C = .03


def short_term_features(bars: Sequence[DailyBar], index: int,
                        context: AlignedForecastContext | None = None) -> tuple[float, ...]:
    if index < 20 or index >= len(bars):
        raise ValueError('短期特征需要截至信号日的 21 根完整日线')
    bar, previous = bars[index], bars[index - 1]
    closes = np.asarray([row.close for row in bars[index - 20:index + 1]])
    volatility = max(.005, float(np.std(np.log(closes[1:] / closes[:-1]))))
    span = max(bar.high - bar.low, bar.close * .001)
    amounts = [row.amount for row in bars[index - 20:index]]
    if any(value is None or value <= 0 for value in amounts) or not bar.amount or bar.amount <= 0:
        raise ValueError('短期特征需要完整正值成交额')
    surprise = bar.amount / float(np.mean(amounts))
    market_return = 0.0
    if context is not None and index < len(context.market_bars):
        current_market, previous_market = context.market_bars[index], context.market_bars[index - 1]
        if current_market is not None and previous_market is not None:
            market_return = current_market.close / previous_market.close - 1
    values = [math.log(bar.close / bars[index - length].close) / volatility / math.sqrt(length)
              for length in (1, 2, 5, 20)]
    values.extend((
        (bar.close - bar.low) / span - .5,
        (bar.high - max(bar.open, bar.close)) / span,
        (min(bar.open, bar.close) - bar.low) / span,
        (bar.open / previous.close - 1) / volatility,
        (bar.close / bar.open - 1) / volatility,
        math.log(max(surprise, .01)),
        (bar.high - bar.low) / previous.close / volatility,
        market_return / .01,
        (bar.close / previous.close - 1 - market_return) / volatility,
        float(bar.close >= bar.high * .998 and bar.close / previous.close > 1.095),
        float(bar.close <= bar.low * 1.002 and bar.close / previous.close < .905),
    ))
    if not np.all(np.isfinite(values)):
        raise ValueError('短期特征包含非有限值')
    return tuple(float(value) for value in np.clip(values, -8, 8))


class ShortTermModel:
    """Decay weights follow observed sessions; fit excludes all unmatured labels."""

    def __init__(self, samples: Sequence[ForecastSample], cutoff: str):
        training = sorted((row for row in samples if row.exit_date < cutoff),
                          key=lambda row: row.signal_date)[-TRAIN_WINDOW:]
        if len(training) < 120:
            raise ValueError('短期模型需要至少 120 个成熟样本')
        matrix = np.asarray([row.features for row in training], dtype=float)
        if matrix.shape != (len(training), len(SHORT_TERM_FEATURE_CODES)) or not np.all(np.isfinite(matrix)):
            raise ValueError('短期模型特征维度或数值无效')
        self.training_through = training[-1].exit_date
        self.training_count = len(training)
        self.scaler = StandardScaler().fit(matrix)
        labels = np.asarray([row.positive for row in training], dtype=int)
        weights = np.exp2(-np.arange(len(training) - 1, -1, -1) / HALF_LIFE)
        self.prior = float(np.average(labels, weights=weights))
        self.model = None
        if len(set(labels)) > 1:
            self.model = LogisticRegression(C=REGULARIZATION_C, max_iter=500)
            self.model.fit(self._transform(matrix), labels, sample_weight=weights)

    def _transform(self, matrix):
        return np.clip(self.scaler.transform(matrix), -5, 5)

    def predict(self, features: Sequence[float]) -> float:
        if len(features) != len(SHORT_TERM_FEATURE_CODES) or not np.all(np.isfinite(features)):
            raise ValueError('短期预测特征维度或数值无效')
        if self.model is None:
            return float(np.clip(self.prior, .01, .99))
        return float(self.model.predict_proba(self._transform([features]))[0, 1])
