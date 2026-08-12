from __future__ import annotations

from dataclasses import dataclass
import math
import statistics
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
    validation_fold_count: int
    brier_std: float
    reason: str


@dataclass(frozen=True)
class ModelCompetition:
    selected_model: str
    selection_end_date: str
    calibration_start_date: str
    selection_rule: str
    candidates: tuple[CandidateResult, ...]
    fold_audits: tuple["CompetitionFoldAudit", ...]


@dataclass(frozen=True)
class CompetitionFoldAudit:
    fold: int
    training_sample_count: int
    validation_sample_count: int
    training_last_exit_date: str
    validation_start_date: str
    validation_end_date: str


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


@dataclass(frozen=True)
class RegimeAwareLogisticModel:
    global_model: RegularizedLogisticModel
    regime_models: tuple[tuple[str, RegularizedLogisticModel], ...]
    volatility_threshold: float
    local_weight: float = 0.65

    @classmethod
    def fit(
        cls,
        samples: Sequence[ForecastSample],
        *,
        minimum_regime_samples: int = 60,
    ) -> "RegimeAwareLogisticModel":
        if not samples:
            raise ValueError("市场状态模型训练样本不能为空")
        global_model = RegularizedLogisticModel.fit(samples)
        volatility_threshold = statistics.median(
            _volatility_feature(item.features) for item in samples
        )
        grouped: dict[str, list[ForecastSample]] = {}
        for sample in samples:
            grouped.setdefault(
                _regime(sample.features, volatility_threshold), []
            ).append(sample)
        regime_models = tuple(
            (code, RegularizedLogisticModel.fit(values))
            for code, values in sorted(grouped.items())
            if len(values) >= minimum_regime_samples
            and 0 < sum(item.positive for item in values) < len(values)
        )
        return cls(
            global_model=global_model,
            regime_models=regime_models,
            volatility_threshold=volatility_threshold,
        )

    def predict(self, features: Sequence[float]) -> float:
        global_probability = self.global_model.predict(features)
        local_models = dict(self.regime_models)
        local = local_models.get(_regime(features, self.volatility_threshold))
        if local is None:
            return global_probability
        return _bounded(
            self.local_weight * local.predict(features)
            + (1.0 - self.local_weight) * global_probability
        )


def run_model_competition(
    samples: Sequence[ForecastSample], *, independent_stride_days: int
) -> ModelCompetition:
    ordered = tuple(sorted(samples, key=lambda item: item.signal_date))
    development_end = max(120, int(len(ordered) * 0.60))
    calibration_start_date = ordered[development_end].signal_date
    fold_boundaries = _fold_boundaries(development_end)
    folds: list[tuple[tuple[ForecastSample, ...], tuple[ForecastSample, ...]]] = []
    audits: list[CompetitionFoldAudit] = []
    for fold, (validation_start_index, validation_end_index) in enumerate(
        fold_boundaries, start=1
    ):
        validation_start_date = ordered[validation_start_index].signal_date
        training = tuple(
            item for item in ordered[:validation_start_index]
            if item.exit_date < validation_start_date
        )
        validation = tuple(
            item
            for item in ordered[
                validation_start_index:validation_end_index:independent_stride_days
            ]
            if item.exit_date < calibration_start_date
        )
        if not training or not validation:
            continue
        folds.append((training, validation))
        audits.append(CompetitionFoldAudit(
            fold=fold,
            training_sample_count=len(training),
            validation_sample_count=len(validation),
            training_last_exit_date=max(item.exit_date for item in training),
            validation_start_date=validation[0].signal_date,
            validation_end_date=validation[-1].signal_date,
        ))
    if len(folds) < 3 or development_end >= len(ordered):
        raise ValueError("模型竞赛样本不足")
    candidates: tuple[tuple[str, str], ...] = (
        ("LOGISTIC", "正则化逻辑回归"),
        ("BOOSTED_STUMPS", "轻量梯度提升树桩"),
        ("REGIME_LOGISTIC", "市场状态感知逻辑回归"),
        ("RULE_BASELINE", "确定性动量规则"),
    )
    scored: list[tuple[float, str, str, tuple[object, ...]]] = []
    for code, name in candidates:
        fold_metrics: list[object] = []
        for training, validation in folds:
            model = fit_model(code, training)
            probabilities = [model.predict(item.features) for item in validation]
            baseline = sum(item.positive for item in training) / len(training)
            fold_metrics.append(evaluate_probability_metrics(
                probabilities, [item.positive for item in validation], baseline
            ))
        brier_std = statistics.pstdev(
            item.brier_score for item in fold_metrics
        )
        score = (
            statistics.mean(item.brier_score for item in fold_metrics)
            + statistics.mean(item.log_loss for item in fold_metrics) * 0.05
            + brier_std * 0.10
        )
        scored.append((score, code, name, tuple(fold_metrics)))
    selected_code = min(scored, key=lambda item: (item[0], item[1]))[1]
    results = tuple(
        CandidateResult(
            code=code,
            name=name,
            selected=code == selected_code,
            selection_sample_count=sum(item.sample_count for item in fold_metrics),
            accuracy=statistics.mean(item.accuracy for item in fold_metrics),
            brier_score=statistics.mean(item.brier_score for item in fold_metrics),
            log_loss=statistics.mean(item.log_loss for item in fold_metrics),
            baseline_brier_score=statistics.mean(
                item.baseline_brier_score for item in fold_metrics
            ),
            validation_fold_count=len(fold_metrics),
            brier_std=statistics.pstdev(
                item.brier_score for item in fold_metrics
            ),
            reason=("开发区内部验证最优" if code == selected_code else "开发区内部验证未胜出"),
        )
        for _, code, name, fold_metrics in sorted(scored, key=lambda item: item[1])
    )
    return ModelCompetition(
        selected_model=selected_code,
        selection_end_date=audits[-1].validation_end_date,
        calibration_start_date=calibration_start_date,
        selection_rule="只使用开发区三折扩展窗口比较 Brier、Log Loss 与折间稳定性；训练标签退出日早于各折验证起点；校准区和锁定测试不参与冠军选择",
        candidates=results,
        fold_audits=tuple(audits),
    )


def fit_model(code: str, samples: Sequence[ForecastSample]) -> ProbabilityModel:
    if code == "LOGISTIC":
        return RegularizedLogisticModel.fit(samples)
    if code == "BOOSTED_STUMPS":
        return BoostedStumpModel.fit(samples)
    if code == "REGIME_LOGISTIC":
        return RegimeAwareLogisticModel.fit(samples)
    if code == "RULE_BASELINE":
        return RuleBaselineModel()
    raise ValueError(f"未知预测模型：{code}")


def _bounded(value: float) -> float:
    return min(0.99, max(0.01, float(value)))


def _fold_boundaries(development_end: int) -> tuple[tuple[int, int], ...]:
    selection_start = max(80, int(development_end * 0.75))
    width = max(1, (development_end - selection_start) // 3)
    return (
        (selection_start, selection_start + width),
        (selection_start + width, selection_start + width * 2),
        (selection_start + width * 2, development_end),
    )


def _volatility_feature(features: Sequence[float]) -> float:
    if len(features) > 16:
        return float(features[16])
    return abs(float(features[-1]))


def _regime(features: Sequence[float], volatility_threshold: float) -> str:
    momentum = float(features[14] if len(features) > 14 else features[0])
    trend = "UP" if momentum >= 0 else "DOWN"
    volatility = "HIGH" if _volatility_feature(features) > volatility_threshold else "LOW"
    return f"{trend}_{volatility}"
