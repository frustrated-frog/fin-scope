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


def test_serving_distribution_uses_recent_mature_labels_and_current_volatility():
    from dataclasses import replace
    history = [replace(row, features=(*row.features, 0., 0., .01)) for row in _samples(800)]
    cutoff = history[700].exit_date
    low = forecast_return_distribution(history, current_features=(.2, .1, .4, 0., 0., .01), horizon_days=5, cutoff=cutoff)
    high = forecast_return_distribution(history, current_features=(.2, .1, .4, 0., 0., .04), horizon_days=5, cutoff=cutoff)
    assert low.production_training_through > low.development_last_exit_date
    assert low.production_calibration_through <= cutoff
    from finscope_market_data.forecast.return_distribution import _return_scale
    assert _return_scale((.2, .1, .4, 0., 0., .04), 5) == 4 * _return_scale((.2, .1, .4, 0., 0., .01), 5)
    if low.production_applied:
        assert high.p90 - high.p10 > 3.9 * (low.p90 - low.p10)
    else:
        assert high.p90 - high.p10 == low.p90 - low.p10
    assert high.locked_coverage == low.locked_coverage
    changed = [replace(row, net_return=50.) if row.exit_date > cutoff else row for row in history]
    repeated = forecast_return_distribution(changed, current_features=(.2, .1, .4, 0., 0., .01), horizon_days=5, cutoff=cutoff)
    assert repeated == low


def test_recent_distribution_cannot_replace_baseline_without_beating_zero_return(monkeypatch):
    from dataclasses import replace
    module = 'finscope_market_data.forecast.return_distribution'
    monkeypatch.setattr(f'{module}._fit_quantile', lambda *args: 'baseline')
    monkeypatch.setattr(f'{module}._recent_models', lambda *args: (('candidate',), 0.))
    monkeypatch.setattr(f'{module}._ordered_predictions',
        lambda models, features: (-.1, .02, .1) if models[0] == 'candidate' else (-1., .03, 1.))
    history = [replace(row, net_return=0.) for row in _samples(800)]
    result = forecast_return_distribution(history, current_features=(.2, .1, .4), horizon_days=5)
    # Candidate improves both incumbent MAE and interval score, but the zero forecast is perfect.
    assert result.production_applied is False
    assert result.p50 == .03
