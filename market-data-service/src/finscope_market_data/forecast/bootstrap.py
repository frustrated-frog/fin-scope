from __future__ import annotations

from dataclasses import dataclass
import math
from typing import Callable, Sequence

import numpy as np


@dataclass(frozen=True)
class ConfidenceInterval:
    status: str
    lower: float | None
    upper: float | None
    confidence_level: float
    method: str
    valid_iterations: int
    reason: str | None = None


def moving_block_indices(
    sample_count: int,
    *,
    block_length: int,
    seed: int,
) -> tuple[int, ...]:
    if sample_count < 1 or block_length < 1:
        return ()
    generator = np.random.default_rng(seed)
    indices: list[int] = []
    while len(indices) < sample_count:
        start = int(generator.integers(0, sample_count))
        indices.extend(
            (start + offset) % sample_count
            for offset in range(block_length)
        )
    return tuple(indices[:sample_count])


def bootstrap_interval(
    sample_count: int,
    statistic: Callable[[tuple[int, ...]], float],
    *,
    block_length: int,
    iterations: int,
    seed: int,
    confidence_level: float = 0.95,
) -> ConfidenceInterval:
    if sample_count < 2 or iterations < 1 or block_length < 1:
        return _unavailable(confidence_level, "有效样本或迭代次数不足")
    generator = np.random.default_rng(seed)
    values: list[float] = []
    for _ in range(iterations):
        draw_seed = int(generator.integers(0, np.iinfo(np.int64).max))
        indices = moving_block_indices(
            sample_count,
            block_length=min(block_length, sample_count),
            seed=draw_seed,
        )
        try:
            value = float(statistic(indices))
        except (ArithmeticError, ValueError):
            continue
        if math.isfinite(value):
            values.append(value)
    if not values:
        return _unavailable(confidence_level, "bootstrap 未产生有效统计量")
    alpha = (1.0 - confidence_level) / 2.0
    lower, upper = np.quantile(np.asarray(values), [alpha, 1.0 - alpha])
    return ConfidenceInterval(
        status="AVAILABLE",
        lower=float(lower),
        upper=float(upper),
        confidence_level=confidence_level,
        method="MOVING_BLOCK_BOOTSTRAP",
        valid_iterations=len(values),
    )


def paired_annualized_excess(
    strategy_returns: Sequence[float],
    benchmark_returns: Sequence[float],
    indices: Sequence[int],
    *,
    periods_per_year: float = 242.0,
) -> float:
    if len(strategy_returns) != len(benchmark_returns):
        raise ValueError("策略与基准收益序列数量不一致")
    if not indices or periods_per_year <= 0:
        raise ValueError("年化超额收益样本或周期参数无效")
    strategy_log_return = 0.0
    benchmark_log_return = 0.0
    for index in indices:
        if strategy_returns[index] <= -1.0 or benchmark_returns[index] <= -1.0:
            raise ValueError("收益率不得小于等于 -100%")
        strategy_log_return += math.log1p(strategy_returns[index])
        benchmark_log_return += math.log1p(benchmark_returns[index])
    scale = periods_per_year / len(indices)
    return math.expm1(strategy_log_return * scale) - math.expm1(
        benchmark_log_return * scale
    )


def _unavailable(confidence_level: float, reason: str) -> ConfidenceInterval:
    return ConfidenceInterval(
        status="UNAVAILABLE",
        lower=None,
        upper=None,
        confidence_level=confidence_level,
        method="MOVING_BLOCK_BOOTSTRAP",
        valid_iterations=0,
        reason=reason,
    )
