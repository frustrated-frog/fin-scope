from __future__ import annotations

from datetime import date, datetime

import pandas as pd

from finscope_market_data.breadth import MarketBreadthService
from finscope_market_data.models import (
    DailyBar,
    DataCapability,
    DataEnvelope,
    QualityStatus,
    StockSymbol,
)
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
        today_provider=lambda: date(2026, 8, 21),
        calendar_loader=lambda: pd.DataFrame([{"trade_date": "2026-08-21"}]),
    )

    result = service.fetch(date(2026, 8, 21))

    assert result.schema_version == "market-breadth-v2"
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


def test_default_retrieved_at_contains_timezone_offset() -> None:
    service = MarketBreadthService(
        eastmoney_loader=lambda: pd.DataFrame(
            [{"代码": "600001", "最新价": 10.2, "涨跌幅": 2.0, "成交额": 100}]
        ),
        sina_loader=lambda: pd.DataFrame(),
        limit_up_loader=lambda _: pd.DataFrame(),
        limit_down_loader=lambda _: pd.DataFrame(),
        today_provider=lambda: date(2026, 8, 21),
        calendar_loader=lambda: pd.DataFrame([{"trade_date": "2026-08-21"}]),
    )

    result = service.fetch(date(2026, 8, 21))

    assert result.retrieved_at.utcoffset() is not None


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
        today_provider=lambda: date(2026, 8, 21),
        calendar_loader=lambda: pd.DataFrame([{"trade_date": "2026-08-21"}]),
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
        today_provider=lambda: date(2026, 8, 21),
        calendar_loader=lambda: pd.DataFrame([{"trade_date": "2026-08-21"}]),
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
        today_provider=lambda: date(2026, 8, 21),
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
        today_provider=lambda: date(2026, 8, 21),
        snapshot_store=store,
    ).fetch(date(2026, 8, 21))

    assert fallback.quality_status == "STALE_FALLBACK"
    assert fallback.valid_count == 1
    assert any("快照" in warning for warning in fallback.warnings)


def test_closed_business_date_uses_exact_snapshot_without_online_retry(tmp_path) -> None:
    store = SnapshotStore(tmp_path / "snapshots.db")
    MarketBreadthService(
        eastmoney_loader=lambda: pd.DataFrame(
            [{"代码": "600001", "最新价": 10.2, "涨跌幅": 2.0, "成交额": 100}]
        ),
        sina_loader=lambda: pd.DataFrame(),
        limit_up_loader=lambda _: pd.DataFrame(),
        limit_down_loader=lambda _: pd.DataFrame(),
        now_provider=lambda: datetime(2026, 8, 21, 15, 20),
        today_provider=lambda: date(2026, 8, 21),
        snapshot_store=store,
    ).fetch(date(2026, 8, 21))
    calls = 0

    def fail() -> pd.DataFrame:
        nonlocal calls
        calls += 1
        raise RuntimeError("online should not run")

    result = MarketBreadthService(
        eastmoney_loader=fail,
        sina_loader=fail,
        limit_up_loader=lambda _: pd.DataFrame(),
        limit_down_loader=lambda _: pd.DataFrame(),
        today_provider=lambda: date(2026, 8, 23),
        snapshot_store=store,
    ).fetch(date(2026, 8, 21))

    assert calls == 0
    assert result.quality_status == "FRESH_PRIMARY"


def test_missing_closed_business_date_never_uses_current_spot_data(tmp_path) -> None:
    calls = 0

    def current_spot() -> pd.DataFrame:
        nonlocal calls
        calls += 1
        return pd.DataFrame(
            [{"代码": "600001", "最新价": 10.2, "涨跌幅": 2.0, "成交额": 100}]
        )

    service = MarketBreadthService(
        eastmoney_loader=current_spot,
        sina_loader=current_spot,
        limit_up_loader=lambda _: pd.DataFrame(),
        limit_down_loader=lambda _: pd.DataFrame(),
        today_provider=lambda: date(2026, 8, 24),
        snapshot_store=SnapshotStore(tmp_path / "snapshots.db"),
        calendar_loader=lambda: pd.DataFrame([{"trade_date": "2026-08-24"}]),
    )

    try:
        service.fetch(date(2026, 8, 21))
        raise AssertionError("closed date without a snapshot must be unavailable")
    except RuntimeError as error:
        assert "历史快照" in str(error)

    assert calls == 0


def test_closed_business_date_uses_local_daily_bar_panel_with_partial_quality(tmp_path) -> None:
    store = SnapshotStore(tmp_path / "snapshots.db")
    values = (("SH", "600001", 10.0, 11.0), ("SZ", "000001", 20.0, 18.0))
    for market, code, previous, current in values:
        symbol = StockSymbol(market=market, code=code)
        bars = [
            DailyBar(symbol=symbol, trade_date="2026-08-20", open=previous,
                     high=previous, low=previous, close=previous, volume=100,
                     amount=100.0, adjustment="QFQ"),
            DailyBar(symbol=symbol, trade_date="2026-08-21", open=current,
                     high=current, low=current, close=current, volume=100,
                     amount=200.0, adjustment="QFQ"),
        ]
        store.save(DataEnvelope[list[DailyBar]](
            capability=DataCapability.DAILY_BARS,
            symbol=symbol,
            quality_status=QualityStatus.FRESH_FALLBACK,
            source_code="FIXTURE",
            source_family="FIXTURE",
            retrieved_at=datetime(2026, 8, 22, 12, 0),
            data=bars,
        ))
    calls = 0

    def current_spot() -> pd.DataFrame:
        nonlocal calls
        calls += 1
        return pd.DataFrame()

    result = MarketBreadthService(
        eastmoney_loader=current_spot,
        sina_loader=current_spot,
        limit_up_loader=lambda _: pd.DataFrame([{"代码": "600001"}]),
        limit_down_loader=lambda _: pd.DataFrame([{"代码": "000001"}]),
        today_provider=lambda: date(2026, 8, 24),
        now_provider=lambda: datetime(2026, 8, 24, 12, 0),
        snapshot_store=store,
        calendar_loader=lambda: pd.DataFrame([{"trade_date": "2026-08-24"}]),
    ).fetch(date(2026, 8, 21))

    assert calls == 0
    assert result.source_code == "LOCAL_DAILY_BAR_PANEL"
    assert result.quality_status == "PARTIAL_FRESH"
    assert result.advance_count == 1
    assert result.decline_count == 1
    assert result.total_amount == 400.0
    assert result.median_change_pct == 0.0
    assert any("2 只" in warning and "不代表完整全A" in warning for warning in result.warnings)


def test_daily_bar_panel_calculates_market_internal_structure_and_sixty_day_history(tmp_path) -> None:
    store = SnapshotStore(tmp_path / "snapshots.db")
    trade_dates = pd.bdate_range(end="2026-08-21", periods=260)
    series = (
        ("SH", "600001", [100.0 + index for index in range(260)]),
        ("SZ", "000001", [400.0 - index for index in range(260)]),
        ("SZ", "300001", [200.0] * 259 + [210.0]),
    )
    for market, code, closes in series:
        symbol = StockSymbol(market=market, code=code)
        bars = [
            DailyBar(
                symbol=symbol,
                trade_date=trade_date.date().isoformat(),
                open=close,
                high=close,
                low=close,
                close=close,
                volume=100,
                amount=200.0,
                adjustment="QFQ",
            )
            for trade_date, close in zip(trade_dates, closes)
        ]
        store.save(DataEnvelope[list[DailyBar]](
            capability=DataCapability.DAILY_BARS,
            symbol=symbol,
            quality_status=QualityStatus.FRESH_FALLBACK,
            source_code="FIXTURE",
            source_family="FIXTURE",
            retrieved_at=datetime(2026, 8, 22, 12, 0),
            data=bars,
        ))

    result = MarketBreadthService(
        eastmoney_loader=lambda: pd.DataFrame(),
        sina_loader=lambda: pd.DataFrame(),
        limit_up_loader=lambda _: pd.DataFrame(),
        limit_down_loader=lambda _: pd.DataFrame(),
        today_provider=lambda: date(2026, 8, 24),
        now_provider=lambda: datetime(2026, 8, 24, 12, 0),
        snapshot_store=store,
        calendar_loader=lambda: pd.DataFrame([{"trade_date": "2026-08-24"}]),
    ).fetch(date(2026, 8, 21))

    assert result.schema_version == "market-breadth-v2"
    assert sum(bucket.count for bucket in result.return_distribution) == 3
    assert next(bucket for bucket in result.return_distribution if bucket.code == "UP_3_7").count == 1
    assert result.trend_breadth.ma20_ratio == 2 / 3
    assert result.trend_breadth.ma60_ratio == 2 / 3
    assert result.trend_breadth.ma120_ratio == 2 / 3
    assert result.trend_breadth.ma250_ratio == 2 / 3
    assert result.new_high_low.high20_count == 2
    assert result.new_high_low.low20_count == 1
    assert result.new_high_low.high250_count == 2
    assert result.new_high_low.low250_count == 1
    assert result.net_advances == 1
    assert result.advance_decline_line == 1
    assert len(result.history) == 60
    assert result.history[-1].business_date == "2026-08-21"
    assert result.history[-1].ma20_ratio == 2 / 3
    assert result.history[-1].new_high20_count == 2
    assert result.history[-1].new_low20_count == 1
    assert result.history[-1].advance_decline_line == 1


def test_weekday_holiday_accepts_the_latest_calendar_trade_date(tmp_path) -> None:
    service = MarketBreadthService(
        eastmoney_loader=lambda: pd.DataFrame(
            [{"代码": "600001", "最新价": 10.2, "涨跌幅": 2.0, "成交额": 100}]
        ),
        sina_loader=lambda: pd.DataFrame(),
        limit_up_loader=lambda _: pd.DataFrame(),
        limit_down_loader=lambda _: pd.DataFrame(),
        today_provider=lambda: date(2026, 10, 5),
        calendar_loader=lambda: pd.DataFrame([{"trade_date": "2026-10-02"}]),
        snapshot_store=SnapshotStore(tmp_path / "snapshots.db"),
    )

    result = service.fetch(date(2026, 10, 2))

    assert result.business_date == "2026-10-02"
    assert result.valid_count == 1


def test_weekday_holiday_date_never_labels_current_spot_as_that_date(tmp_path) -> None:
    calls = 0

    def current_spot() -> pd.DataFrame:
        nonlocal calls
        calls += 1
        return pd.DataFrame(
            [{"代码": "600001", "最新价": 10.2, "涨跌幅": 2.0, "成交额": 100}]
        )

    service = MarketBreadthService(
        eastmoney_loader=current_spot,
        sina_loader=current_spot,
        limit_up_loader=lambda _: pd.DataFrame(),
        limit_down_loader=lambda _: pd.DataFrame(),
        today_provider=lambda: date(2026, 10, 5),
        calendar_loader=lambda: pd.DataFrame([{"trade_date": "2026-10-02"}]),
        snapshot_store=SnapshotStore(tmp_path / "snapshots.db"),
    )

    try:
        service.fetch(date(2026, 10, 5))
        raise AssertionError("weekday holiday must not be labeled with current spot")
    except RuntimeError as error:
        assert "最近交易日" in str(error)

    assert calls == 0


def test_future_business_date_never_uses_current_spot_data(tmp_path) -> None:
    calls = 0

    def current_spot() -> pd.DataFrame:
        nonlocal calls
        calls += 1
        return pd.DataFrame(
            [{"代码": "600001", "最新价": 10.2, "涨跌幅": 2.0, "成交额": 100}]
        )

    service = MarketBreadthService(
        eastmoney_loader=current_spot,
        sina_loader=current_spot,
        limit_up_loader=lambda _: pd.DataFrame(),
        limit_down_loader=lambda _: pd.DataFrame(),
        today_provider=lambda: date(2026, 8, 21),
        calendar_loader=lambda: pd.DataFrame([{"trade_date": "2026-08-21"}]),
        snapshot_store=SnapshotStore(tmp_path / "snapshots.db"),
    )

    try:
        service.fetch(date(2026, 8, 24))
        raise AssertionError("future date must not use current spot")
    except RuntimeError as error:
        assert "最近交易日" in str(error)

    assert calls == 0
