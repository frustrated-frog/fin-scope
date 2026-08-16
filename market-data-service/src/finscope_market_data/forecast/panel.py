from __future__ import annotations

from dataclasses import asdict, dataclass
from datetime import datetime
import hashlib
import json
import math
from pathlib import Path
from typing import Mapping, Sequence

import numpy as np

from finscope_market_data.forecast.calibration import CalibrationResult, PlattCalibrator
from finscope_market_data.forecast.features import ForecastSample
from finscope_market_data.forecast.logistic import RegularizedLogisticModel
from finscope_market_data.forecast.qualification import (
    ProbabilityMetrics,
    evaluate_probability_metrics,
)


PANEL_SCHEMA_VERSION = "panel-probability-v1"
MAX_BLEND_WEIGHT = 0.45


@dataclass(frozen=True)
class PanelTargetMetrics:
    sample_count: int
    brier_score: float
    log_loss: float
    expected_calibration_error: float


@dataclass(frozen=True)
class PanelArtifact:
    schema_version: str
    mode: str
    horizon_days: int
    published_at: str
    trained_through: str
    universe_size: int
    sample_count: int
    feature_codes: tuple[str, ...]
    development_last_exit_date: str
    calibration_start_date: str
    calibration_end_date: str
    locked_start_date: str
    model_means: tuple[float, ...]
    model_scales: tuple[float, ...]
    model_weights: tuple[float, ...]
    calibration: CalibrationResult
    locked_metrics: ProbabilityMetrics
    target_metrics: dict[str, PanelTargetMetrics]
    current_features_by_code: dict[str, tuple[float, ...]]
    data_fingerprint: str

    def model(self) -> RegularizedLogisticModel:
        return RegularizedLogisticModel(
            means=np.asarray(self.model_means, dtype=np.float64),
            scales=np.asarray(self.model_scales, dtype=np.float64),
            weights=np.asarray(self.model_weights, dtype=np.float64),
        )

    def to_dict(self) -> dict[str, object]:
        payload = asdict(self)
        payload["feature_codes"] = list(self.feature_codes)
        return payload

    @classmethod
    def from_dict(cls, payload: Mapping[str, object]) -> "PanelArtifact":
        if payload.get("schema_version") != PANEL_SCHEMA_VERSION:
            raise ValueError("面板模型协议版本不受支持")
        calibration = CalibrationResult(**dict(payload["calibration"]))
        locked_metrics = ProbabilityMetrics(**dict(payload["locked_metrics"]))
        target_metrics = {
            str(code): PanelTargetMetrics(**dict(value))
            for code, value in dict(payload["target_metrics"]).items()
        }
        artifact = cls(
            schema_version=str(payload["schema_version"]),
            mode=str(payload["mode"]),
            horizon_days=int(payload["horizon_days"]),
            published_at=str(payload["published_at"]),
            trained_through=str(payload["trained_through"]),
            universe_size=int(payload["universe_size"]),
            sample_count=int(payload["sample_count"]),
            feature_codes=tuple(str(item) for item in payload["feature_codes"]),
            development_last_exit_date=str(payload["development_last_exit_date"]),
            calibration_start_date=str(payload["calibration_start_date"]),
            calibration_end_date=str(payload["calibration_end_date"]),
            locked_start_date=str(payload["locked_start_date"]),
            model_means=tuple(float(item) for item in payload["model_means"]),
            model_scales=tuple(float(item) for item in payload["model_scales"]),
            model_weights=tuple(float(item) for item in payload["model_weights"]),
            calibration=calibration,
            locked_metrics=locked_metrics,
            target_metrics=target_metrics,
            current_features_by_code={
                str(code): tuple(float(item) for item in values)
                for code, values in dict(
                    payload.get("current_features_by_code", {})
                ).items()
            },
            data_fingerprint=str(payload["data_fingerprint"]),
        )
        _validate_artifact(artifact)
        return artifact


@dataclass(frozen=True)
class PanelAssessment:
    status: str
    mode: str
    artifact_version: str
    published_at: str
    artifact_age_days: int
    universe_size: int
    sample_count: int
    feature_coverage: float
    feature_distance: float
    drift_status: str
    individual_probability: float
    panel_probability: float
    final_probability: float
    blend_weight: float
    target_locked_sample_count: int
    locked_brier_delta: float | None
    locked_log_loss_delta: float | None
    panel_brier_score: float
    panel_log_loss: float
    panel_ece: float
    fallback_reason: str | None
    evidence: tuple[str, ...]


class PanelArtifactStore:
    def __init__(self, directory: str | Path) -> None:
        self.directory = Path(directory)

    def save(self, artifact: PanelArtifact) -> None:
        _validate_artifact(artifact)
        self.directory.mkdir(parents=True, exist_ok=True)
        destination = self._path(artifact.horizon_days, artifact.mode)
        temporary = destination.with_suffix(".tmp")
        temporary.write_text(
            json.dumps(
                artifact.to_dict(),
                ensure_ascii=False,
                sort_keys=True,
                allow_nan=False,
            ),
            encoding="utf-8",
        )
        temporary.replace(destination)

    def load(
        self,
        horizon_days: int,
        *,
        mode: str = "PANEL_CORE",
    ) -> PanelArtifact | None:
        path = self._path(horizon_days, mode)
        if not path.exists():
            return None
        try:
            return PanelArtifact.from_dict(json.loads(path.read_text(encoding="utf-8")))
        except (OSError, TypeError, ValueError, KeyError, json.JSONDecodeError):
            return None

    def _path(self, horizon_days: int, mode: str = "PANEL_CORE") -> Path:
        suffix = "-full" if mode == "PANEL_FULL" else ""
        return self.directory / f"panel-model-{horizon_days}{suffix}.json"


def train_panel_artifact(
    samples_by_code: Mapping[str, Sequence[ForecastSample]],
    *,
    horizon_days: int,
    minimum_instruments: int = 20,
    minimum_samples: int = 3000,
    published_at: str | None = None,
    mode: str = "PANEL_CORE",
    feature_codes: Sequence[str] | None = None,
    current_features_by_code: Mapping[str, Sequence[float]] | None = None,
) -> PanelArtifact:
    bounded = {
        code: tuple(sorted(values, key=lambda item: item.signal_date)[-1500:])
        for code, values in sorted(samples_by_code.items())
        if values
    }
    stride = min(5, horizon_days) if sum(map(len, bounded.values())) > 60_000 else 1
    usable = {
        code: values[::stride]
        for code, values in bounded.items()
    }
    if len(usable) < minimum_instruments:
        raise ValueError(f"面板训练至少需要 {minimum_instruments} 只股票")
    dimensions = {len(item.features) for values in usable.values() for item in values}
    if len(dimensions) != 1 or next(iter(dimensions)) == 0:
        raise ValueError("面板训练特征维度不一致")
    all_dates = sorted({item.signal_date for values in usable.values() for item in values})
    if len(all_dates) < 30:
        raise ValueError("面板训练交易日不足")
    development_end = max(1, int(len(all_dates) * 0.60))
    calibration_end = max(development_end + 1, int(len(all_dates) * 0.80))
    calibration_end = min(calibration_end, len(all_dates) - 1)
    calibration_start = all_dates[development_end]
    locked_start = all_dates[calibration_end]
    development_rows = [
        item
        for values in usable.values()
        for item in values
        if item.signal_date < calibration_start and item.exit_date < calibration_start
    ]
    calibration_rows = [
        item
        for values in usable.values()
        for item in values
        if calibration_start <= item.signal_date < locked_start
    ]
    locked_by_code = {
        code: tuple(item for item in values if item.signal_date >= locked_start)
        for code, values in usable.items()
    }
    locked_rows = [item for values in locked_by_code.values() for item in values]
    total_samples = len(development_rows) + len(calibration_rows) + len(locked_rows)
    if total_samples < minimum_samples:
        raise ValueError(f"面板训练至少需要 {minimum_samples} 个样本")
    if not development_rows or not calibration_rows or not locked_rows:
        raise ValueError("面板日期切分为空")
    model = RegularizedLogisticModel.fit(development_rows)
    calibration_raw = [model.predict(item.features) for item in calibration_rows]
    calibration = PlattCalibrator.fit(
        calibration_raw,
        [item.positive for item in calibration_rows],
    )
    locked_probabilities = [
        calibration.calibrate(model.predict(item.features)) for item in locked_rows
    ]
    locked_labels = [item.positive for item in locked_rows]
    baseline = sum(item.positive for item in development_rows) / len(development_rows)
    locked_metrics = evaluate_probability_metrics(
        locked_probabilities,
        locked_labels,
        baseline,
    )
    target_metrics: dict[str, PanelTargetMetrics] = {}
    for code, rows in locked_by_code.items():
        if not rows:
            continue
        probabilities = [
            calibration.calibrate(model.predict(item.features)) for item in rows
        ]
        metrics = evaluate_probability_metrics(
            probabilities,
            [item.positive for item in rows],
            baseline,
        )
        target_metrics[code] = PanelTargetMetrics(
            sample_count=metrics.sample_count,
            brier_score=metrics.brier_score,
            log_loss=metrics.log_loss,
            expected_calibration_error=metrics.expected_calibration_error,
        )
    dimensions_count = next(iter(dimensions))
    fingerprint_payload = {
        "horizon": horizon_days,
        "codes": list(usable),
        "dates": [all_dates[0], all_dates[-1]],
        "counts": [len(development_rows), len(calibration_rows), len(locked_rows)],
        "weights": [float(value) for value in model.weights],
    }
    artifact = PanelArtifact(
        schema_version=PANEL_SCHEMA_VERSION,
        mode=mode,
        horizon_days=horizon_days,
        published_at=published_at or datetime.now().isoformat(),
        trained_through=max(item.exit_date for item in development_rows),
        universe_size=len(usable),
        sample_count=total_samples,
        feature_codes=(
            tuple(feature_codes)
            if feature_codes is not None
            else tuple(f"TARGET_{index}" for index in range(dimensions_count))
        ),
        development_last_exit_date=max(item.exit_date for item in development_rows),
        calibration_start_date=calibration_start,
        calibration_end_date=all_dates[calibration_end - 1],
        locked_start_date=locked_start,
        model_means=tuple(float(value) for value in model.means),
        model_scales=tuple(float(value) for value in model.scales),
        model_weights=tuple(float(value) for value in model.weights),
        calibration=calibration,
        locked_metrics=locked_metrics,
        target_metrics=target_metrics,
        current_features_by_code={
            code: tuple(float(item) for item in values)
            for code, values in sorted((current_features_by_code or {}).items())
        },
        data_fingerprint=hashlib.sha256(
            json.dumps(fingerprint_payload, sort_keys=True).encode()
        ).hexdigest(),
    )
    _validate_artifact(artifact)
    return artifact


def assess_panel_model(
    artifact: PanelArtifact,
    *,
    instrument_code: str,
    current_features: Sequence[float],
    individual_probability: float,
    individual_brier_score: float,
    individual_log_loss: float,
    now: datetime | None = None,
) -> PanelAssessment:
    current_time = now or datetime.now()
    age = max(0, (current_time - datetime.fromisoformat(artifact.published_at)).days)
    inference_features = (
        artifact.current_features_by_code.get(instrument_code, tuple(current_features))
        if artifact.mode == "PANEL_FULL"
        else tuple(current_features)
    )
    finite_count = sum(math.isfinite(float(value)) for value in inference_features)
    coverage = finite_count / max(1, len(artifact.feature_codes))
    panel_probability = individual_probability
    distance = math.inf
    fallback: str | None = None
    if len(inference_features) == len(artifact.feature_codes) and coverage == 1.0:
        model = artifact.model()
        normalized = model.normalized(inference_features)
        distance = sum(abs(value) for value in normalized) / len(normalized)
        panel_probability = artifact.calibration.calibrate(model.predict(inference_features))
    else:
        fallback = "当前特征维度或覆盖率与面板产物不一致"
    target = artifact.target_metrics.get(instrument_code)
    brier_delta = (
        target.brier_score - individual_brier_score if target is not None else None
    )
    log_loss_delta = (
        target.log_loss - individual_log_loss if target is not None else None
    )
    drift_status = "HEALTHY"
    if age > 90 or coverage < 1.0 or distance > 6.0:
        drift_status = "REJECTED"
    elif age > 45 or distance > 3.0:
        drift_status = "WATCH"
    eligible = (
        drift_status != "REJECTED"
        and target is not None
        and target.sample_count >= 15
        and brier_delta is not None
        and brier_delta <= -0.005
        and log_loss_delta is not None
        and log_loss_delta <= 0.0
    )
    weight = 0.0
    if eligible:
        evidence_weight = min(1.0, target.sample_count / 60.0)
        drift_weight = 0.5 if drift_status == "WATCH" else 1.0
        weight = MAX_BLEND_WEIGHT * evidence_weight * coverage * drift_weight
    elif fallback is None:
        if drift_status == "REJECTED":
            fallback = "面板产物过期、覆盖不足或特征漂移超限"
        elif target is None:
            fallback = "目标股票没有同口径锁定样本，面板模型仅作影子观察"
        else:
            fallback = "面板模型未在目标股票锁定区稳定优于个股冠军"
    final_probability = _bounded(
        (1.0 - weight) * individual_probability + weight * panel_probability
    )
    return PanelAssessment(
        status="BLENDED" if weight > 0 else "SHADOW",
        mode=artifact.mode,
        artifact_version=artifact.data_fingerprint[:12],
        published_at=artifact.published_at,
        artifact_age_days=age,
        universe_size=artifact.universe_size,
        sample_count=artifact.sample_count,
        feature_coverage=coverage,
        feature_distance=distance if math.isfinite(distance) else 999.0,
        drift_status=drift_status,
        individual_probability=individual_probability,
        panel_probability=panel_probability,
        final_probability=final_probability,
        blend_weight=weight,
        target_locked_sample_count=target.sample_count if target is not None else 0,
        locked_brier_delta=brier_delta,
        locked_log_loss_delta=log_loss_delta,
        panel_brier_score=artifact.locked_metrics.brier_score,
        panel_log_loss=artifact.locked_metrics.log_loss,
        panel_ece=artifact.locked_metrics.expected_calibration_error,
        fallback_reason=fallback,
        evidence=(
            "面板模型仅使用日期级前向切分与已成熟标签",
            "融合权重上限 45%，未通过目标锁定区比较时保持个股冠军概率",
        ),
    )


def _validate_artifact(artifact: PanelArtifact) -> None:
    if artifact.mode not in {"PANEL_CORE", "PANEL_FULL"}:
        raise ValueError("面板模型模式无效")
    if artifact.universe_size < 1 or artifact.sample_count < 1:
        raise ValueError("面板模型样本审计无效")
    if not artifact.development_last_exit_date < artifact.calibration_start_date:
        raise ValueError("面板模型训练标签未完成清洗")
    if not artifact.calibration_end_date < artifact.locked_start_date:
        raise ValueError("面板模型锁定测试边界无效")
    dimensions = len(artifact.feature_codes)
    if not (
        len(artifact.model_means) == dimensions
        and len(artifact.model_scales) == dimensions
        and len(artifact.model_weights) == dimensions + 1
    ):
        raise ValueError("面板模型参数维度无效")
    numeric = (
        *artifact.model_means,
        *artifact.model_scales,
        *artifact.model_weights,
        artifact.locked_metrics.brier_score,
        artifact.locked_metrics.log_loss,
    )
    if any(not math.isfinite(value) for value in numeric):
        raise ValueError("面板模型包含非有限数值")
    if any(
        len(values) != dimensions
        or any(not math.isfinite(value) for value in values)
        for values in artifact.current_features_by_code.values()
    ):
        raise ValueError("面板模型当前截面特征无效")


def _bounded(value: float) -> float:
    return min(0.99, max(0.01, float(value)))
