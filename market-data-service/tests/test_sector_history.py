from __future__ import annotations

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
    frame = pd.concat(
        [
            history_frame("2026-07-20", 25),
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
    assert result.schema_version == "sector-history-v1"
    assert result.business_date == "2026-08-21"
    assert result.quality_status == "FRESH_PRIMARY"
    assert result.covered_trade_dates[-1] == "2026-08-21"
    assert item.last_trade_date == "2026-08-21"
    assert item.return_1d == pytest.approx((124 / 123 - 1) * 100)
    assert item.return_5d == pytest.approx((124 / 119 - 1) * 100)
    assert item.return_20d == pytest.approx((124 / 104 - 1) * 100)
    assert item.positive_days_5 == 5
    assert item.coverage_days == 25


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
