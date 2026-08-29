from __future__ import annotations

from dataclasses import dataclass
import math
from typing import Sequence

from sklearn.ensemble import HistGradientBoostingRegressor

from finscope_market_data.forecast.features import ForecastSample


MINIMUM_SAMPLE_COUNT = 150
QUANTILES = (0.10, 0.50, 0.90)
MIS_COVERAGE_RATE = 0.20


@dataclass(frozen=True)
class ReturnDistributionResult:
    status: str
    horizon_days: int
    p10: float | None
    p50: float | None
    p90: float | None
    raw_p10: float | None
    raw_p50: float | None
    raw_p90: float | None
    conformal_radius: float | None
    locked_coverage: float | None
    mean_interval_width: float | None
    locked_pinball_loss: float | None
    sample_count: int
    development_count: int
    calibration_count: int
    locked_count: int
    development_last_exit_date: str | None
    calibration_start_date: str | None
    calibration_end_date: str | None
    locked_start_date: str | None
    method: str
    reason: str | None = None


def forecast_return_distribution(
    samples: Sequence[ForecastSample],
    *,
    current_features: Sequence[float],
    horizon_days: int,
) -> ReturnDistributionResult:
    if horizon_days not in (1, 5, 20):
        raise ValueError("收益分布只支持 1、5、20 日周期")
    features = tuple(float(value) for value in current_features)
    if not features or any(not math.isfinite(value) for value in features):
        raise ValueError("当前收益分布特征必须为有限数值")
    ordered = tuple(sorted(samples, key=lambda item: item.signal_date))
    if len(ordered) < MINIMUM_SAMPLE_COUNT:
        return _insufficient(horizon_days, len(ordered), "收益分布至少需要 150 个已成熟样本")
    dimensions = {len(item.features) for item in ordered}
    if dimensions != {len(features)}:
        raise ValueError("收益分布训练与当前特征维度不一致")
    if any(
        not math.isfinite(item.net_return)
        or any(not math.isfinite(value) for value in item.features)
        for item in ordered
    ):
        raise ValueError("收益分布训练样本必须为有限数值")

    development_end = max(90, int(len(ordered) * 0.60))
    calibration_end = max(development_end + 30, int(len(ordered) * 0.80))
    calibration_end = min(calibration_end, len(ordered) - 20)
    calibration_start_date = ordered[development_end].signal_date
    locked_start_date = ordered[calibration_end].signal_date
    development = tuple(
        item for item in ordered[:development_end]
        if item.exit_date < calibration_start_date
    )
    calibration = tuple(
        item for item in ordered[development_end:calibration_end]
        if item.exit_date < locked_start_date
    )
    locked = ordered[calibration_end:]
    if len(development) < 60 or len(calibration) < 20 or len(locked) < 20:
        return _insufficient(
            horizon_days,
            len(ordered),
            "无泄漏切分后训练、校准或锁定样本不足",
        )

    models = tuple(_fit_quantile(development, quantile) for quantile in QUANTILES)
    calibration_predictions = tuple(
        _ordered_predictions(models, item.features) for item in calibration
    )
    residuals = sorted(
        max(lower - item.net_return, item.net_return - upper, 0.0)
        for item, (lower, _, upper) in zip(calibration, calibration_predictions)
    )
    radius = _finite_sample_quantile(residuals, 1.0 - MIS_COVERAGE_RATE)
    locked_predictions = tuple(
        _ordered_predictions(models, item.features) for item in locked
    )
    covered = sum(
        lower - radius <= item.net_return <= upper + radius
        for item, (lower, _, upper) in zip(locked, locked_predictions)
    )
    interval_widths = [
        upper - lower + 2.0 * radius
        for lower, _, upper in locked_predictions
    ]
    pinball = sum(
        _pinball(item.net_return, prediction, quantile)
        for item, predictions in zip(locked, locked_predictions)
        for prediction, quantile in zip(predictions, QUANTILES)
    ) / (len(locked) * len(QUANTILES))
    raw_lower, raw_median, raw_upper = _ordered_predictions(models, features)
    return ReturnDistributionResult(
        status="AVAILABLE",
        horizon_days=horizon_days,
        p10=raw_lower - radius,
        p50=raw_median,
        p90=raw_upper + radius,
        raw_p10=raw_lower,
        raw_p50=raw_median,
        raw_p90=raw_upper,
        conformal_radius=radius,
        locked_coverage=covered / len(locked),
        mean_interval_width=sum(interval_widths) / len(interval_widths),
        locked_pinball_loss=pinball,
        sample_count=len(ordered),
        development_count=len(development),
        calibration_count=len(calibration),
        locked_count=len(locked),
        development_last_exit_date=max(item.exit_date for item in development),
        calibration_start_date=calibration[0].signal_date,
        calibration_end_date=calibration[-1].signal_date,
        locked_start_date=locked[0].signal_date,
        method="HISTOGRAM_QUANTILE_CQR_V1",
    )


def _fit_quantile(
    samples: Sequence[ForecastSample], quantile: float
) -> HistGradientBoostingRegressor:
    estimator = HistGradientBoostingRegressor(
        loss="quantile",
        quantile=quantile,
        learning_rate=0.06,
        max_iter=50,
        max_leaf_nodes=9,
        max_depth=3,
        max_bins=63,
        min_samples_leaf=max(12, min(30, len(samples) // 15)),
        l2_regularization=1.5,
        early_stopping=False,
        random_state=20260830,
    )
    estimator.fit(
        [list(item.features) for item in samples],
        [item.net_return for item in samples],
    )
    return estimator


def _ordered_predictions(
    models: Sequence[HistGradientBoostingRegressor],
    features: Sequence[float],
) -> tuple[float, float, float]:
    values = sorted(float(model.predict([list(features)])[0]) for model in models)
    return values[0], values[1], values[2]


def _finite_sample_quantile(values: Sequence[float], probability: float) -> float:
    if not values:
        raise ValueError("conformal 校准残差不能为空")
    rank = math.ceil((len(values) + 1) * probability)
    return float(values[min(len(values), max(1, rank)) - 1])


def _pinball(actual: float, predicted: float, quantile: float) -> float:
    residual = actual - predicted
    return max(quantile * residual, (quantile - 1.0) * residual)


def _insufficient(
    horizon_days: int, sample_count: int, reason: str
) -> ReturnDistributionResult:
    return ReturnDistributionResult(
        status="INSUFFICIENT_DATA",
        horizon_days=horizon_days,
        p10=None,
        p50=None,
        p90=None,
        raw_p10=None,
        raw_p50=None,
        raw_p90=None,
        conformal_radius=None,
        locked_coverage=None,
        mean_interval_width=None,
        locked_pinball_loss=None,
        sample_count=sample_count,
        development_count=0,
        calibration_count=0,
        locked_count=0,
        development_last_exit_date=None,
        calibration_start_date=None,
        calibration_end_date=None,
        locked_start_date=None,
        method="HISTOGRAM_QUANTILE_CQR_V1",
        reason=reason,
    )
