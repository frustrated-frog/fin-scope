from __future__ import annotations

import pytest
from pydantic import ValidationError

from finscope_market_data.forecast.schemas import (
    AuditEngineMetrics,
    AuditMismatch,
    BacktestAudit,
)
from finscope_market_data.forecast.backtest_audit import audit_backtests
from finscope_market_data.forecast.backtesting_adapter import (
    ShadowBacktestResult,
    ShadowTrade,
)
from finscope_market_data.forecast.performance import (
    BacktestReport,
    PerformanceSummary,
    SimulatedTrade,
)


def engine(name: str = "FIN_SCOPE") -> AuditEngineMetrics:
    return AuditEngineMetrics(
        engine=name,
        trade_count=2,
        total_return=0.12,
        max_drawdown=0.08,
        sharpe_ratio=0.9,
        total_cost=0.003,
    )


def test_backtest_audit_serializes_a_strict_shadow_contract() -> None:
    audit = BacktestAudit(
        status="PASS",
        primary_engine=engine(),
        shadow_engine=engine("BACKTESTING_PY"),
        trade_count_agreement=True,
        entry_date_agreement_rate=1.0,
        exit_date_agreement_rate=1.0,
        return_delta=0.0001,
        max_drawdown_delta=0.0002,
        sharpe_delta=0.001,
        cost_delta=0.0,
        duration_ms=12,
        mismatches=[],
        limitations=["影子验证不参与本期方向决策"],
    )

    payload = audit.model_dump(mode="json", by_alias=True)

    assert payload["status"] == "PASS"
    assert payload["mode"] == "SHADOW"
    assert payload["primaryEngine"]["engine"] == "FIN_SCOPE"
    assert payload["shadowEngine"]["engine"] == "BACKTESTING_PY"
    assert payload["entryDateAgreementRate"] == 1.0


def test_backtest_audit_rejects_invalid_status_and_agreement_rate() -> None:
    with pytest.raises(ValidationError):
        BacktestAudit(
            status="SUCCESS",
            primary_engine=engine(),
            trade_count_agreement=True,
            entry_date_agreement_rate=1.1,
            exit_date_agreement_rate=1.0,
            return_delta=0.0,
            max_drawdown_delta=0.0,
            sharpe_delta=0.0,
            cost_delta=0.0,
            duration_ms=0,
        )


def test_audit_mismatch_keeps_trade_level_evidence() -> None:
    mismatch = AuditMismatch(
        category="ENTRY_DATE",
        trade_index=1,
        primary_value="2026-08-03",
        shadow_value="2026-08-04",
        detail="下一交易日成交日期不一致",
    )

    assert mismatch.trade_index == 1
    assert mismatch.category == "ENTRY_DATE"


def summary(total_return: float = 0.12) -> PerformanceSummary:
    return PerformanceSummary(
        total_return=total_return,
        annualized_return=0.1,
        annualized_volatility=0.12,
        sharpe_ratio=0.9,
        daily_win_rate=0.55,
        max_drawdown=0.08,
        max_drawdown_start_date="2026-01-01",
        max_drawdown_trough_date="2026-01-03",
        max_drawdown_recovery_date="2026-01-05",
        max_drawdown_duration_days=4,
    )


def native_report() -> BacktestReport:
    trade = SimulatedTrade(
        signal_date="2026-01-01",
        entry_date="2026-01-02",
        exit_date="2026-01-08",
        probability=0.7,
        net_return=0.04,
        cost=0.0015,
        holding_days=5,
    )
    return BacktestReport(
        benchmark_label="同股买入并持有",
        strategy=summary(),
        benchmark=summary(0.08),
        excess_return=0.04,
        trade_count=1,
        profitable_trade_rate=1.0,
        turnover=1.0,
        total_cost=0.0015,
        holding_time_ratio=0.5,
        average_holding_days=5.0,
        trades=(trade,),
        equity_curve=(),
    )


def shadow_result(
    *, entry_date: str = "2026-01-02", available: bool = True
) -> ShadowBacktestResult:
    return ShadowBacktestResult(
        available=available,
        trade_count=1 if available else 0,
        total_return=0.1201 if available else 0.0,
        max_drawdown=0.0801 if available else 0.0,
        sharpe_ratio=0.901 if available else 0.0,
        total_cost=0.0015 if available else 0.0,
        trades=(
            ShadowTrade(
                signal_date="2026-01-01",
                entry_date=entry_date,
                exit_date="2026-01-08",
                net_return=0.0401,
                cost=0.0015,
            ),
        ) if available else (),
        duration_ms=18,
        error=None if available else "影子回测引擎执行失败",
    )


def test_differential_audit_passes_matching_ledgers_within_tolerance() -> None:
    audit = audit_backtests(native_report(), shadow_result())

    assert audit.status == "PASS"
    assert audit.trade_count_agreement is True
    assert audit.entry_date_agreement_rate == 1.0
    assert audit.exit_date_agreement_rate == 1.0
    assert audit.mismatches == []


def test_differential_audit_reports_trade_level_date_mismatch() -> None:
    audit = audit_backtests(
        native_report(), shadow_result(entry_date="2026-01-03")
    )

    assert audit.status == "WARNING"
    assert audit.entry_date_agreement_rate == 0.0
    assert audit.mismatches[0].category == "ENTRY_DATE"
    assert audit.mismatches[0].trade_index == 1


def test_differential_audit_degrades_without_blocking_primary_report() -> None:
    audit = audit_backtests(native_report(), shadow_result(available=False))

    assert audit.status == "UNAVAILABLE"
    assert audit.primary_engine.trade_count == 1
    assert audit.shadow_engine is None
    assert audit.limitations


def test_differential_audit_warns_when_risk_metrics_diverge() -> None:
    shadow = shadow_result()
    shadow = ShadowBacktestResult(
        **{
            **shadow.__dict__,
            "max_drawdown": 0.30,
            "sharpe_ratio": -0.2,
        }
    )

    audit = audit_backtests(native_report(), shadow)

    assert audit.status == "WARNING"
    assert {item.category for item in audit.mismatches} >= {"MAX_DRAWDOWN", "SHARPE"}
