from __future__ import annotations

from collections.abc import Callable
from concurrent.futures import ThreadPoolExecutor, as_completed
from datetime import date, datetime, timedelta
from typing import Any, Literal

from pydantic import BaseModel, ConfigDict, Field


CatalogLoader = Callable[[], Any]
HistoryLoader = Callable[[str, str, str], Any]


class SectorRotationPoint(BaseModel):
    model_config = ConfigDict(extra="forbid")

    business_date: str
    relative_strength: float
    relative_momentum: float


class SectorHistoryEntry(BaseModel):
    model_config = ConfigDict(extra="forbid")

    code: str
    name: str
    last_trade_date: str
    coverage_days: int = Field(ge=2)
    return_1d: float
    return_5d: float | None = None
    return_20d: float | None = None
    positive_days_5: int | None = Field(default=None, ge=0, le=5)
    rotation_trail: list[SectorRotationPoint] = Field(default_factory=list)


class SectorHistoryEnvelope(BaseModel):
    model_config = ConfigDict(extra="forbid")

    schema_version: Literal["sector-history-v2"] = "sector-history-v2"
    source_code: Literal["AKSHARE_TONGHUASHUN_SECTOR_HISTORY"] = (
        "AKSHARE_TONGHUASHUN_SECTOR_HISTORY"
    )
    source_family: Literal["TONGHUASHUN"] = "TONGHUASHUN"
    category: Literal["INDUSTRY"] = "INDUSTRY"
    business_date: str
    quality_status: Literal["FRESH_PRIMARY", "PARTIAL_FRESH"]
    retrieved_at: str
    requested_window: int = Field(ge=20, le=120)
    covered_trade_dates: list[str]
    entries: list[SectorHistoryEntry]
    warnings: list[str] = Field(default_factory=list)


class TonghuashunSectorHistoryService:
    def __init__(
        self,
        catalog_loader: CatalogLoader | None = None,
        history_loader: HistoryLoader | None = None,
        now_provider: Callable[[], datetime] | None = None,
        max_workers: int = 4,
    ) -> None:
        self._catalog_loader = catalog_loader or self._load_catalog
        self._history_loader = history_loader or self._load_history
        self._now_provider = now_provider or datetime.now
        self._max_workers = max(1, min(max_workers, 6))

    def fetch(self, business_date: date, window: int = 60) -> SectorHistoryEnvelope:
        bounded_window = max(20, min(int(window), 120))
        catalog = self._catalog()
        start_date = business_date - timedelta(days=bounded_window * 2 + 30)
        results: dict[int, SectorHistoryEntry] = {}
        bars_by_index: dict[int, list[tuple[date, float]]] = {}
        dates: set[str] = set()
        warnings_by_index: dict[int, str] = {}

        concurrent_start = len(catalog)
        for index, (code, name) in enumerate(catalog):
            try:
                entry, trade_dates, bars = self._entry(
                    code,
                    name,
                    start_date,
                    business_date,
                )
                results[index] = entry
                bars_by_index[index] = bars
                dates.update(trade_dates)
                concurrent_start = index + 1
                break
            except Exception as error:
                warnings_by_index[index] = (
                    f"{name}({code})行业历史不可用: {_message(error)}"
                )

        with ThreadPoolExecutor(max_workers=self._max_workers) as executor:
            futures = {
                executor.submit(
                    self._entry,
                    code,
                    name,
                    start_date,
                    business_date,
                ): (index, code, name)
                for index, (code, name) in enumerate(
                    catalog[concurrent_start:],
                    start=concurrent_start,
                )
            }
            for future in as_completed(futures):
                index, code, name = futures[future]
                try:
                    entry, trade_dates, bars = future.result()
                    results[index] = entry
                    bars_by_index[index] = bars
                    dates.update(trade_dates)
                except Exception as error:
                    warnings_by_index[index] = (
                        f"{name}({code})行业历史不可用: {_message(error)}"
                    )
        entries = [results[index] for index in sorted(results)]
        trails = _rotation_trails(bars_by_index)
        for index, entry in results.items():
            entry.rotation_trail = trails.get(index, [])
        warnings = [warnings_by_index[index] for index in sorted(warnings_by_index)]
        if not entries:
            raise RuntimeError("同花顺没有有效行业历史")
        return SectorHistoryEnvelope(
            business_date=business_date.isoformat(),
            quality_status=(
                "PARTIAL_FRESH" if warnings else "FRESH_PRIMARY"
            ),
            retrieved_at=self._now_provider().isoformat(),
            requested_window=bounded_window,
            covered_trade_dates=sorted(dates)[-bounded_window:],
            entries=entries,
            warnings=warnings,
        )

    def _catalog(self) -> list[tuple[str, str]]:
        frame = self._catalog_loader()
        if frame is None or not hasattr(frame, "iterrows"):
            raise RuntimeError("同花顺行业目录响应不是表格")
        values: list[tuple[str, str]] = []
        seen: set[str] = set()
        for _, row in frame.iterrows():
            code = _text(row.get("code"))
            name = _text(row.get("name"))
            if not code or not name or code in seen:
                continue
            seen.add(code)
            values.append((code, name))
        if not values:
            raise RuntimeError("同花顺行业目录为空")
        return values

    def _entry(
        self,
        code: str,
        name: str,
        start_date: date,
        business_date: date,
    ) -> tuple[SectorHistoryEntry, list[str], list[tuple[date, float]]]:
        frame = self._history_loader(
            name,
            start_date.strftime("%Y%m%d"),
            business_date.strftime("%Y%m%d"),
        )
        bars = _bars(frame, business_date)
        if len(bars) < 2:
            raise RuntimeError("有效交易日少于2日")
        if bars[-1][0] != business_date:
            raise RuntimeError(f"最后交易日{bars[-1][0].isoformat()}与请求日期不一致")
        closes = [value for _, value in bars]
        return (
            SectorHistoryEntry(
                code=code,
                name=name,
                last_trade_date=bars[-1][0].isoformat(),
                coverage_days=len(bars),
                return_1d=_return(closes, 1),
                return_5d=_optional_return(closes, 5),
                return_20d=_optional_return(closes, 20),
                positive_days_5=_positive_days(closes, 5),
            ),
            [trade_date.isoformat() for trade_date, _ in bars],
            bars,
        )

    @staticmethod
    def _load_catalog() -> Any:
        import akshare as ak

        return ak.stock_board_industry_name_ths()

    @staticmethod
    def _load_history(name: str, start_date: str, end_date: str) -> Any:
        import akshare as ak

        return ak.stock_board_industry_index_ths(
            symbol=name,
            start_date=start_date,
            end_date=end_date,
        )


def _bars(frame: Any, maximum_date: date) -> list[tuple[date, float]]:
    if frame is None or not hasattr(frame, "iterrows"):
        raise RuntimeError("行业历史响应不是表格")
    values: dict[date, float] = {}
    for _, row in frame.iterrows():
        trade_date = _date(row.get("日期"))
        close = _number(row.get("收盘价"))
        if close is None:
            close = _number(row.get("收盘"))
        if trade_date is None or trade_date > maximum_date or close is None or close <= 0:
            continue
        values[trade_date] = close
    return sorted(values.items())


def _return(closes: list[float], days: int) -> float:
    return (closes[-1] / closes[-days - 1] - 1.0) * 100.0


def _optional_return(closes: list[float], days: int) -> float | None:
    return _return(closes, days) if len(closes) > days else None


def _positive_days(closes: list[float], days: int) -> int | None:
    if len(closes) <= days:
        return None
    return sum(
        1
        for index in range(len(closes) - days, len(closes))
        if closes[index] > closes[index - 1]
    )


def _rotation_trails(
    bars_by_index: dict[int, list[tuple[date, float]]],
) -> dict[int, list[SectorRotationPoint]]:
    returns_by_date: dict[date, dict[int, float]] = {}
    for sector_index, bars in bars_by_index.items():
        for position in range(20, len(bars)):
            trade_date, close = bars[position]
            previous_close = bars[position - 20][1]
            if previous_close <= 0:
                continue
            returns_by_date.setdefault(trade_date, {})[sector_index] = (
                close / previous_close - 1.0
            ) * 100.0

    strength_by_index: dict[int, list[tuple[date, float]]] = {}
    for trade_date in sorted(returns_by_date):
        daily_returns = returns_by_date[trade_date]
        mean_return = sum(daily_returns.values()) / len(daily_returns)
        for sector_index, sector_return in daily_returns.items():
            strength_by_index.setdefault(sector_index, []).append(
                (trade_date, sector_return - mean_return)
            )

    trails: dict[int, list[SectorRotationPoint]] = {}
    for sector_index, strengths in strength_by_index.items():
        points = [
            SectorRotationPoint(
                business_date=strengths[position][0].isoformat(),
                relative_strength=strengths[position][1],
                relative_momentum=(
                    strengths[position][1] - strengths[position - 5][1]
                ),
            )
            for position in range(5, len(strengths))
        ]
        trails[sector_index] = points[-10:]
    return trails


def _date(value: object) -> date | None:
    if isinstance(value, datetime):
        return value.date()
    if isinstance(value, date):
        return value
    try:
        return date.fromisoformat(str(value).strip()[:10])
    except (TypeError, ValueError):
        return None


def _number(value: object) -> float | None:
    try:
        number = float(value)
    except (TypeError, ValueError):
        return None
    return number if number == number else None


def _text(value: object) -> str:
    text = str(value).strip() if value is not None else ""
    return "" if not text or text.lower() == "nan" else text


def _message(error: Exception) -> str:
    return str(error).strip() or type(error).__name__
