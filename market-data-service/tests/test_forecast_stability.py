from __future__ import annotations

from finscope_market_data.forecast.context import build_aligned_context
import finscope_market_data.forecast.stability as stability_module
from finscope_market_data.forecast.stability import analyze_stability, neighbor_scenarios
from test_forecast_features import bars


def test_stability_uses_fixed_neighbors_without_selecting_a_winner() -> None:
    scenarios = neighbor_scenarios(5)
    assert [(item.holding_days, item.threshold) for item in scenarios] == [
        (5, 0.60),
        (5, 0.55),
        (5, 0.65),
        (3, 0.60),
        (10, 0.60),
    ]
    assert scenarios[0].primary is True
    assert all(not item.primary for item in scenarios[1:])


def test_stability_reuses_the_selected_model_and_context_feature_set() -> None:
    history = bars(800)
    context = build_aligned_context(history, market_bars=history)

    report = analyze_stability(
        history,
        0.0015,
        horizon_days=5,
        model_code="RULE_BASELINE",
        context=context,
    )

    assert len(report.scenarios) == 5
    assert report.scenario_count == 5
    assert 0 <= report.outperform_benchmark_ratio <= 1
    assert report.surface_variance >= 0
    assert report.robust_region_size <= report.scenario_count


def test_stability_computes_each_holding_period_only_once(monkeypatch) -> None:
    history = bars(800)
    calls: list[int] = []
    original = stability_module.validate_walk_forward

    def recording_validation(samples, *, independent_stride_days, model_code):
        calls.append(independent_stride_days)
        return original(
            samples,
            independent_stride_days=independent_stride_days,
            model_code=model_code,
        )

    monkeypatch.setattr(stability_module, "validate_walk_forward", recording_validation)

    report = analyze_stability(
        history,
        0.0015,
        horizon_days=5,
        model_code="RULE_BASELINE",
    )

    assert len(report.scenarios) == 5
    assert calls == [5, 3, 10]


def test_stability_reuses_primary_walk_forward_result(monkeypatch) -> None:
    history = bars(800)
    primary_samples = stability_module.build_samples(
        history,
        transaction_cost_rate=0.0015,
        horizon_days=5,
    )
    primary_validation = stability_module.validate_walk_forward(
        primary_samples,
        independent_stride_days=5,
        model_code="RULE_BASELINE",
    )
    calls: list[int] = []
    original = stability_module.validate_walk_forward

    def recording_validation(samples, *, independent_stride_days, model_code):
        calls.append(independent_stride_days)
        return original(
            samples,
            independent_stride_days=independent_stride_days,
            model_code=model_code,
        )

    monkeypatch.setattr(stability_module, "validate_walk_forward", recording_validation)

    report = analyze_stability(
        history,
        0.0015,
        horizon_days=5,
        model_code="RULE_BASELINE",
        primary_samples=primary_samples,
        primary_validation=primary_validation,
    )

    assert len(report.scenarios) == 5
    assert calls == [3, 10]
