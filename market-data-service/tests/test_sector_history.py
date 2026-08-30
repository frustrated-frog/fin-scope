from __future__ import annotations

import threading
from datetime import date, datetime

import pandas as pd
import pytest

from finscope_market_data.sector_history import TonghuashunSectorHistoryService


def history_frame(start: str, count: int, base: float = 100.0) -> pd.DataFrame:
    dates = pd.bdate_range(start=start, periods=count)
    return pd.DataFrame(
        {
            "日期": dates,
            "收盘价": [base + index for index in range(count)],
        }
    )


def test_sector_history_calculates_returns_without_future_leakage() -> None:
    dates = pd.bdate_range(end="2026-08-21", periods=40)
    frame = pd.concat(
        [
            pd.DataFrame(
                {"日期": dates, "收盘价": [100.0 + index for index in range(40)]}
            ),
            pd.DataFrame([{"日期": "2026-08-24", "收盘价": 999.0}]),
        ],
        ignore_index=True,
    )
    service = TonghuashunSectorHistoryService(
        catalog_loader=lambda: pd.DataFrame(
            [{"name": "半导体", "code": "881121"}]
        ),
        history_loader=lambda name, start, end: frame,
        now_provider=lambda: datetime(2026, 8, 23, 18, 0),
        max_workers=1,
    )

    result = service.fetch(date(2026, 8, 21), window=20)

    item = result.entries[0]
    assert result.schema_version == "sector-history-v2"
    assert result.business_date == "2026-08-21"
    assert result.quality_status == "FRESH_PRIMARY"
    assert result.covered_trade_dates[-1] == "2026-08-21"
    assert item.last_trade_date == "2026-08-21"
    assert item.return_1d == pytest.approx((139 / 138 - 1) * 100)
    assert item.return_5d == pytest.approx((139 / 134 - 1) * 100)
    assert item.return_20d == pytest.approx((139 / 119 - 1) * 100)
    assert item.positive_days_5 == 5
    assert item.coverage_days == 40
    assert len(item.rotation_trail) == 10
    assert item.rotation_trail[-1].business_date == "2026-08-21"
    assert [point.business_date for point in item.rotation_trail] == sorted(
        point.business_date for point in item.rotation_trail
    )
    assert all(point.relative_strength == 0 for point in item.rotation_trail)
    assert all(point.relative_momentum == 0 for point in item.rotation_trail)


def test_sector_history_builds_cross_sectional_relative_strength_trails() -> None:
    dates = pd.bdate_range(end="2026-08-21", periods=45)

    def load(name: str, start: str, end: str) -> pd.DataFrame:
        step = 2.0 if name == "半导体" else 0.2
        return pd.DataFrame(
            {"日期": dates, "收盘价": [100.0 + step * index for index in range(45)]}
        )

    service = TonghuashunSectorHistoryService(
        catalog_loader=lambda: pd.DataFrame(
            [
                {"name": "半导体", "code": "881121"},
                {"name": "白酒", "code": "881273"},
            ]
        ),
        history_loader=load,
        max_workers=1,
    )

    result = service.fetch(date(2026, 8, 21), window=40)

    by_code = {item.code: item for item in result.entries}
    assert len(by_code["881121"].rotation_trail) == 10
    assert by_code["881121"].rotation_trail[-1].relative_strength > 0
    assert by_code["881273"].rotation_trail[-1].relative_strength < 0
    assert all(
        point.business_date <= "2026-08-21"
        for item in result.entries
        for point in item.rotation_trail
    )


def test_sector_history_keeps_successful_industries_when_one_loader_fails() -> None:
    def load(name: str, start: str, end: str) -> pd.DataFrame:
        if name == "白酒":
            raise RuntimeError("upstream rejected")
        return history_frame("2026-07-20", 25)

    service = TonghuashunSectorHistoryService(
        catalog_loader=lambda: pd.DataFrame(
            [
                {"name": "半导体", "code": "881121"},
                {"name": "白酒", "code": "881273"},
            ]
        ),
        history_loader=load,
        max_workers=2,
    )

    result = service.fetch(date(2026, 8, 21), window=20)

    assert [item.code for item in result.entries] == ["881121"]
    assert result.quality_status == "PARTIAL_FRESH"
    assert result.warnings == ["白酒(881273)行业历史不可用: upstream rejected"]


def test_sector_history_initializes_provider_on_the_calling_thread() -> None:
    calls: list[str] = []

    def load(name: str, start: str, end: str) -> pd.DataFrame:
        calls.append(name)
        if len(calls) == 1:
            assert threading.current_thread() is threading.main_thread()
        return history_frame("2026-07-20", 25)

    service = TonghuashunSectorHistoryService(
        catalog_loader=lambda: pd.DataFrame(
            [
                {"name": "半导体", "code": "881121"},
                {"name": "白酒", "code": "881273"},
            ]
        ),
        history_loader=load,
        max_workers=2,
    )

    result = service.fetch(date(2026, 8, 21), window=20)

    assert len(result.entries) == 2
    assert result.quality_status == "FRESH_PRIMARY"


def test_sector_history_rejects_an_empty_effective_result() -> None:
    service = TonghuashunSectorHistoryService(
        catalog_loader=lambda: pd.DataFrame(
            [{"name": "半导体", "code": "881121"}]
        ),
        history_loader=lambda name, start, end: pd.DataFrame(),
        max_workers=1,
    )

    with pytest.raises(RuntimeError, match="没有有效行业历史"):
        service.fetch(date(2026, 8, 21), window=20)
