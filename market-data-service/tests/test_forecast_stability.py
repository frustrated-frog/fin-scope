from __future__ import annotations

from finscope_market_data.forecast.context import build_aligned_context
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
