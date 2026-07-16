from __future__ import annotations

import asyncio
import importlib.util
import os
from collections.abc import Callable, Sequence
from typing import Any, Protocol

from finscope_market_data.models import DailyBar, DataCapability, Market, StockSymbol
from finscope_market_data.providers.base import ProviderError


DEFAULT_TDX_SERVERS: tuple[tuple[str, int], ...] = (
    ("115.238.56.198", 7709),
    ("115.238.90.165", 7709),
    ("180.153.18.170", 7709),
    ("60.191.117.167", 7709),
)


class TdxApi(Protocol):
    def connect(self, host: str, port: int, time_out: int) -> bool: ...

    def get_security_bars(
        self, category: int, market: int, code: str, start: int, count: int
    ) -> list[dict[str, Any]] | None: ...

    def disconnect(self) -> None: ...


class PytdxDailyProvider:
    provider_code = "PYTDX_DAILY"
    provider_family = "TDX"
    priority = 40
    capabilities = {DataCapability.DAILY_BARS}

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

    def supports(self, capability: DataCapability, symbol: StockSymbol) -> bool:
        return (
            capability is DataCapability.DAILY_BARS
            and symbol.market in {Market.SH, Market.SZ}
            and (self._api_factory is not None or importlib.util.find_spec("pytdx") is not None)
        )

    async def fetch(self, capability: DataCapability, symbol: StockSymbol, **kwargs: Any) -> list[DailyBar]:
        if capability is not DataCapability.DAILY_BARS:
            raise ProviderError("UNSUPPORTED_CAPABILITY", capability.value, False)
        limit = min(max(int(kwargs.get("limit", 250)), 1), 800)
        try:
            rows = await asyncio.to_thread(self._fetch_rows, symbol, limit)
            return self.map_rows(rows, symbol)
        except ProviderError:
            raise
        except Exception as error:
            raise ProviderError("TDX_ERROR", f"通达信行情获取失败：{error}") from error

    def _fetch_rows(self, symbol: StockSymbol, limit: int) -> list[dict[str, Any]]:
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
                market = 1 if symbol.market is Market.SH else 0
                rows = api.get_security_bars(9, market, symbol.code, 0, limit)
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
                )
            )
        return result
