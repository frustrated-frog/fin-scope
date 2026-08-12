from __future__ import annotations

import math
from datetime import date, timedelta

from finscope_market_data.forecast.features import ForecastSample
from finscope_market_data.forecast.service import _comparable_observations, build_forecast
from finscope_market_data.forecast.context import build_aligned_context
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


def test_similar_signal_distribution_compares_probabilities_on_the_raw_scale() -> None:
    observations = validate_walk_forward(samples(500)).observations
    raw_probability = observations[-1].probability

    comparable = _comparable_observations(observations, raw_probability)

    assert comparable
    assert all(abs(item.probability - raw_probability) <= 0.10 for item in comparable)


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


def test_forecast_refuses_probability_when_locked_qualification_is_too_small() -> None:
    result = build_forecast(
        bars(800),
        instrument_code="600519.SH",
        source_code="PYTDX",
        source_family="TDX",
        quality_status="FRESH_FALLBACK",
        warnings=[],
        horizon_days=20,
    )

    assert result.status == "INSUFFICIENT_DATA"
    assert result.up_probability is None
    assert result.qualification is not None
    assert result.qualification.status == "INSUFFICIENT_DATA"


def test_forecast_produces_auditable_default_five_day_probability() -> None:
    history = bars(1600)
    market_symbol = StockSymbol(market="SH", code="000300")
    market = [item.model_copy(update={"symbol": market_symbol}) for item in history]
    result = build_forecast(
        history,
        instrument_code="600519.SH",
        source_code="PYTDX",
        source_family="TDX",
        quality_status="FRESH_FALLBACK",
        warnings=[],
        context=build_aligned_context(history, market_bars=market),
    )

    assert result.instrument_code == "600519.SH"
    assert result.horizon_days == 5
    assert result.up_probability is not None and 0 <= result.up_probability <= 1
    assert result.expected_net_return is not None
    assert result.lower_net_return <= result.upper_net_return
    assert len(result.data_fingerprint) == 64
    assert result.validation is not None
    assert result.validation.independent_sample_count > 0
    assert len(result.recent_observations) <= 12
    assert result.report_schema_version == "single-stock-research-v5"
    assert result.model_version.startswith("competition-")
    assert result.model_version.endswith("-v5.1")
    assert result.raw_probability is not None
    assert result.qualification is not None
    assert len(result.qualification.trial.trial_id) == 64
    assert result.qualification.split_audit.development.end_date < result.qualification.split_audit.calibration.start_date
    assert result.qualification.split_audit.calibration.end_date < result.qualification.split_audit.locked_test.start_date
    assert result.qualification.locked_test.calibrated_metrics.sample_count > 0
    assert result.qualification.locked_test.baseline_metrics.brier_skill_score == 0
    assert sum(item.count for item in result.qualification.locked_test.reliability_bins) == result.qualification.locked_test.calibrated_metrics.sample_count
    assert result.qualification.confidence_intervals.brier_skill_score.status in {"AVAILABLE", "UNAVAILABLE"}
    assert result.probability_interval is not None
    assert result.performance is not None
    assert result.performance.benchmark_label == "同股买入并持有"
    assert result.performance.trade_count >= 0
    assert len(result.factor_explanations) == len(result.context.feature_codes)
    assert result.context.market.code == "000300.SH"
    assert result.context.market.coverage == 1.0
    assert result.context.market.regime in {"UPTREND", "DOWNTREND", "RANGE", "HIGH_VOLATILITY"}
    assert result.context.industry.status == "UNAVAILABLE"
    assert len(result.model_competition.candidates) == 3
    assert sum(item.selected for item in result.model_competition.candidates) == 1
    assert result.model_competition.selected_model in {"LOGISTIC", "BOOSTED_STUMPS", "RULE_BASELINE"}
    assert result.leakage_audit.status == "PASSED"
    assert result.leakage_audit.checked_sample_count > 0
    assert all("训练仅使用" not in item for item in result.leakage_audit.checks)
    assert result.qlib_reference.status == "NOT_RUN"
    assert result.in_sample is not None
    assert result.out_of_sample is not None
    assert len(result.parameter_stability.scenarios) == 5
    assert result.status in {"ROBUST", "CONDITIONAL", "NO_CLEAR_EDGE"}
    assert result.decision in {"UP", "DOWN", "ABSTAIN"}
    assert result.selective_validation is not None
    assert 0 <= result.selective_validation.coverage <= 1
    assert result.qualification.split_audit.label_horizon_days == 5
    assert result.qualification.split_audit.independent_stride_days == 5


def test_forecast_keeps_each_horizon_trial_identity_independent() -> None:
    history = bars(1600)

    one_day = build_forecast(
        history, instrument_code="600519.SH", source_code="PYTDX",
        source_family="TDX", quality_status="FRESH", warnings=[], horizon_days=1,
    )
    twenty_day = build_forecast(
        history, instrument_code="600519.SH", source_code="PYTDX",
        source_family="TDX", quality_status="FRESH", warnings=[], horizon_days=20,
    )

    assert one_day.horizon_days == 1
    assert twenty_day.horizon_days == 20
    assert one_day.qualification is not None
    assert twenty_day.qualification is not None
    assert one_day.qualification.trial.trial_id != twenty_day.qualification.trial.trial_id


def test_context_history_participates_in_forecast_fingerprint() -> None:
    history = bars(400)
    market_symbol = StockSymbol(market="SH", code="000300")
    market = [item.model_copy(update={"symbol": market_symbol}) for item in history]
    changed_market = list(market)
    changed_market[-1] = changed_market[-1].model_copy(update={
        "close": changed_market[-1].close + 1,
        "high": changed_market[-1].high + 1,
    })

    first = build_forecast(history, instrument_code="600519.SH", source_code="PYTDX",
        source_family="TDX", quality_status="FRESH", warnings=[],
        context=build_aligned_context(history, market_bars=market))
    second = build_forecast(history, instrument_code="600519.SH", source_code="PYTDX",
        source_family="TDX", quality_status="FRESH", warnings=[],
        context=build_aligned_context(history, market_bars=changed_market))

    assert first.data_fingerprint != second.data_fingerprint
