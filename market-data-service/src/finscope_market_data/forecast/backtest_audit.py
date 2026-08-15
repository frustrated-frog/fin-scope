from __future__ import annotations

from finscope_market_data.forecast.backtesting_adapter import ShadowBacktestResult
from finscope_market_data.forecast.performance import BacktestReport
from finscope_market_data.forecast.schemas import (
    AuditEngineMetrics,
    AuditMismatch,
    BacktestAudit,
)


RETURN_TOLERANCE = 0.002
COST_TOLERANCE = 0.001
MAX_DRAWDOWN_TOLERANCE = 0.002
SHARPE_TOLERANCE = 0.05


def audit_backtests(
    primary: BacktestReport, shadow: ShadowBacktestResult
) -> BacktestAudit:
    primary_metrics = _primary_metrics(primary)
    limitations = [
        "影子验证不参与本期方向决策",
        "首版审计沿用标准化资金与固定双边成本，不代表真实 A 股逐笔成交",
    ]
    if not shadow.available:
        return BacktestAudit(
            status="UNAVAILABLE",
            primary_engine=primary_metrics,
            shadow_engine=None,
            trade_count_agreement=False,
            entry_date_agreement_rate=0.0,
            exit_date_agreement_rate=0.0,
            return_delta=0.0,
            max_drawdown_delta=0.0,
            sharpe_delta=0.0,
            cost_delta=0.0,
            duration_ms=shadow.duration_ms,
            limitations=[*limitations, shadow.error or "影子回测暂不可用"],
        )

    shadow_metrics = _shadow_metrics(shadow)
    mismatches: list[AuditMismatch] = []
    trade_count_agreement = primary.trade_count == shadow.trade_count
    if not trade_count_agreement:
        mismatches.append(
            AuditMismatch(
                category="TRADE_COUNT",
                primary_value=primary.trade_count,
                shadow_value=shadow.trade_count,
                detail="两套引擎生成的完整交易数量不一致",
            )
        )

    entry_matches = 0
    exit_matches = 0
    paired_count = min(len(primary.trades), len(shadow.trades))
    for index in range(paired_count):
        primary_trade = primary.trades[index]
        shadow_trade = shadow.trades[index]
        trade_index = index + 1
        if primary_trade.entry_date == shadow_trade.entry_date:
            entry_matches += 1
        else:
            mismatches.append(
                AuditMismatch(
                    category="ENTRY_DATE",
                    trade_index=trade_index,
                    primary_value=primary_trade.entry_date,
                    shadow_value=shadow_trade.entry_date,
                    detail="下一交易日开盘入场日期不一致",
                )
            )
        if primary_trade.exit_date == shadow_trade.exit_date:
            exit_matches += 1
        else:
            mismatches.append(
                AuditMismatch(
                    category="EXIT_DATE",
                    trade_index=trade_index,
                    primary_value=primary_trade.exit_date,
                    shadow_value=shadow_trade.exit_date,
                    detail="固定持有后的开盘退出日期不一致",
                )
            )
        trade_return_delta = abs(primary_trade.net_return - shadow_trade.net_return)
        if trade_return_delta > RETURN_TOLERANCE:
            mismatches.append(
                AuditMismatch(
                    category="RETURN",
                    trade_index=trade_index,
                    primary_value=primary_trade.net_return,
                    shadow_value=shadow_trade.net_return,
                    detail="单笔扣费收益差异超过审计容差",
                )
            )
        trade_cost_delta = abs(primary_trade.cost - shadow_trade.cost)
        if trade_cost_delta > COST_TOLERANCE:
            mismatches.append(
                AuditMismatch(
                    category="COST",
                    trade_index=trade_index,
                    primary_value=primary_trade.cost,
                    shadow_value=shadow_trade.cost,
                    detail="单笔成本差异超过审计容差",
                )
            )

    denominator = max(primary.trade_count, shadow.trade_count)
    entry_rate = 1.0 if denominator == 0 else entry_matches / denominator
    exit_rate = 1.0 if denominator == 0 else exit_matches / denominator
    return_delta = abs(primary.strategy.total_return - shadow.total_return)
    drawdown_delta = abs(primary.strategy.max_drawdown - shadow.max_drawdown)
    sharpe_delta = abs(primary.strategy.sharpe_ratio - shadow.sharpe_ratio)
    cost_delta = abs(primary.total_cost - shadow.total_cost)
    _append_metric_mismatch(
        mismatches, "RETURN", return_delta, RETURN_TOLERANCE,
        primary.strategy.total_return, shadow.total_return, "累计收益",
    )
    _append_metric_mismatch(
        mismatches, "COST", cost_delta, COST_TOLERANCE,
        primary.total_cost, shadow.total_cost, "累计成本",
    )
    _append_metric_mismatch(
        mismatches, "MAX_DRAWDOWN", drawdown_delta, MAX_DRAWDOWN_TOLERANCE,
        primary.strategy.max_drawdown, shadow.max_drawdown, "最大回撤",
    )
    _append_metric_mismatch(
        mismatches, "SHARPE", sharpe_delta, SHARPE_TOLERANCE,
        primary.strategy.sharpe_ratio, shadow.sharpe_ratio, "Sharpe",
    )
    return BacktestAudit(
        status="PASS" if not mismatches else "WARNING",
        primary_engine=primary_metrics,
        shadow_engine=shadow_metrics,
        trade_count_agreement=trade_count_agreement,
        entry_date_agreement_rate=entry_rate,
        exit_date_agreement_rate=exit_rate,
        return_delta=return_delta,
        max_drawdown_delta=drawdown_delta,
        sharpe_delta=sharpe_delta,
        cost_delta=cost_delta,
        duration_ms=shadow.duration_ms,
        mismatches=mismatches,
        limitations=limitations,
    )


def _primary_metrics(report: BacktestReport) -> AuditEngineMetrics:
    return AuditEngineMetrics(
        engine="FIN_SCOPE",
        trade_count=report.trade_count,
        total_return=report.strategy.total_return,
        max_drawdown=report.strategy.max_drawdown,
        sharpe_ratio=report.strategy.sharpe_ratio,
        total_cost=report.total_cost,
    )


def _shadow_metrics(report: ShadowBacktestResult) -> AuditEngineMetrics:
    return AuditEngineMetrics(
        engine="BACKTESTING_PY",
        trade_count=report.trade_count,
        total_return=report.total_return,
        max_drawdown=report.max_drawdown,
        sharpe_ratio=report.sharpe_ratio,
        total_cost=report.total_cost,
    )


def _append_metric_mismatch(
    mismatches: list[AuditMismatch],
    category: str,
    delta: float,
    tolerance: float,
    primary_value: float,
    shadow_value: float,
    label: str,
) -> None:
    if delta <= tolerance:
        return
    mismatches.append(
        AuditMismatch(
            category=category,
            primary_value=primary_value,
            shadow_value=shadow_value,
            detail=f"{label}差异超过审计容差 {tolerance:.4f}",
        )
    )
