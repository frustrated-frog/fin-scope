from __future__ import annotations

from datetime import date, timedelta
import math

import pytest

from finscope_market_data.forecast.features import ForecastSample
from finscope_market_data.forecast.return_distribution import (
    forecast_return_distribution,
)


def _samples(count: int = 500) -> list[ForecastSample]:
    first = date(2018, 1, 1)
    result: list[ForecastSample] = []
    for index in range(count):
        signal = first + timedelta(days=index)
        feature = math.sin(index / 17.0)
        result.append(ForecastSample(
            signal_date=signal.isoformat(),
            entry_date=(signal + timedelta(days=1)).isoformat(),
            exit_date=(signal + timedelta(days=6)).isoformat(),
            features=(feature, math.cos(index / 11.0), index % 23 / 23.0),
            net_return=feature * 0.035 + math.sin(index / 5.0) * 0.008,
        ))
    return result


def test_return_distribution_is_ordered_and_reports_locked_coverage() -> None:
    result = forecast_return_distribution(
        _samples(),
        current_features=(0.4, -0.2, 0.5),
        horizon_days=5,
    )

    assert result.status == "AVAILABLE"
    assert result.p10 <= result.p50 <= result.p90
    assert result.raw_p10 <= result.raw_p50 <= result.raw_p90
    assert 0 <= result.locked_coverage <= 1
    assert result.mean_interval_width >= 0
    assert result.locked_pinball_loss >= 0
    assert result.development_last_exit_date < result.calibration_start_date
    assert result.calibration_end_date < result.locked_start_date
    assert result.calibration_count > 0
    assert result.locked_count > 0
    assert result.conformal_radius >= 0


def test_return_distribution_is_deterministic() -> None:
    first = forecast_return_distribution(
        _samples(), current_features=(0.2, 0.1, 0.4), horizon_days=5,
    )
    second = forecast_return_distribution(
        _samples(), current_features=(0.2, 0.1, 0.4), horizon_days=5,
    )

    assert first == second


def test_return_distribution_rejects_non_finite_features() -> None:
    with pytest.raises(ValueError, match="有限"):
        forecast_return_distribution(
            _samples(), current_features=(math.nan, 0.1, 0.2), horizon_days=5,
        )


def test_return_distribution_returns_explicit_insufficient_state() -> None:
    result = forecast_return_distribution(
        _samples(80), current_features=(0.2, 0.1, 0.4), horizon_days=5,
    )

    assert result.status == "INSUFFICIENT_DATA"
    assert result.p10 is None
    assert result.reason is not None
