from __future__ import annotations

from dataclasses import dataclass
import math
from typing import Sequence

from finscope_market_data.forecast.features import ForecastSample
from finscope_market_data.forecast.logistic import RegularizedLogisticModel


@dataclass(frozen=True)
class WalkForwardObservation:
    signal_date: str
    training_through: str
    probability: float
    baseline_probability: float
    actual_return: float

    @property
    def actual_positive(self) -> bool:
        return self.actual_return > 0

    @property
    def correct(self) -> bool:
        return (self.probability >= 0.5) == self.actual_positive


@dataclass(frozen=True)
class WalkForwardResult:
    observations: tuple[WalkForwardObservation, ...]
    initial_training_size: int
    in_sample_count: int
    in_sample_accuracy: float
    in_sample_brier_score: float
    independent_sample_count: int
    accuracy: float
    brier_score: float
    baseline_brier_score: float


def validate_walk_forward(
    samples: Sequence[ForecastSample],
    *,
    independent_stride_days: int = 20,
    model_code: str = "LOGISTIC",
) -> WalkForwardResult:
    if independent_stride_days < 1:
        raise ValueError("独立锚点步长必须为正整数")
    ordered = sorted(samples, key=lambda item: item.signal_date)
    initial_training_size = max(120, math.floor(len(ordered) * 0.60))
    initial_training_size = min(initial_training_size, len(ordered))
    in_sample_accuracy, in_sample_brier = _in_sample_metrics(
        ordered[:initial_training_size], model_code
    )
    observations: list[WalkForwardObservation] = []
    model: RegularizedLogisticModel | None = None
    matured_count = 0
    refit_every = retraining_interval(model_code)
    for candidate in ordered:
        while (
            matured_count < len(ordered)
            and ordered[matured_count].exit_date < candidate.signal_date
        ):
            matured_count += 1
        if matured_count < initial_training_size:
            continue
        matured = ordered[:matured_count]
        if model is None or len(observations) % refit_every == 0:
            model = _fit_model(model_code, matured)
        baseline = sum(sample.positive for sample in matured) / len(matured)
        observations.append(
            WalkForwardObservation(
                signal_date=candidate.signal_date,
                training_through=matured[-1].exit_date,
                probability=model.predict(candidate.features),
                baseline_probability=baseline,
                actual_return=candidate.net_return,
            )
        )
    return _metrics(
        observations,
        initial_training_size,
        in_sample_accuracy,
        in_sample_brier,
        independent_stride_days,
    )


def _metrics(
    observations: list[WalkForwardObservation],
    initial_training_size: int,
    in_sample_accuracy: float,
    in_sample_brier: float,
    independent_stride_days: int,
) -> WalkForwardResult:
    independent = observations[::independent_stride_days]
    if not independent:
        return WalkForwardResult(
            (),
            initial_training_size,
            initial_training_size,
            in_sample_accuracy,
            in_sample_brier,
            0,
            0.0,
            0.0,
            0.0,
        )
    correct = sum(item.correct for item in independent)
    brier = sum(
        (item.probability - float(item.actual_positive)) ** 2 for item in independent
    )
    baseline_brier = sum(
        (item.baseline_probability - float(item.actual_positive)) ** 2
        for item in independent
    )
    count = len(independent)
    return WalkForwardResult(
        observations=tuple(observations),
        initial_training_size=initial_training_size,
        in_sample_count=initial_training_size,
        in_sample_accuracy=in_sample_accuracy,
        in_sample_brier_score=in_sample_brier,
        independent_sample_count=count,
        accuracy=correct / count,
        brier_score=brier / count,
        baseline_brier_score=baseline_brier / count,
    )


def _in_sample_metrics(
    samples: Sequence[ForecastSample], model_code: str = "LOGISTIC"
) -> tuple[float, float]:
    if not samples:
        return 0.0, 0.0
    model = _fit_model(model_code, samples)
    probabilities = [model.predict(item.features) for item in samples]
    accuracy = sum(
        (probability >= 0.5) == sample.positive
        for probability, sample in zip(probabilities, samples)
    ) / len(samples)
    brier = sum(
        (probability - float(sample.positive)) ** 2
        for probability, sample in zip(probabilities, samples)
    ) / len(samples)
    return accuracy, brier


def _fit_model(model_code: str, samples: Sequence[ForecastSample]):
    if model_code == "LOGISTIC":
        return RegularizedLogisticModel.fit(samples)
    from finscope_market_data.forecast.model_competition import fit_model
    return fit_model(model_code, samples)


def retraining_interval(model_code: str) -> int:
    if model_code in {"HISTOGRAM_GB", "STACKED"}:
        return 60
    return 20
