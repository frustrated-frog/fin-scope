from __future__ import annotations

from dataclasses import dataclass
import math
from typing import Protocol, Sequence

from finscope_market_data.forecast.features import ForecastSample
from finscope_market_data.forecast.logistic import RegularizedLogisticModel
from finscope_market_data.forecast.qualification import evaluate_probability_metrics


class ProbabilityModel(Protocol):
    def predict(self, features: Sequence[float]) -> float: ...


@dataclass(frozen=True)
class CandidateResult:
    code: str
    name: str
    selected: bool
    selection_sample_count: int
    accuracy: float
    brier_score: float
    log_loss: float
    baseline_brier_score: float
    reason: str


@dataclass(frozen=True)
class ModelCompetition:
    selected_model: str
    selection_end_date: str
    calibration_start_date: str
    selection_rule: str
    candidates: tuple[CandidateResult, ...]


@dataclass(frozen=True)
class BoostedStumpModel:
    baseline: float
    stumps: tuple[tuple[int, float, float, float], ...]

    @classmethod
    def fit(cls, samples: Sequence[ForecastSample]) -> "BoostedStumpModel":
        baseline = _bounded(sum(item.positive for item in samples) / len(samples))
        residuals = [float(item.positive) - baseline for item in samples]
        stumps: list[tuple[int, float, float, float]] = []
        for _ in range(12):
            best: tuple[float, int, float, float, float] | None = None
            for dimension in range(len(samples[0].features)):
                values = sorted(item.features[dimension] for item in samples)
                for fraction in (0.25, 0.5, 0.75):
                    threshold = values[round((len(values) - 1) * fraction)]
                    left = [residual for sample, residual in zip(samples, residuals) if sample.features[dimension] <= threshold]
                    right = [residual for sample, residual in zip(samples, residuals) if sample.features[dimension] > threshold]
                    if not left or not right:
                        continue
                    left_value = sum(left) / len(left)
                    right_value = sum(right) / len(right)
                    error = sum(
                        (residual - (left_value if sample.features[dimension] <= threshold else right_value)) ** 2
                        for sample, residual in zip(samples, residuals)
                    )
                    candidate = (error, dimension, threshold, left_value, right_value)
                    if best is None or candidate < best:
                        best = candidate
            if best is None:
                break
            _, dimension, threshold, left_value, right_value = best
            rate = 0.25
            stumps.append((dimension, threshold, rate * left_value, rate * right_value))
            residuals = [
                residual - (rate * left_value if sample.features[dimension] <= threshold else rate * right_value)
                for sample, residual in zip(samples, residuals)
            ]
        return cls(baseline=baseline, stumps=tuple(stumps))

    def predict(self, features: Sequence[float]) -> float:
        value = self.baseline + sum(
            left if features[dimension] <= threshold else right
            for dimension, threshold, left, right in self.stumps
        )
        return _bounded(value)


@dataclass(frozen=True)
class RuleBaselineModel:
    momentum_dimension: int = 0

    def predict(self, features: Sequence[float]) -> float:
        return _bounded(0.5 + math.tanh(features[self.momentum_dimension] * 8.0) * 0.20)


def run_model_competition(
    samples: Sequence[ForecastSample], *, independent_stride_days: int
) -> ModelCompetition:
    ordered = tuple(sorted(samples, key=lambda item: item.signal_date))
    development_end = max(120, int(len(ordered) * 0.60))
    selection_start = max(80, int(development_end * 0.75))
    calibration_start_date = ordered[development_end].signal_date
    validation = tuple(
        item
        for item in ordered[selection_start:development_end:independent_stride_days]
        if item.exit_date < calibration_start_date
    )
    validation_start = ordered[selection_start].signal_date
    training = tuple(
        item for item in ordered[:selection_start]
        if item.exit_date < validation_start
    )
    if not training or not validation or development_end >= len(ordered):
        raise ValueError("模型竞赛样本不足")
    candidates: tuple[tuple[str, str, ProbabilityModel], ...] = (
        ("LOGISTIC", "正则化逻辑回归", RegularizedLogisticModel.fit(training)),
        ("BOOSTED_STUMPS", "轻量梯度提升树桩", BoostedStumpModel.fit(training)),
        ("RULE_BASELINE", "确定性动量规则", RuleBaselineModel()),
    )
    baseline = sum(item.positive for item in training) / len(training)
    scored: list[tuple[float, str, str, object]] = []
    for code, name, model in candidates:
        probabilities = [model.predict(item.features) for item in validation]
        metrics = evaluate_probability_metrics(
            probabilities, [item.positive for item in validation], baseline
        )
        score = metrics.brier_score + metrics.log_loss * 0.05
        scored.append((score, code, name, metrics))
    selected_code = min(scored, key=lambda item: (item[0], item[1]))[1]
    results = tuple(
        CandidateResult(
            code=code,
            name=name,
            selected=code == selected_code,
            selection_sample_count=len(validation),
            accuracy=metrics.accuracy,
            brier_score=metrics.brier_score,
            log_loss=metrics.log_loss,
            baseline_brier_score=metrics.baseline_brier_score,
            reason=("开发区内部验证最优" if code == selected_code else "开发区内部验证未胜出"),
        )
        for _, code, name, metrics in sorted(scored, key=lambda item: item[1])
    )
    return ModelCompetition(
        selected_model=selected_code,
        selection_end_date=validation[-1].signal_date,
        calibration_start_date=calibration_start_date,
        selection_rule="只使用开发区尾段比较 Brier 与 Log Loss；训练标签退出日早于验证起点；校准区和锁定测试不参与冠军选择",
        candidates=results,
    )


def fit_model(code: str, samples: Sequence[ForecastSample]) -> ProbabilityModel:
    if code == "LOGISTIC":
        return RegularizedLogisticModel.fit(samples)
    if code == "BOOSTED_STUMPS":
        return BoostedStumpModel.fit(samples)
    if code == "RULE_BASELINE":
        return RuleBaselineModel()
    raise ValueError(f"未知预测模型：{code}")


def _bounded(value: float) -> float:
    return min(0.99, max(0.01, float(value)))
