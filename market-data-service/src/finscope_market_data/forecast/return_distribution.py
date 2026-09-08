from __future__ import annotations

from dataclasses import dataclass, replace
import math
from typing import Sequence

from sklearn.ensemble import HistGradientBoostingRegressor

from finscope_market_data.forecast.features import ForecastSample
from finscope_market_data.forecast.production_fit import recent_split


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
    production_applied: bool = False
    production_training_through: str | None = None
    production_calibration_through: str | None = None
    production_scale: float | None = None
    historical_conformal_radius: float | None = None


def forecast_return_distribution(
    samples: Sequence[ForecastSample],
    *,
    current_features: Sequence[float],
    horizon_days: int,
    cutoff: str | None = None,
) -> ReturnDistributionResult:
    if horizon_days not in (1, 5, 20):
        raise ValueError("收益分布只支持 1、5、20 日周期")
    features = tuple(float(value) for value in current_features)
    if not features or any(not math.isfinite(value) for value in features):
        raise ValueError("当前收益分布特征必须为有限数值")
    ordered = tuple(sorted((item for item in samples if cutoff is None or item.exit_date <= cutoff), key=lambda item: item.signal_date))
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
    historical_radius = radius
    production = {}
    try:
        training, recent_calibration = recent_split(
            ordered, cutoff=cutoff or max(item.exit_date for item in ordered), horizon_days=horizon_days,
        )
        serving_models, serving_radius = _recent_models(training, recent_calibration, horizon_days)
        scale = _return_scale(features, horizon_days)
        validation = locked[::horizon_days][-30:]
        passed = False
        if len(validation) >= 20:
            from datetime import date, timedelta
            before = (date.fromisoformat(validation[0].signal_date) - timedelta(days=1)).isoformat()
            gate_training, gate_calibration = recent_split(ordered, cutoff=before, horizon_days=horizon_days)
            gate_models, gate_radius = _recent_models(gate_training, gate_calibration, horizon_days)
            old_errors, new_errors, old_scores, new_scores = [], [], [], []
            for item in validation:
                old_lower, old_median, old_upper = _ordered_predictions(models, item.features)
                item_scale = _return_scale(item.features, horizon_days)
                new_lower, new_median, new_upper = (value * item_scale for value in _ordered_predictions(gate_models, item.features))
                old_errors.append(abs(old_median - item.net_return))
                new_errors.append(abs(new_median - item.net_return))
                old_scores.append(_interval_score(old_lower - historical_radius, old_upper + historical_radius, item.net_return))
                new_scores.append(_interval_score(new_lower - gate_radius * item_scale, new_upper + gate_radius * item_scale, item.net_return))
            passed = (
                sum(new_errors) < sum(old_errors)
                and sum(new_errors) < sum(abs(item.net_return) for item in validation)
                and sum(new_scores) < sum(old_scores)
            )
        if passed:
            raw_lower, raw_median, raw_upper = (value * scale for value in _ordered_predictions(serving_models, features))
            radius = serving_radius * scale
        production = dict(production_applied=passed, production_training_through=training[-1].exit_date,
                          production_calibration_through=recent_calibration[-1].exit_date, production_scale=scale,
                          reason="近期收益模型通过原模型、零收益幅度基准与区间评分比较" if passed else "近期收益模型未通过独立比较，保留原收益分布")
    except ValueError:
        production = dict(reason="近期独立样本不足，当前收益分布保留历史模型")
    return ReturnDistributionResult(
        **production, historical_conformal_radius=historical_radius,
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
        method="RECENT_VOLATILITY_CQR_V2",
    )


def _recent_models(training, calibration, horizon_days):
    scaled = tuple(replace(item, net_return=item.net_return / _return_scale(item.features, horizon_days)) for item in training)
    models = tuple(_fit_quantile(scaled, quantile) for quantile in QUANTILES)
    residuals = []
    for item in calibration:
        lower, _, upper = _ordered_predictions(models, item.features)
        actual = item.net_return / _return_scale(item.features, horizon_days)
        residuals.append(max(lower - actual, actual - upper, 0.0))
    return models, _finite_sample_quantile(sorted(residuals), 1.0 - MIS_COVERAGE_RATE)


def _interval_score(lower, upper, actual):
    # Proper interval score penalizes both misses and excessive width.
    return upper - lower + 2 / MIS_COVERAGE_RATE * (max(lower - actual, 0) + max(actual - upper, 0))


def _return_scale(features: Sequence[float], horizon_days: int) -> float:
    # Feature 5 is signal-day historical daily volatility; never use future realised volatility.
    return max(abs(features[5]), .005) * math.sqrt(horizon_days) if len(features) > 5 else 1.0


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
        method="RECENT_VOLATILITY_CQR_V2",
        reason=reason,
    )
