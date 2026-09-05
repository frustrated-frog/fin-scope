"""Bounded rolling next-close prediction, separate from executable holding returns.

Rolling refits/purged segments follow Qlib's workflow design. Residual split
conformal intervals are empirically audited, not guaranteed for dependent returns.
"""
from dataclasses import dataclass
from datetime import date, datetime, time, timedelta
import hashlib
import json
import math
from typing import Sequence
from zoneinfo import ZoneInfo

import numpy as np
from sklearn.linear_model import Ridge

from finscope_market_data.models import DailyBar
from finscope_market_data.forecast.calibration import CalibrationResult, PlattCalibrator
from finscope_market_data.forecast.context import AlignedForecastContext
from finscope_market_data.forecast.features import ForecastSample, _features, _validated_bars
from finscope_market_data.forecast.model_competition import ProbabilityModel, fit_model
from finscope_market_data.forecast.next_session_types import NextSessionPrediction
from finscope_market_data.forecast.trading_calendar import next_session

TRAIN_WINDOW = 504
CALIBRATION_WINDOW = 60
TEST_WINDOW = 60
REFIT_INTERVAL = 20
MINIMUM_SAMPLES = 300


@dataclass(frozen=True)
class RollingFit:
    code: str
    model: ProbabilityModel
    calibration: CalibrationResult
    baseline: float
    regression: Ridge
    means: np.ndarray
    scales: np.ndarray
    radius: float
    training_through: str
    calibration_start: str
    calibration_through: str
    training_count: int
    calibration_count: int

    def predict(self, features: Sequence[float]) -> tuple[float, float, float, float]:
        probability = self.calibration.calibrate(self.model.predict(features))
        expected = float(self.regression.predict([(np.asarray(features) - self.means) / self.scales])[0])
        return probability, expected, expected - self.radius, expected + self.radius


def build_close_samples(bars: Sequence[DailyBar], context: AlignedForecastContext | None = None) -> list[ForecastSample]:
    ordered = _validated_bars(bars)
    return [ForecastSample(
        signal_date=ordered[i].trade_date,
        entry_date=ordered[i].trade_date,
        exit_date=ordered[i + 1].trade_date,
        features=_features(ordered, i, context),
        net_return=ordered[i + 1].close / ordered[i].close - 1.0,
    ) for i in range(60, len(ordered) - 1)]


def _fit_at(samples: Sequence[ForecastSample], cutoff: str) -> RollingFit:
    matured = [item for item in samples if item.exit_date < cutoff][-TRAIN_WINDOW - CALIBRATION_WINDOW - 1:]
    calibration = matured[-CALIBRATION_WINDOW:]
    training = [item for item in matured[:-CALIBRATION_WINDOW]
                if item.exit_date < calibration[0].signal_date][-TRAIN_WINDOW:]
    if len(training) < 120 or len(calibration) < CALIBRATION_WINDOW:
        raise ValueError("滚动训练或独立校准样本不足")
    candidates = []
    for code in ("LOGISTIC", "HISTOGRAM_GB"):
        model = fit_model(code, training)
        raw = [model.predict(item.features) for item in calibration]
        # Selection observes only pre-test labels. The following rolling block remains untouched.
        score = sum((p - float(item.positive)) ** 2 for p, item in zip(raw, calibration)) / len(raw)
        calibrator = PlattCalibrator.fit(raw, [item.positive for item in calibration])
        candidates.append((score, code, model, calibrator))
    _, code, model, calibrator = min(candidates, key=lambda item: (item[0], item[1]))
    matrix = np.asarray([item.features for item in training])
    means, scales = matrix.mean(axis=0), matrix.std(axis=0)
    scales[scales < 1e-9] = 1.0
    regression = Ridge(alpha=10.0).fit((matrix - means) / scales, [item.net_return for item in training])
    estimates = regression.predict((np.asarray([item.features for item in calibration]) - means) / scales)
    residuals = sorted(abs(item.net_return - estimate) for item, estimate in zip(calibration, estimates))
    radius = float(residuals[min(len(residuals) - 1, math.ceil((len(residuals) + 1) * .8) - 1)])
    return RollingFit(code, model, calibrator, sum(item.positive for item in training) / len(training),
                      regression, means, scales, radius, training[-1].exit_date,
                      calibration[0].signal_date, calibration[-1].exit_date, len(training), len(calibration))


def build_next_session_forecast(bars: Sequence[DailyBar], *, context: AlignedForecastContext | None = None,
                                now: datetime | None = None) -> NextSessionPrediction:
    current = now or datetime.now(ZoneInfo("Asia/Shanghai"))
    if current.tzinfo is not None:
        current = current.astimezone(ZoneInfo("Asia/Shanghai")).replace(tzinfo=None)
    ordered = _validated_bars(bars)
    if not ordered:
        raise ValueError("次日预测需要真实日线")
    as_of = date.fromisoformat(ordered[-1].trade_date)
    target = next_session(as_of)
    payload = [(b.trade_date, b.open, b.high, b.low, b.close, b.volume, b.amount, b.adjustment) for b in ordered]
    fingerprint = hashlib.sha256(json.dumps(["next-session-rolling-v1", payload], allow_nan=False).encode()).hexdigest()
    base = dict(as_of_date=as_of.isoformat(), target_date=target.isoformat() if target else None,
                generated_at=current.isoformat(), last_close=ordered[-1].close, data_fingerprint=fingerprint)
    if as_of > current.date() or (as_of == current.date() and current.time() < time(15, 10)):
        return NextSessionPrediction(status="BEFORE_CLOSE", **base, warnings=["当日收盘数据尚未完成，15:10 后再生成"])
    if target is None:
        return NextSessionPrediction(status="CALENDAR_UNAVAILABLE", **base, warnings=["目标年份交易日历尚未核验，不猜测交易日期"])
    if target <= current.date():
        return NextSessionPrediction(status="STALE_DATA", **base, warnings=["行情截止日过旧，禁止事后生成目标日预测"])
    if any(b.adjustment != "QFQ" for b in ordered):
        return NextSessionPrediction(status="INSUFFICIENT_DATA", **base, warnings=["次日模型需要一致的前复权日线"])
    samples = build_close_samples(ordered, context)[-1000:]
    if len(samples) < MINIMUM_SAMPLES:
        return NextSessionPrediction(status="INSUFFICIENT_DATA", **base, warnings=["次日模型至少需要 300 个已成熟收盘收益样本"])
    base["data_fingerprint"] = hashlib.sha256(json.dumps(
        [fingerprint, [item.features for item in samples], _features(ordered, len(ordered) - 1, context)],
        allow_nan=False,
    ).encode()).hexdigest()
    observations = []
    fitted = None
    for index in range(len(samples) - TEST_WINDOW, len(samples)):
        sample = samples[index]
        if fitted is None or len(observations) % REFIT_INTERVAL == 0:
            fitted = _fit_at(samples[:index], sample.signal_date)
        probability, _, lower, upper = fitted.predict(sample.features)
        observations.append((probability, float(sample.positive), fitted.baseline,
                             lower <= sample.net_return <= upper))
    current_fit = _fit_at(samples, (as_of + timedelta(days=1)).isoformat())
    probability, expected, lower, upper = current_fit.predict(_features(ordered, len(ordered) - 1, context))
    count = len(observations)
    brier = sum((p - label) ** 2 for p, label, _, _ in observations) / count
    baseline = sum((prior - label) ** 2 for _, label, prior, _ in observations) / count
    coverage = sum(covered for _, _, _, covered in observations) / count
    ready = brier < baseline and .65 <= coverage <= .95
    return NextSessionPrediction(
        **base, status="READY" if ready else "WATCH", up_probability=probability,
        expected_return=expected, lower_return=lower, upper_return=upper,
        decision=("UP" if probability >= .55 else "DOWN" if probability <= .45 else "ABSTAIN") if ready else "ABSTAIN",
        model_code=current_fit.code, training_through=current_fit.training_through,
        calibration_through=current_fit.calibration_through, training_sample_count=current_fit.training_count,
        calibration_sample_count=current_fit.calibration_count, validation_sample_count=count,
        accuracy=sum((p >= .5) == bool(label) for p, label, _, _ in observations) / count,
        brier_score=brier, baseline_brier_score=baseline, interval_coverage=coverage,
        warnings=["预测次日收盘相对本次复权收盘的涨跌；不是可成交收益，不含交易费用",
                  "60 个滚动测试样本只提供初步证据，80% 残差校准区间不保证未来覆盖率",
                  *([] if ready else ["滚动概率或区间尚未形成稳定优势，保留概率供观察，暂不作方向判断"])],
    )
