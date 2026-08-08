from __future__ import annotations

import math
from datetime import date, timedelta

from finscope_market_data.forecast.features import ForecastSample
from finscope_market_data.forecast.service import build_forecast
from finscope_market_data.forecast.walk_forward import validate_walk_forward
from finscope_market_data.models import DailyBar, StockSymbol


def samples(count: int) -> list[ForecastSample]:
    first = date(2010, 1, 1)
    result: list[ForecastSample] = []
    for index in range(count):
        cycle = math.sin(index / 13.0)
        net_return = cycle * 0.05 + (-0.01 if index % 7 == 0 else 0.005)
        result.append(
            ForecastSample(
                signal_date=(first + timedelta(days=index)).isoformat(),
                entry_date=(first + timedelta(days=index + 1)).isoformat(),
                exit_date=(first + timedelta(days=index + 20)).isoformat(),
                features=(cycle, cycle / 2, index % 20 / 20, 0, 0, 0.2, 0),
                net_return=net_return,
            )
        )
    return result


def bars(count: int) -> list[DailyBar]:
    symbol = StockSymbol(market="SH", code="600519")
    first = date(2015, 1, 1)
    result: list[DailyBar] = []
    for index in range(count):
        trend = 80 + index * 0.025
        price = trend + math.sin(index / 17) * 5 + math.sin(index / 5)
        volume = 1_000_000 + index % 30 * 10_000
        result.append(
            DailyBar(
                symbol=symbol,
                trade_date=(first + timedelta(days=index)).isoformat(),
                open=price * (1 + math.sin(index) * 0.001),
                high=price * 1.02,
                low=price * 0.98,
                close=price,
                volume=volume,
                amount=price * volume,
                adjustment="QFQ",
            )
        )
    return result


def test_walk_forward_uses_only_labels_matured_before_each_signal() -> None:
    result = validate_walk_forward(samples(500))

    assert result.observations
    assert all(item.training_through < item.signal_date for item in result.observations)
    assert all(0 <= item.probability <= 1 for item in result.observations)
    assert result.independent_sample_count > 5
    assert result.initial_training_size == 300
    assert result.in_sample_count == 300
    assert result.in_sample_brier_score >= 0
    assert result.brier_score >= 0
    assert result.baseline_brier_score >= 0


def test_walk_forward_metrics_are_deterministic() -> None:
    first = validate_walk_forward(samples(500))
    second = validate_walk_forward(samples(500))

    assert first.brier_score == second.brier_score
    assert first.accuracy == second.accuracy
    assert len(first.observations) == len(second.observations)


def test_forecast_returns_structured_insufficient_state() -> None:
    result = build_forecast(
        bars(400),
        instrument_code="600519.SH",
        source_code="PYTDX",
        source_family="TDX",
        quality_status="FRESH_FALLBACK",
        warnings=["fallback"],
    )

    assert result.status == "INSUFFICIENT_DATA"
    assert result.bar_count == 400
    assert result.up_probability is None
    assert "不足" in result.conclusion


def test_forecast_produces_auditable_twenty_day_probability() -> None:
    result = build_forecast(
        bars(1600),
        instrument_code="600519.SH",
        source_code="PYTDX",
        source_family="TDX",
        quality_status="FRESH_FALLBACK",
        warnings=[],
    )

    assert result.instrument_code == "600519.SH"
    assert result.horizon_days == 20
    assert result.up_probability is not None and 0 <= result.up_probability <= 1
    assert result.expected_net_return is not None
    assert result.lower_net_return <= result.upper_net_return
    assert len(result.data_fingerprint) == 64
    assert result.validation is not None
    assert result.validation.independent_sample_count > 0
    assert len(result.recent_observations) <= 12
    assert result.report_schema_version == "single-stock-research-v2"
    assert result.model_version == "logistic-walk-forward-v2"
    assert result.performance is not None
    assert result.performance.benchmark_label == "同股买入并持有"
    assert result.performance.trade_count >= 0
    assert len(result.factor_explanations) == 7
    assert result.in_sample is not None
    assert result.out_of_sample is not None
    assert len(result.parameter_stability.scenarios) == 5
    assert result.status in {"ROBUST", "CONDITIONAL", "NO_CLEAR_EDGE"}
