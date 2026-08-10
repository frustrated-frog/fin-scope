from __future__ import annotations

from finscope_market_data.forecast.stability import neighbor_scenarios


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
