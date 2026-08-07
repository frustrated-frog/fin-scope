from __future__ import annotations

from collections.abc import Sequence
from dataclasses import dataclass
import math

from finscope_market_data.forecast.features import ForecastSample


@dataclass(frozen=True)
class RegularizedLogisticModel:
    means: tuple[float, ...]
    scales: tuple[float, ...]
    weights: tuple[float, ...]

    @classmethod
    def fit(cls, samples: Sequence[ForecastSample]) -> "RegularizedLogisticModel":
        if not samples:
            raise ValueError("训练样本不能为空")
        dimensions = len(samples[0].features)
        if dimensions == 0 or any(len(sample.features) != dimensions for sample in samples):
            raise ValueError("预测特征维度不一致")
        means = tuple(
            sum(sample.features[index] for sample in samples) / len(samples)
            for index in range(dimensions)
        )
        scales = tuple(
            cls._scale(samples, index, means[index]) for index in range(dimensions)
        )
        weights = [0.0] * (dimensions + 1)
        for iteration in range(320):
            gradient = [0.0] * len(weights)
            for sample in samples:
                normalized = cls._normalize(sample.features, means, scales)
                error = cls._sigmoid(cls._score(weights, normalized)) - float(sample.positive)
                gradient[0] += error
                for index, value in enumerate(normalized):
                    gradient[index + 1] += error * value
            rate = 0.12 / math.sqrt(1.0 + iteration / 40.0)
            weights[0] -= rate * gradient[0] / len(samples)
            for index in range(1, len(weights)):
                weights[index] -= rate * (gradient[index] / len(samples) + 0.02 * weights[index])
        return cls(means=means, scales=scales, weights=tuple(weights))

    def predict(self, features: Sequence[float]) -> float:
        if len(features) != len(self.means):
            raise ValueError("预测特征维度不一致")
        normalized = self._normalize(features, self.means, self.scales)
        return min(0.99, max(0.01, self._sigmoid(self._score(self.weights, normalized))))

    @staticmethod
    def _scale(samples: Sequence[ForecastSample], index: int, mean: float) -> float:
        variance = sum((sample.features[index] - mean) ** 2 for sample in samples) / max(
            1, len(samples) - 1
        )
        scale = math.sqrt(variance)
        return scale if scale >= 1e-9 else 1.0

    @staticmethod
    def _normalize(
        features: Sequence[float], means: Sequence[float], scales: Sequence[float]
    ) -> tuple[float, ...]:
        return tuple(
            (features[index] - means[index]) / scales[index]
            for index in range(len(features))
        )

    @staticmethod
    def _score(weights: Sequence[float], features: Sequence[float]) -> float:
        return weights[0] + sum(
            weights[index + 1] * value for index, value in enumerate(features)
        )

    @staticmethod
    def _sigmoid(value: float) -> float:
        if value > 30:
            return 1.0
        if value < -30:
            return 0.0
        return 1.0 / (1.0 + math.exp(-value))
