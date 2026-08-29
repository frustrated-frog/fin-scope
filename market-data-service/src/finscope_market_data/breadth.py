from __future__ import annotations

import statistics
from collections import deque
from collections.abc import Callable
from datetime import date, datetime
from typing import Any
from zoneinfo import ZoneInfo

from finscope_market_data.models import (
    MarketBreadthSnapshot,
    MarketInternalHistoryPoint,
    MarketNewHighLow,
    MarketReturnDistributionBucket,
    MarketTrendBreadth,
    QualityStatus,
)
from finscope_market_data.snapshot_store import SnapshotStore


FrameLoader = Callable[[], Any]
PoolLoader = Callable[[str], Any]
CalendarLoader = Callable[[], Any]


class MarketBreadthService:
    def __init__(
        self,
        eastmoney_loader: FrameLoader | None = None,
        sina_loader: FrameLoader | None = None,
        limit_up_loader: PoolLoader | None = None,
        limit_down_loader: PoolLoader | None = None,
        now_provider: Callable[[], datetime] | None = None,
        today_provider: Callable[[], date] | None = None,
        snapshot_store: SnapshotStore | None = None,
        calendar_loader: CalendarLoader | None = None,
    ) -> None:
        self._eastmoney_loader = eastmoney_loader or self._load_eastmoney
        self._sina_loader = sina_loader or self._load_sina
        self._limit_up_loader = limit_up_loader or self._load_limit_up
        self._limit_down_loader = limit_down_loader or self._load_limit_down
        self._now_provider = now_provider or _shanghai_now
        self._today_provider = today_provider or (lambda: _shanghai_now().date())
        self._snapshot_store = snapshot_store
        self._calendar_loader = calendar_loader or self._load_calendar

    def fetch(self, business_date: date) -> MarketBreadthSnapshot:
        today = self._today_provider()
        frozen = None
        if self._snapshot_store is not None:
            frozen = self._snapshot_store.load_market_breadth(business_date.isoformat())
        latest_trade_date = self.latest_trade_date()
        if business_date != latest_trade_date:
            if frozen is not None:
                return frozen
            if business_date < latest_trade_date:
                return self._fetch_historical(business_date)
            raise RuntimeError("请求日期晚于最近交易日，禁止使用当前现货回填")
        if frozen is not None and latest_trade_date < today:
            return frozen
        try:
            result = self._fetch_online(business_date)
            if self._snapshot_store is not None:
                self._snapshot_store.save_market_breadth(result)
            return result
        except Exception as online_error:
            cached = (
                None
                if self._snapshot_store is None
                else self._snapshot_store.load_market_breadth(
                    business_date.isoformat()
                )
            )
            if cached is None:
                raise
            return cached.model_copy(
                update={
                    "quality_status": QualityStatus.STALE_FALLBACK,
                    "warnings": [
                        *cached.warnings,
                        f"在线全A行情不可用，已返回同业务日快照：{_message(online_error)}",
                    ],
                }
            )

    def _fetch_historical(self, business_date: date) -> MarketBreadthSnapshot:
        if self._snapshot_store is None:
            raise RuntimeError("历史业务日没有同日期历史快照或本地日K样本")
        pairs = self._snapshot_store.load_daily_bar_pairs(business_date.isoformat())
        changes = [
            round((current.close / previous.close - 1.0) * 100.0, 6)
            for previous, current in pairs
            if previous.close > 0 and current.close > 0
        ]
        if not changes:
            raise RuntimeError("历史业务日没有同日期历史快照或本地日K样本")
        warnings = [
            f"历史市场宽度来自本地日K样本，共 {len(changes)} 只，不代表完整全A"
        ]
        limit_up = self._pool_count(
            self._limit_up_loader,
            business_date,
            "涨停池",
            warnings,
        )
        limit_down = self._pool_count(
            self._limit_down_loader,
            business_date,
            "跌停池",
            warnings,
        )
        advance = sum(1 for value in changes if value > 0)
        decline = sum(1 for value in changes if value < 0)
        result = MarketBreadthSnapshot(
            business_date=business_date.isoformat(),
            source_code="LOCAL_DAILY_BAR_PANEL",
            source_family="LOCAL_SNAPSHOT",
            quality_status=QualityStatus.PARTIAL_FRESH,
            retrieved_at=self._now_provider(),
            advance_count=advance,
            decline_count=decline,
            flat_count=len(changes) - advance - decline,
            valid_count=len(changes),
            advance_ratio=advance / len(changes),
            total_amount=sum(current.amount or 0.0 for _, current in pairs),
            limit_up_count=limit_up,
            limit_down_count=limit_down,
            median_change_pct=statistics.median(changes),
            return_distribution=_return_distribution(changes),
            warnings=warnings,
        )
        self._attach_historical_internals(result, business_date, warnings)
        self._snapshot_store.save_market_breadth(result)
        return result

    def _fetch_online(self, business_date: date) -> MarketBreadthSnapshot:
        warnings: list[str] = []
        try:
            rows = self._normalize(self._eastmoney_loader(), "EASTMONEY")
            source_code = "AKSHARE_EASTMONEY_A_SPOT"
            source_family = "EASTMONEY"
            quality = QualityStatus.FRESH_PRIMARY
        except Exception as primary_error:
            warnings.append(f"东方财富全A行情不可用，已切换新浪：{_message(primary_error)}")
            rows = self._normalize(self._sina_loader(), "SINA")
            source_code = "AKSHARE_SINA_A_SPOT"
            source_family = "SINA"
            quality = QualityStatus.FRESH_FALLBACK
        if not rows:
            raise RuntimeError("全A行情没有有效股票")

        limit_up = self._pool_count(
            self._limit_up_loader,
            business_date,
            "涨停池",
            warnings,
        )
        limit_down = self._pool_count(
            self._limit_down_loader,
            business_date,
            "跌停池",
            warnings,
        )
        if limit_up is None or limit_down is None:
            quality = QualityStatus.PARTIAL_FRESH

        changes = [row[0] for row in rows]
        advance = sum(1 for value in changes if value > 0)
        decline = sum(1 for value in changes if value < 0)
        flat = len(changes) - advance - decline
        result = MarketBreadthSnapshot(
            business_date=business_date.isoformat(),
            source_code=source_code,
            source_family=source_family,
            quality_status=quality,
            retrieved_at=self._now_provider(),
            advance_count=advance,
            decline_count=decline,
            flat_count=flat,
            valid_count=len(rows),
            advance_ratio=advance / len(rows),
            total_amount=sum(row[1] for row in rows),
            limit_up_count=limit_up,
            limit_down_count=limit_down,
            median_change_pct=statistics.median(changes),
            return_distribution=_return_distribution(changes),
            warnings=warnings,
        )
        self._attach_historical_internals(result, business_date, warnings)
        return result

    def _attach_historical_internals(
        self,
        snapshot: MarketBreadthSnapshot,
        business_date: date,
        warnings: list[str],
    ) -> None:
        if self._snapshot_store is None:
            warnings.append("本地全A日K面板不可用，趋势宽度与历史轨迹暂缺")
            return
        panel = self._snapshot_store.load_daily_bar_panel(
            business_date.isoformat()
        )
        history = _market_internal_history(panel)
        snapshot.history = history
        current = next(
            (
                point
                for point in reversed(history)
                if point.business_date == business_date.isoformat()
            ),
            None,
        )
        if current is None:
            warnings.append("当日日K面板尚未完整入库，趋势宽度与历史型指标暂缺")
            return
        snapshot.trend_breadth = MarketTrendBreadth(
            ma20_ratio=current.ma20_ratio,
            ma20_valid_count=_trend_valid_count(panel, business_date, 20),
            ma60_ratio=current.ma60_ratio,
            ma60_valid_count=_trend_valid_count(panel, business_date, 60),
            ma120_ratio=current.ma120_ratio,
            ma120_valid_count=_trend_valid_count(panel, business_date, 120),
            ma250_ratio=current.ma250_ratio,
            ma250_valid_count=_trend_valid_count(panel, business_date, 250),
        )
        snapshot.new_high_low = MarketNewHighLow(
            high20_count=current.new_high20_count,
            low20_count=current.new_low20_count,
            valid20_count=_trend_valid_count(panel, business_date, 20),
            high60_count=current.new_high60_count,
            low60_count=current.new_low60_count,
            valid60_count=_trend_valid_count(panel, business_date, 60),
            high250_count=current.new_high250_count,
            low250_count=current.new_low250_count,
            valid250_count=_trend_valid_count(panel, business_date, 250),
        )
        snapshot.net_advances = current.net_advances
        snapshot.advance_decline_line = current.advance_decline_line

    def _normalize(self, frame: Any, family: str) -> list[tuple[float, float]]:
        rows: list[tuple[float, float]] = []
        if frame is None or not hasattr(frame, "iterrows"):
            raise RuntimeError(f"{family} 全A行情响应不是表格")
        for _, row in frame.iterrows():
            code = _first(row, "代码", "code", "symbol")
            price = _number(_first(row, "最新价", "trade", "price"))
            change = _number(_first(row, "涨跌幅", "changepercent", "change_pct"))
            amount = _number(_first(row, "成交额", "amount"))
            if not _valid_code(code) or price is None or price <= 0 or change is None:
                continue
            rows.append((change, max(0.0, amount or 0.0)))
        if not rows:
            raise RuntimeError(f"{family} 全A行情没有有效股票")
        return rows

    def _pool_count(
        self,
        loader: PoolLoader,
        business_date: date,
        label: str,
        warnings: list[str],
    ) -> int | None:
        try:
            frame = loader(business_date.strftime("%Y%m%d"))
            if frame is None or not hasattr(frame, "index"):
                raise RuntimeError("响应不是表格")
            return len(frame.index)
        except Exception as error:
            warnings.append(f"东方财富{label}不可用：{_message(error)}")
            return None

    @staticmethod
    def _load_eastmoney() -> Any:
        import akshare as ak

        return ak.stock_zh_a_spot_em()

    @staticmethod
    def _load_sina() -> Any:
        import akshare as ak

        return ak.stock_zh_a_spot()

    @staticmethod
    def _load_limit_up(value: str) -> Any:
        import akshare as ak

        return ak.stock_zt_pool_em(date=value)

    @staticmethod
    def _load_limit_down(value: str) -> Any:
        import akshare as ak

        return ak.stock_zt_pool_dtgc_em(date=value)

    @staticmethod
    def _load_calendar() -> Any:
        import akshare as ak

        return ak.tool_trade_date_hist_sina()

    def latest_trade_date(self) -> date:
        frame = self._calendar_loader()
        if frame is None or not hasattr(frame, "iterrows"):
            raise RuntimeError("交易日历响应不是表格，禁止使用当前现货回填历史日期")
        maximum = self._today_provider()
        values: list[date] = []
        for _, row in frame.iterrows():
            value = _date_value(_first(row, "trade_date", "交易日期", "日期"))
            if value is not None and value <= maximum:
                values.append(value)
        if not values:
            raise RuntimeError("交易日历没有有效日期，禁止使用当前现货回填历史日期")
        return max(values)


def _first(row: Any, *names: str) -> object:
    for name in names:
        if name in row:
            value = row.get(name)
            if value is not None:
                return value
    return None


def _number(value: object) -> float | None:
    try:
        number = float(value)
    except (TypeError, ValueError):
        return None
    return number if number == number else None


def _date_value(value: object) -> date | None:
    if isinstance(value, datetime):
        return value.date()
    if isinstance(value, date):
        return value
    try:
        return date.fromisoformat(str(value).strip()[:10])
    except (TypeError, ValueError):
        return None


def _valid_code(value: object) -> bool:
    text = str(value).strip().lower() if value is not None else ""
    if text.startswith(("sh", "sz", "bj")):
        text = text[2:]
    return len(text) == 6 and text.isdigit()


def _message(error: Exception) -> str:
    return str(error) or type(error).__name__


def _shanghai_now() -> datetime:
    return datetime.now(ZoneInfo("Asia/Shanghai"))


_DISTRIBUTION_BUCKETS = (
    ("DOWN_7", "≤ -7%", None, -7.0),
    ("DOWN_3_7", "-7% ~ -3%", -7.0, -3.0),
    ("DOWN_0_3", "-3% ~ 0", -3.0, 0.0),
    ("FLAT", "0", 0.0, 0.0),
    ("UP_0_3", "0 ~ 3%", 0.0, 3.0),
    ("UP_3_7", "3% ~ 7%", 3.0, 7.0),
    ("UP_7", "≥ 7%", 7.0, None),
)


def _return_distribution(
    changes: list[float],
) -> list[MarketReturnDistributionBucket]:
    counts = {code: 0 for code, _, _, _ in _DISTRIBUTION_BUCKETS}
    for value in changes:
        if value <= -7.0:
            counts["DOWN_7"] += 1
        elif value <= -3.0:
            counts["DOWN_3_7"] += 1
        elif value < 0.0:
            counts["DOWN_0_3"] += 1
        elif value == 0.0:
            counts["FLAT"] += 1
        elif value < 3.0:
            counts["UP_0_3"] += 1
        elif value < 7.0:
            counts["UP_3_7"] += 1
        else:
            counts["UP_7"] += 1
    total = max(1, len(changes))
    return [
        MarketReturnDistributionBucket(
            code=code,
            label=label,
            lower_bound=lower,
            upper_bound=upper,
            count=counts[code],
            ratio=counts[code] / total,
        )
        for code, label, lower, upper in _DISTRIBUTION_BUCKETS
    ]


def _market_internal_history(
    panel: dict[str, list[Any]],
) -> list[MarketInternalHistoryPoint]:
    all_dates = sorted({bar.trade_date for bars in panel.values() for bar in bars})
    target_dates = set(all_dates[-60:])
    aggregates: dict[str, dict[str, Any]] = {
        trade_date: {
            "changes": [],
            "amount": 0.0,
            "above": {20: 0, 60: 0, 120: 0, 250: 0},
            "valid": {20: 0, 60: 0, 120: 0, 250: 0},
            "high": {20: 0, 60: 0, 250: 0},
            "low": {20: 0, 60: 0, 250: 0},
        }
        for trade_date in target_dates
    }
    for bars in panel.values():
        closes = [bar.close for bar in bars]
        prefix = [0.0]
        for close in closes:
            prefix.append(prefix[-1] + close)
        for index, bar in enumerate(bars):
            if index == 0 or bar.trade_date not in target_dates:
                continue
            previous = closes[index - 1]
            if previous <= 0 or bar.close <= 0:
                continue
            aggregate = aggregates[bar.trade_date]
            aggregate["changes"].append(
                (bar.close / previous - 1.0) * 100.0
            )
            aggregate["amount"] += max(0.0, bar.amount or 0.0)
            for window in (20, 60, 120, 250):
                if index + 1 < window:
                    continue
                start = index + 1 - window
                average = (prefix[index + 1] - prefix[start]) / window
                aggregate["valid"][window] += 1
                if bar.close > average:
                    aggregate["above"][window] += 1
            for window in (20, 60, 250):
                if index + 1 < window:
                    continue
                start = index + 1 - window
                window_closes = closes[start:index + 1]
                if bar.close >= max(window_closes):
                    aggregate["high"][window] += 1
                if bar.close <= min(window_closes):
                    aggregate["low"][window] += 1

    values: list[MarketInternalHistoryPoint] = []
    rolling_net: deque[int] = deque(maxlen=60)
    for trade_date in sorted(aggregates):
        aggregate = aggregates[trade_date]
        changes = aggregate["changes"]
        if not changes:
            continue
        advance = sum(1 for value in changes if value > 0)
        decline = sum(1 for value in changes if value < 0)
        flat = len(changes) - advance - decline
        net_advances = advance - decline
        rolling_net.append(net_advances)
        values.append(MarketInternalHistoryPoint(
            business_date=trade_date,
            advance_count=advance,
            decline_count=decline,
            flat_count=flat,
            valid_count=len(changes),
            advance_ratio=advance / len(changes),
            total_amount=aggregate["amount"],
            median_change_pct=statistics.median(changes),
            ma20_ratio=_ratio(aggregate["above"][20], aggregate["valid"][20]),
            ma60_ratio=_ratio(aggregate["above"][60], aggregate["valid"][60]),
            ma120_ratio=_ratio(aggregate["above"][120], aggregate["valid"][120]),
            ma250_ratio=_ratio(aggregate["above"][250], aggregate["valid"][250]),
            new_high20_count=aggregate["high"][20],
            new_low20_count=aggregate["low"][20],
            new_high60_count=aggregate["high"][60],
            new_low60_count=aggregate["low"][60],
            new_high250_count=aggregate["high"][250],
            new_low250_count=aggregate["low"][250],
            net_advances=net_advances,
            advance_decline_line=sum(rolling_net),
        ))
    return values


def _trend_valid_count(
    panel: dict[str, list[Any]],
    business_date: date,
    window: int,
) -> int:
    target = business_date.isoformat()
    return sum(
        1
        for bars in panel.values()
        if len(bars) >= window and bars[-1].trade_date == target
    )


def _ratio(numerator: int, denominator: int) -> float | None:
    return None if denominator == 0 else numerator / denominator
