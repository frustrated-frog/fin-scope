"""Bounded local model selection with separate fit, selection and calibration-check periods."""
from __future__ import annotations

from dataclasses import dataclass, replace
import math
from typing import Sequence

import numpy as np
from lightgbm import LGBMClassifier
from sklearn.linear_model import Ridge

from finscope_market_data.forecast.calibration import CalibrationResult, PlattCalibrator
from finscope_market_data.forecast.features import ForecastSample
from finscope_market_data.forecast.model_competition import fit_model

MODEL_VERSION = 'local-prediction-v3'


class LocalTree:
    def __init__(self, training):
        labels = [row.positive for row in training]
        self.prior = float(np.mean(labels))
        self.model = None
        if len(set(labels)) > 1:
            self.model = LGBMClassifier(n_estimators=80, num_leaves=7, max_depth=3,
                learning_rate=.03, min_child_samples=30, reg_lambda=5,
                random_state=42, n_jobs=1, verbosity=-1, deterministic=True, force_col_wise=True)
            self.model.fit(np.asarray([row.features for row in training]), labels)

    def predict(self, features):
        if self.model is None:
            return self.prior
        return float(self.model.booster_.predict(np.asarray([features]), num_threads=1)[0])


@dataclass(frozen=True)
class LocalFit:
    code: str
    model: object
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
    audit: dict

    def predict(self, features):
        probability = self.calibration.calibrate(self.model.predict(features))
        expected = float(self.regression.predict([(np.asarray(features) - self.means) / self.scales])[0])
        return probability, expected, expected - self.radius, expected + self.radius


def _metrics(probabilities, labels):
    p, y = np.asarray(probabilities), np.asarray(labels, dtype=bool)
    hit = (p >= .5) == y
    ba = float((hit[y].mean() + hit[~y].mean()) / 2) if y.any() and (~y).any() else None
    return dict(accuracy=float(hit.mean()), brier=float(np.mean((p-y)**2)), balancedAccuracy=ba)


def _dominates(challenger, baseline):
    return (challenger['accuracy'] > baseline['accuracy']
            and challenger['brier'] <= baseline['brier']
            and challenger['balancedAccuracy'] is not None
            and baseline['balancedAccuracy'] is not None
            and challenger['balancedAccuracy'] >= baseline['balancedAccuracy'])


def fit_local(samples: Sequence[ForecastSample], cutoff: str) -> LocalFit:
    matured = sorted((row for row in samples if row.exit_date < cutoff), key=lambda row: row.signal_date)
    if len(matured) < 215:
        raise ValueError('本地训练需要至少 215 个成熟样本')
    calibration = matured[-60:]
    before_calibration = [row for row in matured[:-60] if row.exit_date < calibration[0].signal_date]
    selection = before_calibration[-30:]
    training = [row for row in before_calibration[:-30] if row.exit_date < selection[0].signal_date][-504:]
    if len(training) < 120:
        raise ValueError('清除跨段标签后训练样本不足')
    models = {code: fit_model(code, training) for code in ('LOGISTIC', 'HISTOGRAM_GB')}
    models['LIGHTGBM'] = LocalTree(training)
    labels = [row.positive for row in selection]
    scores, periods = {}, {}
    for code, model in models.items():
        probabilities = [model.predict(row.features) for row in selection]
        scores[code] = _metrics(probabilities, labels)
        periods[code] = [_metrics(probabilities[start:start+10], labels[start:start+10]) for start in (0, 10, 20)]
    # A challenger must improve direction without degrading probability quality across periods.
    eligible = ['LOGISTIC'] + [code for code in ('HISTOGRAM_GB', 'LIGHTGBM')
        if _dominates(scores[code], scores['LOGISTIC'])
        and sum(_dominates(c, b) for c, b in zip(periods[code], periods['LOGISTIC'])) >= 2]
    code = min(eligible, key=lambda key: (-scores[key]['accuracy'], scores[key]['brier'], key))
    model = models[code]
    calibration_check = calibration[-30:]
    calibration_fit = [row for row in calibration[:-30] if row.exit_date < calibration_check[0].signal_date]
    calibrator = PlattCalibrator.fit([model.predict(row.features) for row in calibration_fit],
                                     [row.positive for row in calibration_fit])
    raw = [model.predict(row.features) for row in calibration_check]
    adjusted = [calibrator.calibrate(p) for p in raw]
    raw_score = _metrics(raw, [row.positive for row in calibration_check])
    adjusted_score = _metrics(adjusted, [row.positive for row in calibration_check])
    use_calibration = (adjusted_score['brier'] < raw_score['brier']
                       and adjusted_score['accuracy'] >= raw_score['accuracy']
                       and adjusted_score['balancedAccuracy'] is not None
                       and raw_score['balancedAccuracy'] is not None
                       and adjusted_score['balancedAccuracy'] >= raw_score['balancedAccuracy'])
    if not use_calibration:
        calibrator = replace(calibrator, status='NOT_FITTED', reason='独立检查未证明校准改善，保留原始概率')
    matrix = np.asarray([row.features for row in training])
    means, scales = matrix.mean(axis=0), matrix.std(axis=0)
    scales[scales < 1e-9] = 1
    regression = Ridge(alpha=10).fit((matrix-means)/scales, [row.net_return for row in training])
    estimates = regression.predict((np.asarray([row.features for row in calibration])-means)/scales)
    errors = sorted(abs(row.net_return-value) for row, value in zip(calibration, estimates))
    radius = float(errors[min(len(errors)-1, math.ceil((len(errors)+1)*.8)-1)])
    audit = dict(modelVersion=MODEL_VERSION, selectionStart=selection[0].signal_date,
                 selectionThrough=selection[-1].exit_date, candidates=scores, periods=periods,
                 selected=code, calibrationFitThrough=calibration_fit[-1].exit_date,
                 calibrationCheckStart=calibration_check[0].signal_date,
                 calibrationCheckThrough=calibration_check[-1].exit_date,
                 calibrationApplied=use_calibration, rawCalibrationCheck=raw_score,
                 adjustedCalibrationCheck=adjusted_score)
    return LocalFit(code, model, calibrator, float(np.mean([row.positive for row in training])),
                    regression, means, scales, radius, training[-1].exit_date,
                    calibration[0].signal_date, calibration[-1].exit_date,
                    len(training), len(calibration), audit)
