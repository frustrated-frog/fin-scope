from __future__ import annotations

import asyncio
from collections.abc import Callable
from datetime import date
import importlib.util
from typing import Any

from finscope_market_data.models import DailyBar, DataCapability, StockSymbol
from finscope_market_data.providers.base import ProviderError


FrameLoader = Callable[..., Any]


class EastmoneyIndexDailyProvider:
    provider_code = "AKSHARE_EASTMONEY_INDEX_DAILY"
    provider_family = "EASTMONEY"
    priority = 5
    capabilities = {DataCapability.DAILY_BARS}

    def __init__(self, loader: FrameLoader | None = None) -> None:
        self._loader = loader

    def supports(self, capability: DataCapability, symbol: StockSymbol) -> bool:
        return (
            capability is DataCapability.DAILY_BARS
            and symbol.is_market_pulse_index
            and (self._loader is not None or importlib.util.find_spec("akshare") is not None)
        )

    async def fetch(
        self, capability: DataCapability, symbol: StockSymbol, **kwargs: Any
    ) -> list[DailyBar]:
        if not self.supports(capability, symbol):
            raise ProviderError("UNSUPPORTED_INDEX", symbol.cache_key, False)
        loader = self._loader or _eastmoney_loader
        try:
            frame = await asyncio.to_thread(
                loader,
                _eastmoney_symbol(symbol),
                str(kwargs.get("start_date", "19900101")),
                str(kwargs.get("end_date", "20500101")),
            )
            return _map_frame(frame, symbol, int(kwargs.get("limit", 250)))
        except ProviderError:
            raise
        except Exception as error:
            raise ProviderError(
                "EASTMONEY_INDEX_ERROR", f"东方财富指数日线获取失败：{error}"
            ) from error


class SinaIndexDailyProvider:
    provider_code = "AKSHARE_SINA_INDEX_DAILY"
    provider_family = "SINA"
    priority = 6
    capabilities = {DataCapability.DAILY_BARS}

    def __init__(self, loader: FrameLoader | None = None) -> None:
        self._loader = loader

    def supports(self, capability: DataCapability, symbol: StockSymbol) -> bool:
        return (
            capability is DataCapability.DAILY_BARS
            and symbol.is_market_pulse_index
            and (self._loader is not None or importlib.util.find_spec("akshare") is not None)
        )

    async def fetch(
        self, capability: DataCapability, symbol: StockSymbol, **kwargs: Any
    ) -> list[DailyBar]:
        if not self.supports(capability, symbol):
            raise ProviderError("UNSUPPORTED_INDEX", symbol.cache_key, False)
        loader = self._loader or _sina_loader
        try:
            frame = await asyncio.to_thread(loader, symbol.prefixed_code)
            return _map_frame(frame, symbol, int(kwargs.get("limit", 250)))
        except ProviderError:
            raise
        except Exception as error:
            raise ProviderError(
                "SINA_INDEX_ERROR", f"新浪指数日线获取失败：{error}"
            ) from error


def _eastmoney_loader(symbol: str, start_date: str, end_date: str) -> Any:
    import akshare as ak

    return ak.stock_zh_index_daily_em(
        symbol=symbol,
        start_date=start_date,
        end_date=end_date,
    )


def _sina_loader(symbol: str) -> Any:
    import akshare as ak

    return ak.stock_zh_index_daily(symbol=symbol)


def _eastmoney_symbol(symbol: StockSymbol) -> str:
    if symbol.code == "000852":
        return "csi000852"
    return symbol.prefixed_code


def _map_frame(frame: Any, symbol: StockSymbol, limit: int) -> list[DailyBar]:
    if frame is None or not hasattr(frame, "to_dict"):
        raise ProviderError("INVALID_INDEX_RESPONSE", "指数日线响应不是表格", True)
    records = frame.to_dict(orient="records")
    result: list[DailyBar] = []
    for record in records[-min(max(limit, 1), 5000) :]:
        trade_date = _field(record, "date", "日期")
        open_value = _number(_field(record, "open", "开盘"))
        high = _number(_field(record, "high", "最高"))
        low = _number(_field(record, "low", "最低"))
        close = _number(_field(record, "close", "收盘"))
        volume = _number(_field(record, "volume", "成交量"))
        amount = _number(_field(record, "amount", "成交额"))
        if trade_date is None or min(open_value or 0, high or 0, low or 0, close or 0) <= 0:
            continue
        result.append(
            DailyBar(
                symbol=symbol,
                trade_date=_date_text(trade_date),
                open=open_value,
                high=high,
                low=low,
                close=close,
                volume=max(0.0, volume or 0.0),
                amount=max(0.0, amount if amount is not None else (volume or 0.0)),
                adjustment="QFQ",
            )
        )
    if not result:
        raise ProviderError("EMPTY_INDEX_DATA", "指数日线没有有效记录", True)
    return result


def _field(record: dict[str, Any], *names: str) -> object:
    for name in names:
        if name in record:
            return record[name]
    return None


def _number(value: object) -> float | None:
    try:
        result = float(value)
    except (TypeError, ValueError):
        return None
    return result if result == result else None


def _date_text(value: object) -> str:
    if isinstance(value, date):
        return value.isoformat()
    return str(value)[:10]
