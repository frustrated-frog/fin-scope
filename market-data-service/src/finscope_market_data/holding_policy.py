from __future__ import annotations

from datetime import date
from math import floor
from typing import Literal

from pydantic import BaseModel, ConfigDict, Field, model_validator


def _to_camel(value: str) -> str:
    head, *tail = value.split("_")
    return head + "".join(part.capitalize() for part in tail)


class HoldingPolicyModel(BaseModel):
    model_config = ConfigDict(alias_generator=_to_camel, populate_by_name=True)


class HoldingStrategyRequest(HoldingPolicyModel):
    instrument_code: str = Field(pattern=r"^\d{6}\.(SH|SZ|BJ)$")
    as_of_date: date
    horizon_days: Literal[1, 5, 20]
    market_price: float = Field(gt=0)
    quantity: float = Field(ge=0)
    cash: float
    total_equity: float = Field(gt=0)
    current_weight: float = Field(ge=0, le=1.5)
    up_probability: float = Field(ge=0, le=1)
    p10_return: float
    p50_return: float
    p90_return: float
    forecast_status: str
    model_health_status: str
    quote_age_days: int = Field(ge=0)
    round_trip_cost_rate: float = Field(default=0.0015, ge=0, le=0.05)
    max_position_weight: float = Field(default=0.65, gt=0, le=1)
    cash_buffer_rate: float = Field(default=0.10, ge=0, lt=1)
    minimum_net_edge: float = Field(default=0.005, ge=0, le=0.20)
    lot_size: int = Field(default=100, ge=1)
    forecast_run_id: int | None = None
    model_version: str
    data_fingerprint: str
    cost_basis: float | None = Field(default=None, ge=0)
    unrealized_return: float | None = None

    @model_validator(mode="after")
    def validate_quantiles(self) -> "HoldingStrategyRequest":
        if not self.p10_return <= self.p50_return <= self.p90_return:
            raise ValueError("收益分位数必须满足 P10 <= P50 <= P90")
        return self


class HoldingStrategyResult(HoldingPolicyModel):
    action: Literal[
        "HOLD", "ALLOW_ADD", "REDUCE_CONCENTRATION", "EXIT_TRIGGERED", "ABSTAIN"
    ]
    suggested_quantity: int = Field(ge=0)
    expected_edge_after_cost: float
    p10_risk_amount: float
    p90_upside_amount: float
    current_market_value: float
    projected_weight: float
    evidence: list[str] = Field(default_factory=list)
    blockers: list[str] = Field(default_factory=list)
    explanation: str
    benchmark: str = "同一只股票保持当时持仓不动"
    policy_version: str = "holding-policy-v1"


class HoldingStrategySettlementRequest(HoldingPolicyModel):
    action: Literal[
        "HOLD", "ALLOW_ADD", "REDUCE_CONCENTRATION", "EXIT_TRIGGERED", "ABSTAIN"
    ]
    suggested_quantity: int = Field(ge=0)
    held_quantity: float = Field(gt=0)
    current_market_value: float = Field(gt=0)
    entry_price: float = Field(gt=0)
    actual_net_return: float


class HoldingStrategySettlementResult(HoldingPolicyModel):
    strategy_return: float
    hold_return: float
    incremental_return: float
    method: str = "frozen-action-v1"


def evaluate_holding_strategy(
    request: HoldingStrategyRequest,
) -> HoldingStrategyResult:
    market_value = request.market_price * request.quantity
    edge = round(request.p50_return - request.round_trip_cost_rate, 8)
    base = {
        "expected_edge_after_cost": edge,
        "p10_risk_amount": round(market_value * request.p10_return, 2),
        "p90_upside_amount": round(market_value * request.p90_return, 2),
        "current_market_value": round(market_value, 2),
        "projected_weight": round(request.current_weight, 8),
    }
    evidence = [
        f"校准上涨概率 {request.up_probability:.1%}",
        f"P10 / P50 / P90 为 {request.p10_return:.1%} / "
        f"{request.p50_return:.1%} / {request.p90_return:.1%}",
        f"费用后中位优势 {edge:.2%}",
    ]
    blockers: list[str] = []
    if request.quote_age_days > 1:
        blockers.append("行情已过期")
    if request.model_health_status.upper() in {
        "PAUSED", "UNHEALTHY", "INSUFFICIENT", "INSUFFICIENT_DATA"
    }:
        blockers.append("模型健康门禁未通过")
    if request.forecast_status.upper() in {
        "INSUFFICIENT_DATA", "NO_CLEAR_ADVANTAGE", "FAILED"
    }:
        blockers.append("预测证据不足")
    if blockers:
        return HoldingStrategyResult(
            action="ABSTAIN",
            suggested_quantity=0,
            evidence=evidence,
            blockers=blockers,
            explanation="证据门禁未通过，保持当前持仓并等待可验证的新数据。",
            **base,
        )

    if request.current_weight > request.max_position_weight:
        reduction = _reduction_quantity(request)
        projected_value = max(0.0, market_value - reduction * request.market_price)
        return HoldingStrategyResult(
            action="REDUCE_CONCENTRATION",
            suggested_quantity=reduction,
            projected_weight=round(projected_value / request.total_equity, 8),
            evidence=evidence + [
                f"当前单股权重 {request.current_weight:.1%} 高于上限 "
                f"{request.max_position_weight:.1%}"
            ],
            blockers=[],
            explanation="模型方向未必转空，但组合暴露已超过预设风险预算。",
            **{key: value for key, value in base.items() if key != "projected_weight"},
        )

    exit_triggered = (
        request.up_probability < 0.42
        and request.p50_return < -request.round_trip_cost_rate
        and request.p10_return <= -0.04
    )
    if exit_triggered and request.quantity > 0:
        return HoldingStrategyResult(
            action="EXIT_TRIGGERED",
            suggested_quantity=floor(request.quantity),
            projected_weight=0,
            evidence=evidence + ["方向概率、收益中位数和左尾风险同时恶化"],
            blockers=[],
            explanation="退出需要联合证据，本次三项门禁同时触发；不使用成本价或浮亏作决策。",
            **{key: value for key, value in base.items() if key != "projected_weight"},
        )

    add_signal = (
        request.up_probability >= 0.62
        and edge >= request.minimum_net_edge
        and request.p10_return > -0.08
        and request.forecast_status.upper() in {"ROBUST", "CONDITIONAL"}
    )
    if add_signal:
        quantity = _affordable_add_quantity(request, market_value)
        if quantity >= request.lot_size:
            projected = (market_value + quantity * request.market_price) / request.total_equity
            return HoldingStrategyResult(
                action="ALLOW_ADD",
                suggested_quantity=quantity,
                projected_weight=round(projected, 8),
                evidence=evidence + ["上涨概率、费用后优势与左尾预算同时通过"],
                blockers=[],
                explanation="允许按最小整手逐步增加暴露；这是风险约束后的许可，不是收益保证。",
                **{key: value for key, value in base.items() if key != "projected_weight"},
            )
        blockers.append("可用现金或集中度额度不足一手")

    return HoldingStrategyResult(
        action="HOLD",
        suggested_quantity=0,
        evidence=evidence,
        blockers=blockers,
        explanation="现有证据不足以支持改变仓位，基准动作是保持同股持仓不动。",
        **base,
    )


def _affordable_add_quantity(
    request: HoldingStrategyRequest,
    market_value: float,
) -> int:
    cash_budget = max(0.0, request.cash - request.total_equity * request.cash_buffer_rate)
    weight_budget = max(
        0.0,
        request.total_equity * request.max_position_weight - market_value,
    )
    notional = min(cash_budget, weight_budget)
    lots = floor(notional / request.market_price / request.lot_size)
    return max(0, lots * request.lot_size)


def _reduction_quantity(request: HoldingStrategyRequest) -> int:
    excess_value = request.total_equity * (
        request.current_weight - request.max_position_weight
    )
    lots = max(1, floor(excess_value / request.market_price / request.lot_size))
    proposed = lots * request.lot_size
    return min(floor(request.quantity), proposed)


def settle_holding_strategy(
    request: HoldingStrategySettlementRequest,
) -> HoldingStrategySettlementResult:
    hold_return = request.actual_net_return
    strategy_return = hold_return
    if request.action == "ALLOW_ADD":
        additional_value = request.suggested_quantity * request.entry_price
        exposure_multiple = 1 + additional_value / request.current_market_value
        strategy_return = hold_return * exposure_multiple
    elif request.action == "REDUCE_CONCENTRATION":
        remaining_ratio = max(
            0.0,
            1 - request.suggested_quantity / request.held_quantity,
        )
        strategy_return = hold_return * remaining_ratio
    elif request.action == "EXIT_TRIGGERED":
        strategy_return = 0.0
    return HoldingStrategySettlementResult(
        strategy_return=round(strategy_return, 8),
        hold_return=round(hold_return, 8),
        incremental_return=round(strategy_return - hold_return, 8),
    )
