from __future__ import annotations

import pytest
from pydantic import ValidationError

from finscope_market_data.forecast.schemas import (
    AuditEngineMetrics,
    AuditMismatch,
    BacktestAudit,
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
