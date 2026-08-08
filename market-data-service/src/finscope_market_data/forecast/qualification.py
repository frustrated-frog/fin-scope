from __future__ import annotations

from dataclasses import dataclass
import math
from typing import Sequence

from finscope_market_data.forecast.calibration import CalibrationResult, PlattCalibrator
from finscope_market_data.forecast.features import ForecastSample
from finscope_market_data.forecast.logistic import RegularizedLogisticModel


@dataclass(frozen=True)
class SplitSlice:
    start_date: str
    end_date: str
    sample_count: int
    independent_sample_count: int
    positive_count: int
    purged_count: int = 0


@dataclass(frozen=True)
class SplitAudit:
    development: SplitSlice
    calibration: SplitSlice
    locked_test: SplitSlice


@dataclass(frozen=True)
class QualificationSplit:
    development: tuple[ForecastSample, ...]
    calibration: tuple[ForecastSample, ...]
    locked_test: tuple[ForecastSample, ...]
    audit: SplitAudit


@dataclass(frozen=True)
class ReliabilityBin:
    lower_bound: float
    upper_bound: float
    count: int
    mean_probability: float | None
    observed_up_rate: float | None
    calibration_error: float | None


@dataclass(frozen=True)
class ProbabilityMetrics:
    sample_count: int
    accuracy: float
    brier_score: float
    baseline_brier_score: float
    brier_skill_score: float
    log_loss: float
    expected_calibration_error: float


@dataclass(frozen=True)
class LockedTestResult:
    baseline_probability: float
    raw_probabilities: tuple[float, ...]
    calibrated_probabilities: tuple[float, ...]
    labels: tuple[bool, ...]
    signal_dates: tuple[str, ...]
    raw_metrics: ProbabilityMetrics
    calibrated_metrics: ProbabilityMetrics
    baseline_metrics: ProbabilityMetrics
    reliability_bins: tuple[ReliabilityBin, ...]


@dataclass(frozen=True)
class ModelQualification:
    status: str
    reason: str | None
    split_audit: SplitAudit
    calibration: CalibrationResult
    locked_test: LockedTestResult
    calibration_raw_probabilities: tuple[float, ...]
    calibration_labels: tuple[bool, ...]


def split_qualification_samples(
    samples: Sequence[ForecastSample],
) -> QualificationSplit:
    ordered = sorted(samples, key=lambda item: item.signal_date)
    dates = [item.signal_date for item in ordered]
    if len(dates) != len(set(dates)):
        raise ValueError("资格检验样本日期必须唯一")
    if len(ordered) < 3:
        raise ValueError("资格检验至少需要三个时序样本")
    development_end = max(1, int(len(ordered) * 0.60))
    calibration_end = max(development_end + 1, int(len(ordered) * 0.80))
    calibration_end = min(calibration_end, len(ordered) - 1)
    development = tuple(ordered[:development_end])
    calibration = tuple(ordered[development_end:calibration_end])
    locked_test = tuple(ordered[calibration_end:])
    return QualificationSplit(
        development=development,
        calibration=calibration,
        locked_test=locked_test,
        audit=SplitAudit(
            development=_slice(development),
            calibration=_slice(calibration),
            locked_test=_slice(locked_test),
        ),
    )


def qualify_model(samples: Sequence[ForecastSample]) -> ModelQualification:
    split = split_qualification_samples(samples)
    training = mature_training_samples(
        split.development,
        split.calibration[0].signal_date,
    )
    calibration_anchors = split.calibration[::20]
    locked_anchors = split.locked_test[::20]
    if not training or not calibration_anchors or not locked_anchors:
        raise ValueError("资格检验切分无法形成有效训练和测试样本")
    model = RegularizedLogisticModel.fit(training)
    calibration_raw = tuple(model.predict(item.features) for item in calibration_anchors)
    calibration_labels = tuple(item.positive for item in calibration_anchors)
    calibration = PlattCalibrator.fit(calibration_raw, calibration_labels)
    locked_raw = tuple(model.predict(item.features) for item in locked_anchors)
    locked_calibrated = tuple(calibration.calibrate(value) for value in locked_raw)
    locked_labels = tuple(item.positive for item in locked_anchors)
    baseline = sum(item.positive for item in training) / len(training)
    raw_metrics = evaluate_probability_metrics(locked_raw, locked_labels, baseline)
    calibrated_metrics = evaluate_probability_metrics(
        locked_calibrated,
        locked_labels,
        baseline,
    )
    baseline_metrics = evaluate_probability_metrics(
        [baseline] * len(locked_labels),
        locked_labels,
        baseline,
    )
    calibration_positive = sum(calibration_labels)
    locked_positive = sum(locked_labels)
    enough = (
        len(calibration_anchors) >= 15
        and len(locked_anchors) >= 15
        and min(calibration_positive, len(calibration_anchors) - calibration_positive) >= 5
        and min(locked_positive, len(locked_anchors) - locked_positive) >= 5
    )
    reason = None if enough else "校准区或锁定测试区的独立锚点/正负标签不足"
    audit = SplitAudit(
        development=_slice(split.development, purged_count=len(split.development) - len(training)),
        calibration=_slice(split.calibration),
        locked_test=_slice(split.locked_test),
    )
    return ModelQualification(
        status="QUALIFIED" if enough and calibration.status == "FITTED" else "INSUFFICIENT_DATA" if not enough else "CONDITIONAL",
        reason=reason or calibration.reason,
        split_audit=audit,
        calibration=calibration,
        locked_test=LockedTestResult(
            baseline_probability=baseline,
            raw_probabilities=locked_raw,
            calibrated_probabilities=locked_calibrated,
            labels=locked_labels,
            signal_dates=tuple(item.signal_date for item in locked_anchors),
            raw_metrics=raw_metrics,
            calibrated_metrics=calibrated_metrics,
            baseline_metrics=baseline_metrics,
            reliability_bins=reliability_bins(locked_calibrated, locked_labels),
        ),
        calibration_raw_probabilities=calibration_raw,
        calibration_labels=calibration_labels,
    )


def mature_training_samples(
    samples: Sequence[ForecastSample],
    prediction_date: str,
) -> tuple[ForecastSample, ...]:
    return tuple(
        item
        for item in sorted(samples, key=lambda sample: sample.signal_date)
        if item.exit_date < prediction_date
    )


def evaluate_probability_metrics(
    probabilities: Sequence[float],
    labels: Sequence[bool],
    baseline_probability: float,
) -> ProbabilityMetrics:
    if len(probabilities) != len(labels) or not probabilities:
        raise ValueError("概率与标签必须等长且非空")
    bounded = [_bounded(value) for value in probabilities]
    baseline = _bounded(baseline_probability)
    count = len(bounded)
    brier = sum(
        (probability - float(label)) ** 2
        for probability, label in zip(bounded, labels)
    ) / count
    baseline_brier = sum(
        (baseline - float(label)) ** 2 for label in labels
    ) / count
    bins = reliability_bins(bounded, labels)
    return ProbabilityMetrics(
        sample_count=count,
        accuracy=sum(
            (probability >= 0.5) == label
            for probability, label in zip(bounded, labels)
        ) / count,
        brier_score=brier,
        baseline_brier_score=baseline_brier,
        brier_skill_score=(1.0 - brier / baseline_brier) if baseline_brier > 0 else 0.0,
        log_loss=sum(
            -(float(label) * math.log(probability)
              + (1.0 - float(label)) * math.log(1.0 - probability))
            for probability, label in zip(bounded, labels)
        ) / count,
        expected_calibration_error=sum(
            item.count / count * (item.calibration_error or 0.0)
            for item in bins
        ),
    )


def reliability_bins(
    probabilities: Sequence[float],
    labels: Sequence[bool],
) -> tuple[ReliabilityBin, ...]:
    if len(probabilities) != len(labels):
        raise ValueError("概率与标签数量不一致")
    edges = (0.0, 0.2, 0.4, 0.6, 0.8, 1.0)
    result: list[ReliabilityBin] = []
    for index, (lower, upper) in enumerate(zip(edges[:-1], edges[1:])):
        members = [
            (float(probability), bool(label))
            for probability, label in zip(probabilities, labels)
            if lower <= probability < upper or (index == 4 and probability == upper)
        ]
        if not members:
            result.append(ReliabilityBin(lower, upper, 0, None, None, None))
            continue
        mean_probability = sum(item[0] for item in members) / len(members)
        observed_up_rate = sum(item[1] for item in members) / len(members)
        result.append(
            ReliabilityBin(
                lower_bound=lower,
                upper_bound=upper,
                count=len(members),
                mean_probability=mean_probability,
                observed_up_rate=observed_up_rate,
                calibration_error=abs(mean_probability - observed_up_rate),
            )
        )
    return tuple(result)


def _slice(samples: Sequence[ForecastSample], *, purged_count: int = 0) -> SplitSlice:
    if not samples:
        raise ValueError("资格检验切分不得为空")
    return SplitSlice(
        start_date=samples[0].signal_date,
        end_date=samples[-1].signal_date,
        sample_count=len(samples),
        independent_sample_count=len(samples[::20]),
        positive_count=sum(item.positive for item in samples[::20]),
        purged_count=purged_count,
    )


def _bounded(value: float) -> float:
    if not math.isfinite(value):
        raise ValueError("概率必须为有限值")
    return min(1.0 - 1e-6, max(1e-6, float(value)))
