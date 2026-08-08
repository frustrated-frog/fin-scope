from __future__ import annotations

from finscope_market_data.forecast.stability import NEIGHBOR_SCENARIOS


def test_stability_uses_fixed_neighbors_without_selecting_a_winner() -> None:
    assert [(item.holding_days, item.threshold) for item in NEIGHBOR_SCENARIOS] == [
        (20, 0.60),
        (20, 0.55),
        (20, 0.65),
        (15, 0.60),
        (25, 0.60),
    ]
    assert NEIGHBOR_SCENARIOS[0].primary is True
    assert all(not item.primary for item in NEIGHBOR_SCENARIOS[1:])
