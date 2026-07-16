from __future__ import annotations

import re
from datetime import datetime
from typing import Any
from zoneinfo import ZoneInfo

from finscope_market_data.models import DataCapability, StockQuote, StockSymbol
from finscope_market_data.providers.base import ProviderError
from finscope_market_data.providers.http import ProviderHttpClient


class SinaQuoteProvider:
    provider_code = "SINA_QUOTE"
    provider_family = "SINA"
    priority = 20
    capabilities = {DataCapability.QUOTE}

    def __init__(self, http: ProviderHttpClient | None = None) -> None:
        self.http = http or ProviderHttpClient()

    def supports(self, capability: DataCapability, symbol: StockSymbol) -> bool:
        return capability is DataCapability.QUOTE

    async def fetch(self, capability: DataCapability, symbol: StockSymbol, **kwargs: Any) -> StockQuote:
        raw = await self.http.get_text(
            self.provider_code,
            f"https://hq.sinajs.cn/list={symbol.prefixed_code}",
            headers={"Referer": "https://finance.sina.com.cn"},
            encoding="gbk",
        )
        return self.parse(raw, symbol)

    @staticmethod
    def parse(raw: str, symbol: StockSymbol) -> StockQuote:
        match = re.search(r'="(.*)"', raw.strip())
        if not match:
            raise ProviderError("SCHEMA_DRIFT", "新浪行情响应结构变化", False)
        fields = match.group(1).split(",")
        if len(fields) < 32:
            raise ProviderError("SCHEMA_DRIFT", "新浪行情字段数量不足", False)
        price = _number(fields[3])
        previous_close = _number(fields[2])
        if price is None or price <= 0:
            raise ProviderError("EMPTY_DATA", "新浪行情暂无有效成交")
        observed = _observed_at(fields[30], fields[31])
        change = None if previous_close is None else price - previous_close
        change_pct = None if not previous_close else change / previous_close * 100
        return StockQuote(
            symbol=symbol,
            name=fields[0].strip() or None,
            price=price,
            previous_close=previous_close,
            open=_number(fields[1]),
            high=_number(fields[4]),
            low=_number(fields[5]),
            change=change,
            change_pct=change_pct,
            volume=_number(fields[8]),
            amount=_number(fields[9]),
            bid_price=_number(fields[6]),
            ask_price=_number(fields[7]),
            observed_at=observed,
        )


def _number(value: str) -> float | None:
    try:
        return float(value.strip())
    except (TypeError, ValueError):
        return None


def _observed_at(date_value: str, time_value: str) -> datetime:
    try:
        return datetime.strptime(f"{date_value} {time_value}", "%Y-%m-%d %H:%M:%S").replace(
            tzinfo=ZoneInfo("Asia/Shanghai")
        )
    except ValueError:
        return datetime.now(ZoneInfo("Asia/Shanghai"))

