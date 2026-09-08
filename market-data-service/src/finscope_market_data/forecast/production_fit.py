"""Recent serving fits are separate from immutable historical qualification evidence."""
from dataclasses import dataclass
from typing import Sequence

from finscope_market_data.forecast.calibration import CalibrationResult, PlattCalibrator
from finscope_market_data.forecast.features import ForecastSample
from finscope_market_data.forecast.model_competition import fit_model
from finscope_market_data.forecast.logistic import RegularizedLogisticModel


def recent_split(samples: Sequence[ForecastSample], *, cutoff: str, horizon_days: int):
    if horizon_days not in (1, 5, 20):
        raise ValueError('仅支持 1、5、20 日周期')
    matured = sorted((item for item in samples if item.exit_date <= cutoff), key=lambda item: item.signal_date)
    span = min(60 * horizon_days, max(60, len(matured) // 3))
    calibration = matured[-span:][::-horizon_days][::-1]
    if len(calibration) < 15:
        raise ValueError('近期独立校准样本不足')
    training = [item for item in matured if item.exit_date < calibration[0].signal_date][-504:]
    if len(training) < 120:
        raise ValueError('近期独立训练样本不足')
    return tuple(training), tuple(calibration)


@dataclass(frozen=True)
class ProductionFit:
    model: object
    explanation_model: object
    calibration: CalibrationResult
    calibration_raw_probabilities: tuple[float, ...]
    calibration_labels: tuple[bool, ...]
    training_through: str
    calibration_start: str
    calibration_through: str
    training_count: int
    calibration_count: int

    def predict(self, features):
        raw = self.model.predict(features)
        return raw, self.calibration.calibrate(raw)


def fit_production_model(samples: Sequence[ForecastSample], *, cutoff: str,
                         horizon_days: int, model_code: str) -> ProductionFit:
    training, calibration = recent_split(samples, cutoff=cutoff, horizon_days=horizon_days)
    model = fit_model(model_code, training)
    raw = tuple(model.predict(item.features) for item in calibration)
    labels = tuple(item.positive for item in calibration)
    calibrator = PlattCalibrator.fit(raw, labels)
    explanation = model if isinstance(model, RegularizedLogisticModel) else RegularizedLogisticModel.fit(training)
    return ProductionFit(model, explanation, calibrator, raw, labels,
                         training[-1].exit_date, calibration[0].signal_date, calibration[-1].exit_date,
                         len(training), len(calibration))


def evaluate_recent_candidate(samples, *, cutoff, horizon_days, model_code, baseline):
    """Selection evidence only: neither fit nor calibrate on the comparison window."""
    from datetime import date, timedelta
    validation = [item for item in samples if item.exit_date <= cutoff
                  and item.signal_date >= baseline.split_audit.locked_test.start_date][::horizon_days][-30:]
    if len(validation) < 20:
        return dict(passed=False, sampleCount=len(validation), reason='近期独立比较样本不足 20 个')
    before = (date.fromisoformat(validation[0].signal_date) - timedelta(days=1)).isoformat()
    try:
        candidate = fit_production_model(samples, cutoff=before, horizon_days=horizon_days, model_code=model_code)
    except ValueError:
        return dict(passed=False, sampleCount=len(validation), reason='比较窗口前的独立训练或校准不足')
    old = [baseline.calibration.calibrate(baseline.model.predict(item.features)) for item in validation]
    new = [candidate.predict(item.features)[1] for item in validation]
    labels = [item.positive for item in validation]
    old_brier = sum((p - y) ** 2 for p, y in zip(old, labels)) / len(labels)
    new_brier = sum((p - y) ** 2 for p, y in zip(new, labels)) / len(labels)
    old_correct = sum((p >= .5) == y for p, y in zip(old, labels))
    new_correct = sum((p >= .5) == y for p, y in zip(new, labels))
    passed = new_brier < old_brier and new_correct >= old_correct
    return dict(passed=passed, sampleCount=len(labels), baselineBrier=old_brier, candidateBrier=new_brier,
                baselineCorrect=old_correct, candidateCorrect=new_correct, startDate=validation[0].signal_date,
                endDate=validation[-1].exit_date, candidateCalibrationThrough=candidate.calibration_through,
                evidenceKind='ROLLING_SELECTION', reason='近期概率质量改善且方向命中未退步' if passed else '近期候选未同时胜过原模型的概率质量与方向命中')
