from __future__ import annotations

from collections.abc import Awaitable
from datetime import datetime
from typing import Any
from zoneinfo import ZoneInfo

from finscope_market_data.models import (
    CapitalFlowData,
    CapitalFlowPoint,
    DailyBar,
    DataCapability,
    StockProfile,
    StockQuote,
    StockSymbol,
)
from finscope_market_data.providers.base import ProviderError
from finscope_market_data.providers.http import ProviderHttpClient


class EastmoneyProvider:
    provider_code = "EASTMONEY_DIRECT"
    provider_family = "EASTMONEY"
    priority = 30
    capabilities = {
        DataCapability.QUOTE,
        DataCapability.CAPITAL_FLOW,
        DataCapability.PROFILE,
    }
    _realtime = "https://push2.eastmoney.com/api/qt/stock/"
    _history = "https://push2his.eastmoney.com/api/qt/stock/"
    _ut = "7eea3edcaed734bea9cbfc24409ed989"
    _flow_ut = "b2884a393a59ad64002292a3e90d46a5"

    def __init__(self, http: ProviderHttpClient | None = None) -> None:
        self.http = http or ProviderHttpClient(
            timeout_seconds=10,
            minimum_interval_seconds=1.0,
        )

    def supports(self, capability: DataCapability, symbol: StockSymbol) -> bool:
        return capability in self.capabilities

    def priority_for(self, capability: DataCapability) -> int:
        # Fund flow has no richer independent intraday source, so try the direct
        # request once before falling back to Sina daily data.
        return 10 if capability is DataCapability.CAPITAL_FLOW else self.priority

    async def fetch(self, capability: DataCapability, symbol: StockSymbol, **kwargs: Any) -> Any:
        if capability is DataCapability.QUOTE:
            return self.parse_quote(await self._quote(symbol), symbol)
        if capability is DataCapability.CAPITAL_FLOW:
            return await self._capital_flow(symbol)
        if capability is DataCapability.PROFILE:
            quote = await self._quote(symbol)
            data = quote.get("data") or {}
            return StockProfile(
                symbol=symbol,
                name=data.get("f58"),
                fields={
                    "pe_ratio": _scaled(data.get("f162"), 100),
                    "pb_ratio": _scaled(data.get("f167"), 100),
                    "market_cap": _number(data.get("f116")),
                    "circulating_market_cap": _number(data.get("f117")),
                },
            )
        raise ProviderError("UNSUPPORTED_CAPABILITY", capability.value, False)

    async def _capital_flow(self, symbol: StockSymbol) -> CapitalFlowData:
        warnings: list[str] = []
        # These endpoints belong to the same upstream family. Serial access costs
        # a little latency, but avoids a burst of four simultaneous requests that
        # is much more likely to be throttled or reset by the public data source.
        minute_payload = await self._capture(self._minute_flow(symbol))
        daily_payload = await self._capture(self._daily_flow(symbol))
        quote_payload = await self._capture(self._quote(symbol))
        bars_payload = await self._capture(self._daily_bars(symbol, 260))
        minutes = self._safe_parse_flow(minute_payload, symbol, False, "实时资金流", warnings)
        days = self._safe_parse_flow(daily_payload, symbol, True, "历史资金流", warnings)
        if not minutes and not days:
            raise ProviderError(
                "ALL_FUND_FLOW_SOURCES_FAILED",
                "东方财富实时与历史资金流均不可用",
                False,
            )
        quote = None
        if isinstance(quote_payload, dict):
            try:
                quote = self.parse_quote(quote_payload, symbol)
            except ProviderError as error:
                warnings.append(f"行情上下文不可用：{error}")
        else:
            warnings.append(f"行情上下文不可用：{quote_payload}")
        bars = self.parse_daily_bars(bars_payload, symbol) if isinstance(bars_payload, dict) else []
        bars_by_date = {bar.trade_date: bar for bar in bars}
        for point in days:
            bar = bars_by_date.get(point.observed_at.date().isoformat())
            if bar:
                point.price = bar.close
                point.change_pct = bar.change_pct
                point.volume = bar.volume
                point.amount = bar.amount
                point.turnover_rate = bar.turnover_rate
        if quote and minutes:
            latest = minutes[-1]
            latest.price = quote.price
            latest.volume = quote.volume
            latest.amount = quote.amount
            latest.turnover_rate = quote.turnover_rate
            latest.volume_ratio = quote.volume_ratio
        return CapitalFlowData(
            minute_points=minutes,
            daily_points=days,
            turnover_rate=quote.turnover_rate if quote else None,
            volume_ratio=quote.volume_ratio if quote else None,
            warnings=warnings,
        )

    @staticmethod
    async def _capture(request: Awaitable[dict[str, Any]]) -> dict[str, Any] | Exception:
        try:
            return await request
        except Exception as error:
            return error

    def _safe_parse_flow(
        self,
        payload: object,
        symbol: StockSymbol,
        daily: bool,
        label: str,
        warnings: list[str],
    ) -> list[CapitalFlowPoint]:
        if isinstance(payload, Exception):
            warnings.append(f"{label}不可用：{payload}")
            return []
        try:
            return self.parse_flow(payload, symbol, daily=daily)  # type: ignore[arg-type]
        except ProviderError as error:
            warnings.append(f"{label}不可用：{error}")
            return []

    async def _quote(self, symbol: StockSymbol) -> dict[str, Any]:
        fields = "f43,f44,f45,f46,f47,f48,f50,f57,f58,f60,f116,f117,f162,f167,f168,f170"
        return await self.http.get_json(
            self.provider_code,
            self._realtime + "get",
            headers={"Referer": "https://quote.eastmoney.com"},
            params={"secid": symbol.eastmoney_secid, "fields": fields},
        )

    async def _daily_bars(self, symbol: StockSymbol, limit: int) -> dict[str, Any]:
        return await self.http.get_json(
            self.provider_code,
            self._history + "kline/get",
            headers={"Referer": "https://quote.eastmoney.com"},
            params={
                "secid": symbol.eastmoney_secid,
                "ut": self._ut,
                "klt": 101,
                "fqt": 1,
                "lmt": min(max(int(limit), 1), 5000),
                "end": 20500101,
                "fields1": "f1,f2,f3,f4,f5,f6",
                "fields2": "f51,f52,f53,f54,f55,f56,f57,f58,f59,f60,f61",
            },
        )

    async def _minute_flow(self, symbol: StockSymbol) -> dict[str, Any]:
        return await self.http.get_json(
            self.provider_code,
            self._realtime + "fflow/kline/get",
            headers={"Referer": "https://quote.eastmoney.com"},
            params={
                "secid": symbol.eastmoney_secid,
                "lmt": 500,
                "klt": 1,
                "fields1": "f1,f2,f3,f7",
                "fields2": "f51,f52,f53,f54,f55,f56,f57,f58,f59,f60,f61",
            },
        )

    async def _daily_flow(self, symbol: StockSymbol) -> dict[str, Any]:
        return await self.http.get_json(
            self.provider_code,
            self._history + "fflow/daykline/get",
            headers={"Referer": "https://quote.eastmoney.com"},
            params={
                "secid": symbol.eastmoney_secid,
                "lmt": 250,
                "klt": 101,
                "ut": self._flow_ut,
                "fields1": "f1,f2,f3,f7",
                "fields2": "f51,f52,f53,f54,f55,f56,f57,f58,f59,f60,f61",
            },
        )

    @staticmethod
    def parse_quote(payload: dict[str, Any], symbol: StockSymbol) -> StockQuote:
        data = payload.get("data")
        if not isinstance(data, dict):
            raise ProviderError("SCHEMA_DRIFT", "东方财富行情缺少 data", False)
        price = _scaled(data.get("f43"), 100)
        if price is None or price <= 0:
            raise ProviderError("EMPTY_DATA", "东方财富行情暂无有效成交")
        return StockQuote(
            symbol=symbol,
            name=data.get("f58"),
            price=price,
            previous_close=_scaled(data.get("f60"), 100),
            open=_scaled(data.get("f46"), 100),
            high=_scaled(data.get("f44"), 100),
            low=_scaled(data.get("f45"), 100),
            change_pct=_scaled(data.get("f170"), 100),
            volume=_number(data.get("f47")),
            amount=_number(data.get("f48")),
            volume_ratio=_scaled(data.get("f50"), 100),
            turnover_rate=_scaled(data.get("f168"), 100),
            pe_ratio=_scaled(data.get("f162"), 100),
            pb_ratio=_scaled(data.get("f167"), 100),
            market_cap=_number(data.get("f116")),
            circulating_market_cap=_number(data.get("f117")),
            observed_at=datetime.now(ZoneInfo("Asia/Shanghai")),
        )

    @staticmethod
    def parse_daily_bars(payload: dict[str, Any], symbol: StockSymbol) -> list[DailyBar]:
        lines = (payload.get("data") or {}).get("klines")
        if not isinstance(lines, list):
            raise ProviderError("SCHEMA_DRIFT", "东方财富日 K 缺少 klines", False)
        result: list[DailyBar] = []
        for line in lines:
            fields = str(line).split(",")
            if len(fields) < 7:
                raise ProviderError("SCHEMA_DRIFT", "东方财富日 K 字段数量不足", False)
            result.append(
                DailyBar(
                    symbol=symbol,
                    trade_date=fields[0],
                    open=float(fields[1]),
                    close=float(fields[2]),
                    high=float(fields[3]),
                    low=float(fields[4]),
                    volume=float(fields[5]),
                    amount=_float_at(fields, 6),
                    amplitude=_float_at(fields, 7),
                    change_pct=_float_at(fields, 8),
                    change=_float_at(fields, 9),
                    turnover_rate=_float_at(fields, 10),
                    adjustment="QFQ",
                )
            )
        return result

    @staticmethod
    def parse_flow(
        payload: dict[str, Any],
        symbol: StockSymbol,
        *,
        daily: bool,
    ) -> list[CapitalFlowPoint]:
        lines = (payload.get("data") or {}).get("klines")
        if not isinstance(lines, list):
            raise ProviderError("SCHEMA_DRIFT", "东方财富资金流缺少 klines", False)
        result: list[CapitalFlowPoint] = []
        previous: list[float | None] | None = None
        previous_date = None
        for line in lines:
            fields = str(line).split(",")
            if len(fields) < 6:
                raise ProviderError("SCHEMA_DRIFT", "东方财富资金流字段数量不足", False)
            observed = datetime.strptime(fields[0], "%Y-%m-%d" if daily else "%Y-%m-%d %H:%M")
            observed = observed.replace(
                hour=15 if daily else observed.hour,
                tzinfo=ZoneInfo("Asia/Shanghai"),
            )
            raw_values = [_float_at(fields, index) for index in range(1, 6)]
            if not daily and previous_date != observed.date():
                previous = None
            values = raw_values if daily else [
                value if value is None or previous is None or previous[index] is None else value - previous[index]
                for index, value in enumerate(raw_values)
            ]
            result.append(
                CapitalFlowPoint(
                    symbol=symbol,
                    granularity="DAY_1" if daily else "MINUTE_1",
                    observed_at=observed,
                    main_net_inflow=values[0],
                    small_net_inflow=values[1],
                    medium_net_inflow=values[2],
                    large_net_inflow=values[3],
                    super_large_net_inflow=values[4],
                    main_net_inflow_ratio=_float_at(fields, 6),
                    small_net_inflow_ratio=_float_at(fields, 7),
                    medium_net_inflow_ratio=_float_at(fields, 8),
                    large_net_inflow_ratio=_float_at(fields, 9),
                    super_large_net_inflow_ratio=_float_at(fields, 10),
                )
            )
            previous = raw_values
            previous_date = observed.date()
        return result


def _float_at(fields: list[str], index: int) -> float | None:
    if index >= len(fields) or fields[index] in {"", "-", "--"}:
        return None
    try:
        return float(fields[index])
    except ValueError:
        return None


def _number(value: object) -> float | None:
    try:
        return None if value is None or value == "-" else float(value)
    except (TypeError, ValueError):
        return None


def _scaled(value: object, divisor: float) -> float | None:
    number = _number(value)
    return None if number is None else number / divisor
