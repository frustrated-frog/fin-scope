from __future__ import annotations

from dataclasses import dataclass
from typing import Sequence

from finscope_market_data.forecast.features import build_samples
from finscope_market_data.forecast.performance import simulate_strategy
from finscope_market_data.forecast.walk_forward import validate_walk_forward
from finscope_market_data.models import DailyBar


@dataclass(frozen=True)
class NeighborScenario:
    holding_days: int
    threshold: float
    primary: bool = False


NEIGHBOR_SCENARIOS: tuple[NeighborScenario, ...] = (
    NeighborScenario(20, 0.60, True),
    NeighborScenario(20, 0.55),
    NeighborScenario(20, 0.65),
    NeighborScenario(15, 0.60),
    NeighborScenario(25, 0.60),
)


@dataclass(frozen=True)
class StabilityScenarioResult:
    holding_days: int
    threshold: float
    primary: bool
    annualized_return: float
    excess_return: float
    sharpe_ratio: float
    max_drawdown: float
    trade_count: int


@dataclass(frozen=True)
class StabilityReport:
    scenarios: tuple[StabilityScenarioResult, ...]
    positive_excess_ratio: float
    worst_excess_return: float
    worst_sharpe_ratio: float


def analyze_stability(
    bars: Sequence[DailyBar], transaction_cost_rate: float
) -> StabilityReport:
    results: list[StabilityScenarioResult] = []
    for scenario in NEIGHBOR_SCENARIOS:
        samples = build_samples(
            bars,
            transaction_cost_rate=transaction_cost_rate,
            horizon_days=scenario.holding_days,
        )
        validation = validate_walk_forward(samples)
        if not validation.observations:
            results.append(
                StabilityScenarioResult(
                    scenario.holding_days,
                    scenario.threshold,
                    scenario.primary,
                    0.0,
                    0.0,
                    0.0,
                    0.0,
                    0,
                )
            )
            continue
        report = simulate_strategy(
            bars,
            samples,
            validation.observations,
            threshold=scenario.threshold,
            holding_days=scenario.holding_days,
            round_trip_cost=transaction_cost_rate,
        )
        results.append(
            StabilityScenarioResult(
                holding_days=scenario.holding_days,
                threshold=scenario.threshold,
                primary=scenario.primary,
                annualized_return=report.strategy.annualized_return,
                excess_return=report.excess_return,
                sharpe_ratio=report.strategy.sharpe_ratio,
                max_drawdown=report.strategy.max_drawdown,
                trade_count=report.trade_count,
            )
        )
    return StabilityReport(
        scenarios=tuple(results),
        positive_excess_ratio=sum(item.excess_return > 0 for item in results)
        / len(results),
        worst_excess_return=min(item.excess_return for item in results),
        worst_sharpe_ratio=min(item.sharpe_ratio for item in results),
    )
