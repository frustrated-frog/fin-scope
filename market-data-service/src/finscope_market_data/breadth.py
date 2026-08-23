from __future__ import annotations

import statistics
from collections.abc import Callable
from datetime import date, datetime
from typing import Any

from finscope_market_data.models import MarketBreadthSnapshot, QualityStatus
from finscope_market_data.snapshot_store import SnapshotStore


FrameLoader = Callable[[], Any]
PoolLoader = Callable[[str], Any]


class MarketBreadthService:
    def __init__(
        self,
        eastmoney_loader: FrameLoader | None = None,
        sina_loader: FrameLoader | None = None,
        limit_up_loader: PoolLoader | None = None,
        limit_down_loader: PoolLoader | None = None,
        now_provider: Callable[[], datetime] | None = None,
        snapshot_store: SnapshotStore | None = None,
    ) -> None:
        self._eastmoney_loader = eastmoney_loader or self._load_eastmoney
        self._sina_loader = sina_loader or self._load_sina
        self._limit_up_loader = limit_up_loader or self._load_limit_up
        self._limit_down_loader = limit_down_loader or self._load_limit_down
        self._now_provider = now_provider or datetime.now
        self._snapshot_store = snapshot_store

    def fetch(self, business_date: date) -> MarketBreadthSnapshot:
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
        return MarketBreadthSnapshot(
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
            warnings=warnings,
        )

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


def _valid_code(value: object) -> bool:
    text = str(value).strip().lower() if value is not None else ""
    if text.startswith(("sh", "sz", "bj")):
        text = text[2:]
    return len(text) == 6 and text.isdigit()


def _message(error: Exception) -> str:
    return str(error) or type(error).__name__
