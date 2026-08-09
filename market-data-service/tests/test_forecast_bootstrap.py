from __future__ import annotations

import pytest

from finscope_market_data.forecast.bootstrap import (
    bootstrap_interval,
    moving_block_indices,
    paired_annualized_excess,
)


def test_moving_block_indices_are_deterministic_and_have_requested_length() -> None:
    first = moving_block_indices(11, block_length=3, seed=42)
    second = moving_block_indices(11, block_length=3, seed=42)

    assert first == second
    assert len(first) == 11
    assert all(0 <= index < 11 for index in first)


def test_bootstrap_interval_is_deterministic_and_ordered() -> None:
    values = [0.01, -0.02, 0.03, 0.015, -0.005, 0.02, 0.01, -0.01]

    first = bootstrap_interval(
        len(values),
        lambda indices: sum(values[index] for index in indices) / len(indices),
        block_length=3,
        iterations=200,
        seed=7,
    )
    second = bootstrap_interval(
        len(values),
        lambda indices: sum(values[index] for index in indices) / len(indices),
        block_length=3,
        iterations=200,
        seed=7,
    )

    assert first == second
    assert first.status == "AVAILABLE"
    assert first.lower <= first.upper
    assert first.valid_iterations == 200


def test_bootstrap_interval_returns_unavailable_for_invalid_input() -> None:
    result = bootstrap_interval(
        0,
        lambda _: 0.0,
        block_length=3,
        iterations=100,
        seed=1,
    )

    assert result.status == "UNAVAILABLE"
    assert result.lower is None
    assert result.upper is None


def test_annualized_excess_is_comparable_across_sample_lengths() -> None:
    strategy = [0.001] * 242
    benchmark = [0.0004] * 242
    once = paired_annualized_excess(strategy, benchmark, tuple(range(242)))
    twice = paired_annualized_excess(
        strategy * 2,
        benchmark * 2,
        tuple(range(484)),
    )

    assert once == pytest.approx(twice)
    assert 0 < once < 1
