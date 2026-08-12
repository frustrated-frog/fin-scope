from __future__ import annotations

import csv

import pytest

from finscope_market_data.forecast.qlib_reference import export_research_dataset, qlib_status
from test_forecast_features import bars


def test_qlib_reference_exports_plain_csv_without_runtime_dependency(tmp_path) -> None:
    target = tmp_path / "research.csv"

    summary = export_research_dataset(bars(100), target)

    with target.open(encoding="utf-8") as source:
        rows = list(csv.DictReader(source))
    assert summary.row_count == 100
    assert rows[-1]["instrument"] == "600519.SH"
    assert rows[-1]["adjustment"] == "QFQ"
    assert qlib_status().runtime_dependency is False


def test_qlib_reference_requires_separate_environment_to_run() -> None:
    status = qlib_status()

    assert status.status in {"AVAILABLE", "NOT_INSTALLED"}
    if status.status == "NOT_INSTALLED":
        assert "独立" in status.message
