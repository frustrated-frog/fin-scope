from __future__ import annotations

from datetime import date, datetime

import pandas as pd

from finscope_market_data.breadth import MarketBreadthService
from finscope_market_data.snapshot_store import SnapshotStore


def test_eastmoney_snapshot_calculates_core_market_breadth() -> None:
    service = MarketBreadthService(
        eastmoney_loader=lambda: pd.DataFrame(
            [
                {"代码": "600001", "名称": "样本一", "最新价": 10.2, "涨跌幅": 2.0, "成交额": 100},
                {"代码": "000001", "名称": "样本二", "最新价": 8.1, "涨跌幅": -1.0, "成交额": 200},
                {"代码": "300001", "名称": "样本三", "最新价": 12.0, "涨跌幅": 0.0, "成交额": 300},
                {"代码": "BAD", "名称": "无效", "最新价": None, "涨跌幅": None, "成交额": 999},
            ]
        ),
        sina_loader=lambda: pd.DataFrame(),
        limit_up_loader=lambda _: pd.DataFrame([{"代码": "600001"}]),
        limit_down_loader=lambda _: pd.DataFrame([{"代码": "000001"}]),
        now_provider=lambda: datetime(2026, 8, 21, 15, 20),
    )

    result = service.fetch(date(2026, 8, 21))

    assert result.schema_version == "market-breadth-v1"
    assert result.business_date == "2026-08-21"
    assert result.source_code == "AKSHARE_EASTMONEY_A_SPOT"
    assert result.source_family == "EASTMONEY"
    assert result.quality_status == "FRESH_PRIMARY"
    assert result.advance_count == 1
    assert result.decline_count == 1
    assert result.flat_count == 1
    assert result.valid_count == 3
    assert result.advance_ratio == 1 / 3
    assert result.total_amount == 600
    assert result.limit_up_count == 1
    assert result.limit_down_count == 1
    assert result.median_change_pct == 0


def test_sina_is_used_when_eastmoney_fails() -> None:
    def fail():
        raise RuntimeError("eastmoney unavailable")

    service = MarketBreadthService(
        eastmoney_loader=fail,
        sina_loader=lambda: pd.DataFrame(
            [
                {"code": "sh600001", "name": "样本一", "trade": 10.2, "changepercent": 1.5, "amount": 100},
                {"code": "sz000001", "name": "样本二", "trade": 8.1, "changepercent": -0.5, "amount": 200},
            ]
        ),
        limit_up_loader=lambda _: pd.DataFrame(),
        limit_down_loader=lambda _: pd.DataFrame(),
        now_provider=lambda: datetime(2026, 8, 21, 15, 20),
    )

    result = service.fetch(date(2026, 8, 21))

    assert result.source_family == "SINA"
    assert result.quality_status == "FRESH_FALLBACK"
    assert result.valid_count == 2
    assert any("东方财富" in warning for warning in result.warnings)


def test_limit_pool_failure_keeps_breadth_and_marks_partial_quality() -> None:
    def fail_pool(_: str):
        raise RuntimeError("pool unavailable")

    service = MarketBreadthService(
        eastmoney_loader=lambda: pd.DataFrame(
            [{"代码": "600001", "名称": "样本一", "最新价": 10.2, "涨跌幅": 2.0, "成交额": 100}]
        ),
        sina_loader=lambda: pd.DataFrame(),
        limit_up_loader=fail_pool,
        limit_down_loader=fail_pool,
        now_provider=lambda: datetime(2026, 8, 21, 15, 20),
    )

    result = service.fetch(date(2026, 8, 21))

    assert result.quality_status == "PARTIAL_FRESH"
    assert result.limit_up_count is None
    assert result.limit_down_count is None
    assert len(result.warnings) == 2


def test_same_business_date_snapshot_is_used_when_online_sources_fail(tmp_path) -> None:
    store = SnapshotStore(tmp_path / "snapshots.db")
    online = MarketBreadthService(
        eastmoney_loader=lambda: pd.DataFrame(
            [{"代码": "600001", "名称": "样本一", "最新价": 10.2, "涨跌幅": 2.0, "成交额": 100}]
        ),
        sina_loader=lambda: pd.DataFrame(),
        limit_up_loader=lambda _: pd.DataFrame(),
        limit_down_loader=lambda _: pd.DataFrame(),
        now_provider=lambda: datetime(2026, 8, 21, 15, 20),
        snapshot_store=store,
    )
    online.fetch(date(2026, 8, 21))

    def fail():
        raise RuntimeError("offline")

    fallback = MarketBreadthService(
        eastmoney_loader=fail,
        sina_loader=fail,
        limit_up_loader=lambda _: pd.DataFrame(),
        limit_down_loader=lambda _: pd.DataFrame(),
        now_provider=lambda: datetime(2026, 8, 21, 15, 25),
        snapshot_store=store,
    ).fetch(date(2026, 8, 21))

    assert fallback.quality_status == "STALE_FALLBACK"
    assert fallback.valid_count == 1
    assert any("快照" in warning for warning in fallback.warnings)
