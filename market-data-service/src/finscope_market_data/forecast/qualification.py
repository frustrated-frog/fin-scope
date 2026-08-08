from __future__ import annotations

from dataclasses import dataclass
from typing import Sequence

from finscope_market_data.forecast.features import ForecastSample


@dataclass(frozen=True)
class SplitSlice:
    start_date: str
    end_date: str
    sample_count: int


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


def mature_training_samples(
    samples: Sequence[ForecastSample],
    prediction_date: str,
) -> tuple[ForecastSample, ...]:
    return tuple(
        item
        for item in sorted(samples, key=lambda sample: sample.signal_date)
        if item.exit_date < prediction_date
    )


def _slice(samples: Sequence[ForecastSample]) -> SplitSlice:
    if not samples:
        raise ValueError("资格检验切分不得为空")
    return SplitSlice(
        start_date=samples[0].signal_date,
        end_date=samples[-1].signal_date,
        sample_count=len(samples),
    )
