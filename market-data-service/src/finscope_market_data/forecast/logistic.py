from __future__ import annotations

from collections.abc import Sequence
from dataclasses import dataclass

import numpy as np
from numpy.typing import NDArray

from finscope_market_data.forecast.features import ForecastSample


@dataclass(frozen=True)
class RegularizedLogisticModel:
    means: NDArray[np.float64]
    scales: NDArray[np.float64]
    weights: NDArray[np.float64]

    @classmethod
    def fit(cls, samples: Sequence[ForecastSample]) -> "RegularizedLogisticModel":
        if not samples:
            raise ValueError("训练样本不能为空")
        dimensions = len(samples[0].features)
        if dimensions == 0 or any(len(sample.features) != dimensions for sample in samples):
            raise ValueError("预测特征维度不一致")
        matrix = np.asarray([sample.features for sample in samples], dtype=np.float64)
        labels = np.asarray([sample.positive for sample in samples], dtype=np.float64)
        means = matrix.mean(axis=0)
        scales = matrix.std(axis=0, ddof=1)
        scales[scales < 1e-9] = 1.0
        normalized = (matrix - means) / scales
        weights = np.zeros(dimensions + 1, dtype=np.float64)
        for iteration in range(320):
            scores = weights[0] + normalized @ weights[1:]
            probabilities = cls._sigmoid(scores)
            errors = probabilities - labels
            rate = 0.12 / np.sqrt(1.0 + iteration / 40.0)
            weights[0] -= rate * errors.mean()
            weights[1:] -= rate * ((normalized.T @ errors) / len(samples) + 0.02 * weights[1:])
        return cls(means=means, scales=scales, weights=weights)

    def predict(self, features: Sequence[float]) -> float:
        if len(features) != len(self.means):
            raise ValueError("预测特征维度不一致")
        normalized = (np.asarray(features, dtype=np.float64) - self.means) / self.scales
        probability = float(self._sigmoid(self.weights[0] + normalized @ self.weights[1:]))
        return min(0.99, max(0.01, probability))

    @staticmethod
    def _sigmoid(value: NDArray[np.float64] | np.float64) -> NDArray[np.float64] | np.float64:
        return 1.0 / (1.0 + np.exp(-np.clip(value, -30.0, 30.0)))
