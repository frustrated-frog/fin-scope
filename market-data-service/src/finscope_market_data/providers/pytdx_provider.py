from __future__ import annotations

import asyncio
import importlib.util
import os
from collections.abc import Callable, Sequence
from datetime import datetime
from typing import Any, Protocol, TypeVar
from zoneinfo import ZoneInfo

from finscope_market_data.models import DataCapability, Market, StockQuote, StockSymbol
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

    def get_security_quotes(
        self, stocks: list[tuple[int, str]]
    ) -> list[dict[str, Any]] | None: ...

    def disconnect(self) -> None: ...


class PytdxQuoteProvider:
    provider_code = "PYTDX"
    provider_family = "TDX"
    priority = 40
    capabilities = {DataCapability.QUOTE}

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
        return 25

    def supports(self, capability: DataCapability, symbol: StockSymbol) -> bool:
        return (
            capability in self.capabilities
            and symbol.market in {Market.SH, Market.SZ}
            and (self._api_factory is not None or importlib.util.find_spec("pytdx") is not None)
        )

    async def fetch(
        self, capability: DataCapability, symbol: StockSymbol, **kwargs: Any
    ) -> StockQuote:
        try:
            if capability is DataCapability.QUOTE:
                row = await asyncio.to_thread(self._fetch_quote_row, symbol)
                return self.map_quote(row, symbol)
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
