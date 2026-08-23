from __future__ import annotations

import asyncio
import importlib.util
import os
from collections.abc import Callable, Sequence
from datetime import date, datetime
from typing import Any, Protocol, TypeVar
from zoneinfo import ZoneInfo

from finscope_market_data.models import DailyBar, DataCapability, Market, StockQuote, StockSymbol
from finscope_market_data.providers.base import ProviderError


DEFAULT_TDX_SERVERS: tuple[tuple[str, int], ...] = (
    ("115.238.56.198", 7709),
    ("115.238.90.165", 7709),
    ("180.153.18.170", 7709),
    ("60.191.117.167", 7709),
)

T = TypeVar("T")


class TdxApi(Protocol):
    def connect(self, host: str, port: int, time_out: int) -> bool: ...

    def get_security_bars(
        self, category: int, market: int, code: str, start: int, count: int
    ) -> list[dict[str, Any]] | None: ...

    def get_security_quotes(
        self, stocks: list[tuple[int, str]]
    ) -> list[dict[str, Any]] | None: ...

    def get_xdxr_info(self, market: int, code: str) -> list[dict[str, Any]] | None: ...

    def disconnect(self) -> None: ...


class PytdxDailyProvider:
    provider_code = "PYTDX"
    provider_family = "TDX"
    priority = 40
    capabilities = {DataCapability.QUOTE, DataCapability.DAILY_BARS}

    def __init__(
        self,
        api_factory: Callable[[], TdxApi] | None = None,
        servers: Sequence[tuple[str, int]] | None = None,
    ) -> None:
        host = os.getenv("FINSCOPE_MARKET_DATA_TDX_HOST", "").strip()
        port = os.getenv("FINSCOPE_MARKET_DATA_TDX_PORT", "7709").strip()
        if host:
            self._servers = ((host, int(port)),)
            self._server: tuple[str, int] | None = self._servers[0]
        else:
            self._servers = tuple(servers or DEFAULT_TDX_SERVERS)
            self._server = None
        self._api_factory = api_factory

    def priority_for(self, capability: DataCapability) -> int:
        return 25 if capability is DataCapability.QUOTE else self.priority

    def supports(self, capability: DataCapability, symbol: StockSymbol) -> bool:
        return (
            capability in self.capabilities
            and symbol.market in {Market.SH, Market.SZ}
            and not (
                capability is DataCapability.DAILY_BARS
                and symbol.is_market_pulse_index
            )
            and (self._api_factory is not None or importlib.util.find_spec("pytdx") is not None)
        )

    async def fetch(
        self, capability: DataCapability, symbol: StockSymbol, **kwargs: Any
    ) -> StockQuote | list[DailyBar]:
        try:
            if capability is DataCapability.QUOTE:
                row = await asyncio.to_thread(self._fetch_quote_row, symbol)
                return self.map_quote(row, symbol)
            if capability is DataCapability.DAILY_BARS:
                limit = min(max(int(kwargs.get("limit", 250)), 1), 5000)
                rows, events = await asyncio.to_thread(self._fetch_daily_data, symbol, limit)
                return self.map_qfq_rows(rows, events, symbol)
            raise ProviderError("UNSUPPORTED_CAPABILITY", capability.value, False)
        except ProviderError:
            raise
        except Exception as error:
            raise ProviderError("TDX_ERROR", f"通达信行情获取失败：{error}") from error

    def _fetch_quote_row(self, symbol: StockSymbol) -> dict[str, Any]:
        market = 1 if symbol.market is Market.SH else 0
        rows = self._fetch_from_servers(
            lambda api: api.get_security_quotes([(market, symbol.code)])
        )
        return rows[0]

    def _fetch_daily_data(
        self, symbol: StockSymbol, limit: int
    ) -> tuple[list[dict[str, Any]], list[dict[str, Any]]]:
        market = 1 if symbol.market is Market.SH else 0

        def request(api: TdxApi) -> tuple[list[dict[str, Any]], list[dict[str, Any]]]:
            rows: list[dict[str, Any]] = []
            start = 0
            while len(rows) < limit:
                count = min(800, limit - len(rows))
                page = api.get_security_bars(9, market, symbol.code, start, count)
                if not page:
                    break
                rows.extend(page)
                if len(page) < count:
                    break
                start += len(page)
            events = api.get_xdxr_info(market, symbol.code)
            if events is None:
                raise ProviderError("QFQ_UNAVAILABLE", "通达信除权除息记录不可用", False)
            return rows, list(events)

        return self._fetch_from_servers(request)

    def _fetch_from_servers(
        self, request: Callable[[TdxApi], T | None]
    ) -> T:
        candidates = list(self._servers)
        if self._server is not None:
            candidates = [self._server, *(server for server in candidates if server != self._server)]

        failures: list[str] = []
        for host, port in candidates:
            api = self._new_api()
            try:
                if not api.connect(host, port, time_out=2):
                    failures.append(f"{host}:{port}=connect_false")
                    continue
                rows = request(api)
                if not rows:
                    failures.append(f"{host}:{port}=empty")
                    continue
                self._server = (host, port)
                return list(rows)
            except Exception as error:
                failures.append(f"{host}:{port}={type(error).__name__}")
            finally:
                try:
                    api.disconnect()
                except Exception:
                    pass

        summary = ", ".join(failures) or "没有配置候选节点"
        raise ProviderError("TDX_SERVER_UNAVAILABLE", f"通达信候选节点均不可用：{summary}")

    def _new_api(self) -> TdxApi:
        if self._api_factory is not None:
            return self._api_factory()
        from pytdx.hq import TdxHq_API

        return TdxHq_API(heartbeat=True, auto_retry=True, raise_exception=True)

    @staticmethod
    def map_rows(rows: list[dict[str, Any]], symbol: StockSymbol) -> list[DailyBar]:
        result: list[DailyBar] = []
        for row in rows:
            date_text = str(row.get("datetime") or row.get("date") or "")[:10]
            if len(date_text) != 10:
                raise ProviderError("SCHEMA_DRIFT", "通达信日 K 缺少交易日期", False)
            result.append(
                DailyBar(
                    symbol=symbol,
                    trade_date=date_text,
                    open=float(row["open"]),
                    high=float(row["high"]),
                    low=float(row["low"]),
                    close=float(row["close"]),
                    volume=float(row.get("vol") or row.get("volume") or 0),
                    amount=float(row["amount"]) if row.get("amount") is not None else None,
                    adjustment="NONE",
                )
            )
        return result

    @classmethod
    def map_qfq_rows(
        cls,
        rows: list[dict[str, Any]],
        events: list[dict[str, Any]],
        symbol: StockSymbol,
    ) -> list[DailyBar]:
        bars = sorted(cls.map_rows(rows, symbol), key=lambda item: item.trade_date)
        if not bars:
            return bars
        factors: list[tuple[date, float]] = []
        for event in events:
            parsed = cls._qfq_event_factor(event, bars)
            if parsed is not None:
                factors.append(parsed)
        for bar in bars:
            trade_date = date.fromisoformat(bar.trade_date)
            factor = 1.0
            for event_date, event_factor in factors:
                if trade_date < event_date:
                    factor *= event_factor
            bar.open *= factor
            bar.high *= factor
            bar.low *= factor
            bar.close *= factor
            bar.adjustment = "QFQ"
        return bars

    @staticmethod
    def _qfq_event_factor(
        event: dict[str, Any], bars: list[DailyBar]
    ) -> tuple[date, float] | None:
        if int(_number(event.get("category")) or 0) != 1:
            return None
        try:
            event_date = date(
                int(event["year"]),
                int(event["month"]),
                int(event["day"]),
            )
        except (KeyError, TypeError, ValueError):
            return None
        previous = [bar for bar in bars if date.fromisoformat(bar.trade_date) < event_date]
        if not previous:
            return None
        previous_close = previous[-1].close
        cash = _number(event.get("fenhong")) or 0.0
        bonus = _number(event.get("songzhuangu")) or 0.0
        rights = _number(event.get("peigu")) or 0.0
        rights_price = _number(event.get("peigujia")) or 0.0
        denominator = previous_close * (10.0 + bonus + rights)
        numerator = previous_close * 10.0 - cash + rights * rights_price
        if denominator <= 0 or numerator <= 0:
            raise ProviderError("QFQ_INVALID_EVENT", "通达信除权除息记录无法计算复权因子", False)
        return event_date, numerator / denominator

    @staticmethod
    def map_quote(
        row: dict[str, Any], symbol: StockSymbol, observed_date: str | None = None
    ) -> StockQuote:
        price = _positive_number(row.get("price"))
        if price is None:
            raise ProviderError("EMPTY_DATA", "通达信行情暂无有效成交")
        previous_close = _positive_number(row.get("last_close"))
        change = price - previous_close if previous_close is not None else None
        change_pct = change / previous_close * 100 if change is not None else None
        date_text = observed_date or datetime.now(ZoneInfo("Asia/Shanghai")).date().isoformat()
        time_text = str(row.get("servertime") or "").strip()
        try:
            observed_at = datetime.fromisoformat(f"{date_text}T{time_text}").replace(
                tzinfo=ZoneInfo("Asia/Shanghai")
            )
        except ValueError:
            observed_at = datetime.now(ZoneInfo("Asia/Shanghai"))
        return StockQuote(
            symbol=symbol,
            price=price,
            previous_close=previous_close,
            open=_number(row.get("open")),
            high=_number(row.get("high")),
            low=_number(row.get("low")),
            change=change,
            change_pct=change_pct,
            volume=_number(row.get("vol")),
            amount=_number(row.get("amount")),
            bid_price=_number(row.get("bid1")),
            ask_price=_number(row.get("ask1")),
            observed_at=observed_at,
        )


def _number(value: Any) -> float | None:
    try:
        return None if value is None or value == "" else float(value)
    except (TypeError, ValueError):
        return None


def _positive_number(value: Any) -> float | None:
    number = _number(value)
    return number if number is not None and number > 0 else None
