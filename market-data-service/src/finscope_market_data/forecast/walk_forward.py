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
    independent_sample_count: int
    accuracy: float
    brier_score: float
    baseline_brier_score: float


def validate_walk_forward(samples: Sequence[ForecastSample]) -> WalkForwardResult:
    ordered = sorted(samples, key=lambda item: item.signal_date)
    initial_training_size = max(120, math.floor(len(ordered) * 0.60))
    observations: list[WalkForwardObservation] = []
    model: RegularizedLogisticModel | None = None
    matured_count = 0
    for candidate in ordered:
        while (
            matured_count < len(ordered)
            and ordered[matured_count].exit_date < candidate.signal_date
        ):
            matured_count += 1
        if matured_count < initial_training_size:
            continue
        matured = ordered[:matured_count]
        if model is None or len(observations) % 20 == 0:
            model = RegularizedLogisticModel.fit(matured)
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
    return _metrics(observations)


def _metrics(observations: list[WalkForwardObservation]) -> WalkForwardResult:
    independent = observations[::20]
    if not independent:
        return WalkForwardResult((), 0, 0.0, 0.0, 0.0)
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
        independent_sample_count=count,
        accuracy=correct / count,
        brier_score=brier / count,
        baseline_brier_score=baseline_brier / count,
    )
