from __future__ import annotations

from dataclasses import replace
from datetime import datetime, timedelta
import json
import math

import pytest

from finscope_market_data.forecast.features import ForecastSample
from finscope_market_data.forecast.panel import (
    PanelArtifactStore,
    assess_panel_model,
    train_panel_artifact,
)
from finscope_market_data.forecast.panel_features import augment_cross_sectional_features


def _samples(code_index: int, count: int = 260) -> list[ForecastSample]:
    first = datetime(2018, 1, 1)
    values: list[ForecastSample] = []
    for index in range(count):
        signal = first + timedelta(days=index)
        feature = math.sin((index + code_index) / 11.0)
        values.append(ForecastSample(
            signal_date=signal.date().isoformat(),
            entry_date=(signal + timedelta(days=1)).date().isoformat(),
            exit_date=(signal + timedelta(days=6)).date().isoformat(),
            features=(feature, code_index / 10.0, math.cos(index / 17.0)),
            net_return=0.02 if feature + (code_index % 2) * 0.1 > 0 else -0.015,
        ))
    return values


def test_panel_training_uses_date_level_purged_split() -> None:
    artifact = train_panel_artifact(
        {f"{600000 + index}.SH": _samples(index) for index in range(24)},
        horizon_days=5,
        minimum_instruments=20,
        minimum_samples=3000,
        published_at="2026-08-17T10:00:00",
    )

    assert artifact.universe_size == 24
    assert artifact.sample_count >= 3000
    assert artifact.development_last_exit_date < artifact.calibration_start_date
    assert artifact.calibration_end_date < artifact.locked_start_date
    assert artifact.locked_metrics.sample_count > 0
    assert artifact.feature_codes == ("TARGET_0", "TARGET_1", "TARGET_2")
    assert len(artifact.target_metrics) == 24


def test_panel_store_is_atomic_and_keeps_last_valid_artifact(tmp_path) -> None:
    store = PanelArtifactStore(tmp_path)
    artifact = train_panel_artifact(
        {f"{600000 + index}.SH": _samples(index) for index in range(20)},
        horizon_days=5,
        minimum_instruments=20,
        minimum_samples=3000,
        published_at="2026-08-17T10:00:00",
    )

    store.save(artifact)
    loaded = store.load(5)
    assert loaded is not None
    assert loaded.data_fingerprint == artifact.data_fingerprint

    path = tmp_path / "panel-model-5.json"
    path.write_text("{broken", encoding="utf-8")
    assert store.load(5) is None


def test_panel_assessment_rejects_stale_artifact_without_breaking_baseline() -> None:
    artifact = train_panel_artifact(
        {f"{600000 + index}.SH": _samples(index) for index in range(20)},
        horizon_days=5,
        minimum_instruments=20,
        minimum_samples=3000,
        published_at="2025-01-01T00:00:00",
    )
    target = artifact.target_metrics["600000.SH"]
    assessment = assess_panel_model(
        artifact,
        instrument_code="600000.SH",
        current_features=(0.2, 0.0, 0.1),
        individual_probability=0.61,
        individual_brier_score=target.brier_score + 0.02,
        individual_log_loss=target.log_loss + 0.02,
        now=datetime(2026, 8, 17),
    )

    assert assessment.drift_status == "REJECTED"
    assert assessment.blend_weight == 0
    assert assessment.final_probability == 0.61
    assert assessment.fallback_reason is not None


def test_panel_assessment_only_blends_when_target_locked_metrics_improve() -> None:
    artifact = train_panel_artifact(
        {f"{600000 + index}.SH": _samples(index) for index in range(20)},
        horizon_days=5,
        minimum_instruments=20,
        minimum_samples=3000,
        published_at="2026-08-17T10:00:00",
    )
    target = artifact.target_metrics["600000.SH"]
    assessment = assess_panel_model(
        artifact,
        instrument_code="600000.SH",
        current_features=(0.2, 0.0, 0.1),
        individual_probability=0.55,
        individual_brier_score=target.brier_score + 0.02,
        individual_log_loss=target.log_loss + 0.02,
        now=datetime(2026, 8, 17, 11, 0, 0),
    )

    assert assessment.mode == "PANEL_CORE"
    assert assessment.drift_status in {"HEALTHY", "WATCH"}
    assert 0 < assessment.blend_weight <= 0.45
    assert assessment.final_probability != assessment.individual_probability
    assert assessment.locked_brier_delta <= -0.005


def test_panel_artifact_json_has_no_non_finite_values() -> None:
    artifact = train_panel_artifact(
        {f"{600000 + index}.SH": _samples(index) for index in range(20)},
        horizon_days=5,
        minimum_instruments=20,
        minimum_samples=3000,
        published_at="2026-08-17T10:00:00",
    )

    payload = json.dumps(artifact.to_dict(), allow_nan=False)
    assert "NaN" not in payload


def test_cross_sectional_features_are_date_local_and_available_for_current_pool() -> None:
    source = {f"{600000 + index}.SH": _samples(index, 120) for index in range(20)}
    current = {code: values[-1].features for code, values in source.items()}

    augmented, current_augmented, codes = augment_cross_sectional_features(
        source,
        current,
        minimum_cross_section=20,
    )

    assert len(codes) == 8
    assert all(len(item.features) == 11 for values in augmented.values() for item in values)
    assert all(len(values) == 11 for values in current_augmented.values())
    ranks = [current_augmented[code][3] for code in sorted(current_augmented)]
    assert min(ranks) == 0
    assert max(ranks) == 1
    assert all(0 <= value <= 1 for values in current_augmented.values() for value in values[-8:])


def test_store_keeps_full_and_core_artifacts_separate(tmp_path) -> None:
    source = {f"{600000 + index}.SH": _samples(index) for index in range(20)}
    current = {code: values[-1].features for code, values in source.items()}
    augmented, current_augmented, codes = augment_cross_sectional_features(source, current)
    store = PanelArtifactStore(tmp_path)
    core = train_panel_artifact(source, horizon_days=5, published_at="2026-08-17T10:00:00")
    full = train_panel_artifact(
        augmented,
        horizon_days=5,
        published_at="2026-08-17T10:00:00",
        mode="PANEL_FULL",
        feature_codes=("TARGET_0", "TARGET_1", "TARGET_2", *codes),
        current_features_by_code=current_augmented,
    )

    store.save(core)
    store.save(full)

    assert store.load(5, mode="PANEL_CORE").mode == "PANEL_CORE"
    assert store.load(5, mode="PANEL_FULL").mode == "PANEL_FULL"
    assert len(store.load(5, mode="PANEL_FULL").current_features_by_code) == 20


@pytest.mark.parametrize("horizon_days", [1, 5, 20])
@pytest.mark.parametrize("boundary_only", [False, True])
def test_panel_calibrator_ignores_labels_not_mature_before_locked_start(
    horizon_days: int, boundary_only: bool,
) -> None:
    source = {
        str(code): [
            replace(item, exit_date=(datetime.fromisoformat(item.signal_date)
                                    + timedelta(days=horizon_days)).date().isoformat())
            for item in _samples(code)
        ]
        for code in range(2)
    }
    cutoff = source["0"][208].signal_date
    changed = {
        code: [
            replace(item, net_return=-item.net_return)
            if (item.exit_date == cutoff if boundary_only else item.exit_date >= cutoff)
            else item
            for item in rows
        ]
        for code, rows in source.items()
    }
    options = dict(horizon_days=horizon_days, minimum_instruments=2, minimum_samples=100)
    original = train_panel_artifact(source, **options)
    flipped = train_panel_artifact(changed, **options)

    assert original.calibration == flipped.calibration
    assert original.model_weights == flipped.model_weights
    features = source["0"][208].features
    assert original.calibration.calibrate(original.model().predict(features)) == (
        flipped.calibration.calibrate(flipped.model().predict(features))
    )
    assert original.calibration.sample_count == 2 * (52 - horizon_days)
    assert original.calibration_last_exit_date < original.locked_start_date


@pytest.mark.parametrize("invalid_proof", ["missing", "same_date", "future", "empty", "old_version"])
def test_panel_store_rejects_missing_or_invalid_calibration_boundary_proof(
    tmp_path, invalid_proof: str,
) -> None:
    artifact = train_panel_artifact(
        {"0": _samples(0), "1": _samples(1)},
        horizon_days=5, minimum_instruments=2, minimum_samples=100,
    )
    payload = artifact.to_dict()
    if invalid_proof == "missing":
        payload.pop("calibration_last_exit_date", None)
    elif invalid_proof == "same_date":
        payload["calibration_last_exit_date"] = artifact.locked_start_date
    elif invalid_proof == "future":
        payload["calibration_last_exit_date"] = "2099-01-01"
    elif invalid_proof == "empty":
        payload["calibration_last_exit_date"] = ""
    else:
        payload["schema_version"] = "panel-probability-v1"
    (tmp_path / "panel-model-5.json").write_text(json.dumps(payload), encoding="utf-8")

    assert PanelArtifactStore(tmp_path).load(5) is None
