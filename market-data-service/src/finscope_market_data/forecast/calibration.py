from __future__ import annotations

from dataclasses import dataclass
import math
from typing import Sequence

import numpy as np


MIN_CALIBRATION_SAMPLES = 15
MIN_CLASS_SAMPLES = 5
PROBABILITY_EPSILON = 1e-6
MAX_LOG_LOSS_DEGRADATION = 0.005


@dataclass(frozen=True)
class CalibrationResult:
    status: str
    slope: float
    intercept: float
    sample_count: int
    positive_count: int
    raw_log_loss: float
    calibrated_log_loss: float
    reason: str | None = None

    def calibrate(self, raw_probability: float) -> float:
        probability = _bounded(raw_probability)
        if self.status != "FITTED":
            return probability
        logit = math.log(probability / (1.0 - probability))
        calibrated = _sigmoid(self.slope * logit + self.intercept)
        return _bounded(calibrated)


class PlattCalibrator:
    @staticmethod
    def fit(
        raw_probabilities: Sequence[float],
        labels: Sequence[bool],
    ) -> CalibrationResult:
        if len(raw_probabilities) != len(labels):
            raise ValueError("校准概率与标签数量不一致")
        probabilities = [_bounded(value) for value in raw_probabilities]
        targets = np.asarray(labels, dtype=np.float64)
        sample_count = len(probabilities)
        positive_count = int(targets.sum())
        raw_loss = _log_loss(probabilities, labels) if probabilities else 0.0
        if sample_count < MIN_CALIBRATION_SAMPLES:
            return _fallback(sample_count, positive_count, raw_loss, "校准独立样本少于 15 个")
        if min(positive_count, sample_count - positive_count) < MIN_CLASS_SAMPLES:
            return _fallback(sample_count, positive_count, raw_loss, "校准区正负标签均需至少 5 个")

        logits = np.asarray(
            [math.log(value / (1.0 - value)) for value in probabilities],
            dtype=np.float64,
        )
        design = np.column_stack((logits, np.ones(sample_count, dtype=np.float64)))
        parameters = np.asarray([1.0, 0.0], dtype=np.float64)
        ridge = np.asarray([0.01, 0.002], dtype=np.float64)
        try:
            for _ in range(80):
                fitted = np.asarray(_sigmoid_array(design @ parameters))
                weights = np.clip(fitted * (1.0 - fitted), 1e-6, None)
                gradient = design.T @ (fitted - targets) / sample_count + ridge * parameters
                hessian = (design.T * weights) @ design / sample_count + np.diag(ridge)
                update = np.linalg.solve(hessian, gradient)
                current_objective = _objective(design, targets, parameters, ridge)
                step = 1.0
                while step >= 1e-6:
                    candidate = parameters - step * update
                    if _objective(design, targets, candidate, ridge) <= current_objective:
                        parameters = candidate
                        break
                    step *= 0.5
                if step < 1e-6 or float(np.max(np.abs(step * update))) < 1e-9:
                    break
        except (FloatingPointError, np.linalg.LinAlgError):
            return _fallback(sample_count, positive_count, raw_loss, "概率校准数值求解失败")

        if not np.all(np.isfinite(parameters)):
            return _fallback(sample_count, positive_count, raw_loss, "概率校准产生非有限参数")
        calibrated = [
            _bounded(float(value))
            for value in _sigmoid_array(design @ parameters)
        ]
        calibrated_loss = _log_loss(calibrated, labels)
        if calibrated_loss > raw_loss + MAX_LOG_LOSS_DEGRADATION:
            return _fallback(sample_count, positive_count, raw_loss, "校准区 Log Loss 未通过退化门槛")
        return CalibrationResult(
            status="FITTED",
            slope=float(parameters[0]),
            intercept=float(parameters[1]),
            sample_count=sample_count,
            positive_count=positive_count,
            raw_log_loss=raw_loss,
            calibrated_log_loss=calibrated_loss,
        )


def log_loss(probabilities: Sequence[float], labels: Sequence[bool]) -> float:
    if len(probabilities) != len(labels) or not probabilities:
        raise ValueError("概率与标签必须等长且非空")
    return _log_loss(probabilities, labels)


def _fallback(
    sample_count: int,
    positive_count: int,
    raw_loss: float,
    reason: str,
) -> CalibrationResult:
    return CalibrationResult(
        status="NOT_FITTED",
        slope=1.0,
        intercept=0.0,
        sample_count=sample_count,
        positive_count=positive_count,
        raw_log_loss=raw_loss,
        calibrated_log_loss=raw_loss,
        reason=reason,
    )


def _log_loss(probabilities: Sequence[float], labels: Sequence[bool]) -> float:
    losses = [
        -(float(label) * math.log(_bounded(probability))
          + (1.0 - float(label)) * math.log(1.0 - _bounded(probability)))
        for probability, label in zip(probabilities, labels)
    ]
    return sum(losses) / len(losses)


def _bounded(value: float) -> float:
    if not math.isfinite(value):
        raise ValueError("概率必须为有限值")
    return min(1.0 - PROBABILITY_EPSILON, max(PROBABILITY_EPSILON, float(value)))


def _sigmoid(value: float) -> float:
    return 1.0 / (1.0 + math.exp(-max(-30.0, min(30.0, value))))


def _sigmoid_array(values: np.ndarray) -> np.ndarray:
    return 1.0 / (1.0 + np.exp(-np.clip(values, -30.0, 30.0)))


def _objective(
    design: np.ndarray,
    targets: np.ndarray,
    parameters: np.ndarray,
    ridge: np.ndarray,
) -> float:
    probabilities = np.clip(_sigmoid_array(design @ parameters), PROBABILITY_EPSILON, 1.0 - PROBABILITY_EPSILON)
    loss = -np.mean(targets * np.log(probabilities) + (1.0 - targets) * np.log(1.0 - probabilities))
    return float(loss + 0.5 * np.sum(ridge * parameters * parameters))
