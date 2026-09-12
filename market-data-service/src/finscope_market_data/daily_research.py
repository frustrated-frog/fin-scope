"""Bounded retrospective research over local snapshots; never triggers ingestion."""
from __future__ import annotations

from datetime import date, datetime, time, timedelta
from functools import lru_cache
from math import isfinite
from statistics import median
from typing import Callable, Literal
from zoneinfo import ZoneInfo

from pydantic import BaseModel, ConfigDict, Field

from finscope_market_data.forecast.trading_calendar import next_session
from finscope_market_data.models import DailyBar
from finscope_market_data.snapshot_store import SnapshotStore


class ResearchModel(BaseModel):
    model_config = ConfigDict(extra="forbid", allow_inf_nan=False)


class ResearchStock(ResearchModel):
    instrument_code: str
    return_1d: float | None = None
    return_5d: float | None = None
    return_20d: float | None = None
    amount: float | None = Field(default=None, ge=0)
    group_codes: list[str] = Field(default_factory=list)


class ResearchGroup(ResearchModel):
    code: Literal["STRONG", "TREND", "BREAKOUT"]
    label: str
    definition: str
    eligible_count: int = Field(default=0, ge=0)
    member_count: int = Field(default=0, ge=0)
    valid_count: int = Field(default=0, ge=0)
    advance_ratio: float | None = Field(default=None, ge=0, le=1)
    median_return: float | None = None
    members: list[str] = Field(default_factory=list)


class DailyResearchSnapshot(ResearchModel):
    schema_version: Literal["daily-research-v1"] = "daily-research-v1"
    business_date: date
    selection_date: date | None = None
    source_code: Literal["LOCAL_DAILY_BAR_PANEL"] = "LOCAL_DAILY_BAR_PANEL"
    quality_status: Literal["PARTIAL", "UNAVAILABLE"] = "UNAVAILABLE"
    sample_count: int = Field(default=0, ge=0)
    stocks: list[ResearchStock] = Field(default_factory=list, max_length=10000)
    groups: list[ResearchGroup]
    warnings: list[str]


@lru_cache(maxsize=4096)
def _previous_session(day: date) -> date | None:
    for offset in range(1, 32):
        candidate = day - timedelta(days=offset)
        if next_session(candidate - timedelta(days=1)) == candidate:
            return candidate
    return None


def _finite(value: float | None) -> bool:
    return value is not None and isfinite(value)


def _a_stock(key: str) -> bool:
    market, _, code = key.partition(":")
    if len(code) != 6 or not code.isdigit():
        return False
    return ((market == "SH" and code.startswith(("600", "601", "603", "605", "688")))
            or (market == "SZ" and code.startswith(("000", "001", "002", "003", "300", "301")))
            or (market == "BJ" and code.startswith(("43", "83", "87", "88", "92"))))


def _window(bars: dict[date, DailyBar], end: date, count: int) -> list[DailyBar] | None:
    result = []
    current = end
    for index in range(count):
        bar = bars.get(current)
        if bar is None or not _finite(bar.close) or bar.close <= 0 or bar.adjustment != "QFQ":
            return None
        result.append(bar)
        if index < count - 1:
            current = _previous_session(current)
            if current is None:
                return None
    return list(reversed(result))


def _return(bars: dict[date, DailyBar], day: date, sessions: int) -> float | None:
    bar = bars.get(day)
    if bar is None or not _finite(bar.close) or bar.close <= 0:
        return None
    if sessions == 1 and _finite(bar.change_pct):
        return bar.change_pct
    window = _window(bars, day, sessions + 1)
    if window is None:
        return None
    result = (window[-1].close / window[0].close - 1) * 100
    return result if isfinite(result) else None


class DailyResearchService:
    def __init__(self, snapshots: SnapshotStore, now: Callable[[], datetime] | None = None):
        self.snapshots = snapshots
        self.now = now or (lambda: datetime.now(ZoneInfo("Asia/Shanghai")))

    def fetch(self, business_date: date) -> DailyResearchSnapshot:
        groups = [
            ResearchGroup(code="STRONG", label="强势股", definition="前一交易日涨幅至少3%"),
            ResearchGroup(code="TREND", label="趋势股", definition="前一交易日收盘高于20日均线且5日收益为正（前复权连续交易日）"),
            ResearchGroup(code="BREAKOUT", label="突破股", definition="前一交易日收盘高于此前20个交易日最高收盘（前复权连续交易日）"),
        ]
        result = DailyResearchSnapshot(business_date=business_date, groups=groups, warnings=[
            "仅使用本地日线样本，最多10000只，不代表全市场；没有发起远程采集。",
            "样本数为已检查的本地A股数量，包含当日缺失及未入组证券；不等于当日有效收益数。",
            "本地快照可被后续抓取和前复权重算覆盖，不是不可变的历史时点数据库。",
            "多日收益及均线仅采用连续交易日前复权收盘；单日可采用供应商涨跌幅。",
            "分组在前一交易日确定；当日缺失仍计成员，有效成员少于5只时不展示分组统计。",
        ])
        now = self.now().astimezone(ZoneInfo("Asia/Shanghai"))
        if (business_date == date.min or business_date > now.date()
                or (business_date == now.date() and now.time() < time(15, 30))
                or next_session(business_date - timedelta(days=1)) != business_date):
            result.warnings.append("请求日期尚未收盘、不是交易日或超出已维护交易日历，未回退到其他日期。")
            return result
        selection_date = _previous_session(business_date)
        if selection_date is None:
            result.warnings.append("前一交易日不可验证。")
            return result
        result.selection_date = selection_date
        panel = self.snapshots.load_daily_bar_panel(
            business_date.isoformat(), max_bars_per_symbol=22,
            max_symbols=10000, warnings=result.warnings,
        )
        for key, raw_bars in panel.items():
            if not _a_stock(key):
                continue
            result.sample_count += 1
            bars: dict[date, DailyBar] = {}
            duplicates: set[date] = set()
            for bar in raw_bars:
                try:
                    day = date.fromisoformat(bar.trade_date)
                except ValueError:
                    continue
                if bar.symbol.cache_key != key or day > business_date:
                    continue
                if day in bars:
                    duplicates.add(day)
                bars[day] = bar
            for day in duplicates:
                bars.pop(day, None)
            instrument = key.split(":")[1] + "." + key.split(":")[0]
            prior_return = _return(bars, selection_date, 1)
            trend_window = _window(bars, selection_date, 20)
            prior_five = _return(bars, selection_date, 5)
            breakout_window = _window(bars, selection_date, 21)
            decisions = [
                None if prior_return is None else prior_return >= 3,
                None if trend_window is None or prior_five is None else (
                    trend_window[-1].close > sum(bar.close for bar in trend_window) / 20 and prior_five > 0),
                None if breakout_window is None else (
                    breakout_window[-1].close > max(bar.close for bar in breakout_window[:-1])),
            ]
            memberships = []
            for group, selected in zip(groups, decisions):
                if selected is not None:
                    group.eligible_count += 1
                if selected:
                    memberships.append(group.code)
                    group.members.append(instrument)
            current = bars.get(business_date)
            valid_current = current is not None and _finite(current.close) and current.close > 0
            if not valid_current and not memberships:
                continue
            result.stocks.append(ResearchStock(
                instrument_code=instrument,
                return_1d=_return(bars, business_date, 1),
                return_5d=_return(bars, business_date, 5),
                return_20d=_return(bars, business_date, 20),
                amount=current.amount if valid_current and _finite(current.amount) and current.amount >= 0 else None,
                group_codes=memberships,
            ))
        for group in groups:
            values = [stock.return_1d for stock in result.stocks
                      if group.code in stock.group_codes and stock.return_1d is not None]
            group.member_count = len(group.members)
            group.valid_count = len(values)
            if len(values) >= 5:
                group.advance_ratio = sum(value > 0 for value in values) / len(values)
                group.median_return = median(values)
        if any(stock.return_1d is not None for stock in result.stocks):
            result.quality_status = "PARTIAL"
        else:
            result.warnings.append("请求日期没有可计算单日收益的本地股票样本。")
        return result
