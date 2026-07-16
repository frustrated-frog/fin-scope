from __future__ import annotations

import re
from datetime import datetime
from typing import Any
from zoneinfo import ZoneInfo

from finscope_market_data.models import DataCapability, StockQuote, StockSymbol
from finscope_market_data.providers.base import ProviderError
from finscope_market_data.providers.http import ProviderHttpClient


class TencentQuoteProvider:
    provider_code = "TENCENT_QUOTE"
    provider_family = "TENCENT"
    priority = 10
    capabilities = {DataCapability.QUOTE}

    def __init__(self, http: ProviderHttpClient | None = None) -> None:
        self.http = http or ProviderHttpClient()

    def supports(self, capability: DataCapability, symbol: StockSymbol) -> bool:
        return capability is DataCapability.QUOTE

    async def fetch(self, capability: DataCapability, symbol: StockSymbol, **kwargs: Any) -> StockQuote:
        raw = await self.http.get_text(
            self.provider_code,
            f"https://qt.gtimg.cn/q={symbol.prefixed_code}",
            headers={"Referer": "https://gu.qq.com"},
            encoding="gbk",
        )
        return self.parse(raw, symbol)

    @staticmethod
    def parse(raw: str, symbol: StockSymbol) -> StockQuote:
        match = re.search(r'v_[a-z]{2}\d+="(.*)"', raw.strip())
        if not match:
            raise ProviderError("SCHEMA_DRIFT", "腾讯行情响应结构变化", False)
        fields = match.group(1).split("~")
        if len(fields) < 44:
            raise ProviderError("SCHEMA_DRIFT", "腾讯行情字段数量不足", False)
        observed = _parse_time(_field(fields, 30))
        price = _number(_field(fields, 3))
        if price is None or price <= 0:
            raise ProviderError("EMPTY_DATA", "腾讯行情暂无有效成交")
        return StockQuote(
            symbol=symbol,
            name=_field(fields, 1) or None,
            price=price,
            previous_close=_number(_field(fields, 4)),
            open=_number(_field(fields, 5)),
            change=_number(_field(fields, 31)),
            change_pct=_number(_field(fields, 32)),
            high=_number(_field(fields, 33)),
            low=_number(_field(fields, 34)),
            volume=_scale(_number(_field(fields, 36)), 100),
            amount=_scale(_number(_field(fields, 37)), 10_000),
            observed_at=observed,
        )


def _field(fields: list[str], index: int) -> str:
    return fields[index].strip() if index < len(fields) else ""


def _number(value: str) -> float | None:
    try:
        return None if not value or value == "--" else float(value)
    except ValueError:
        return None


def _scale(value: float | None, multiplier: float) -> float | None:
    return None if value is None else value * multiplier


def _parse_time(value: str) -> datetime:
    if value:
        try:
            return datetime.strptime(value, "%Y%m%d%H%M%S").replace(tzinfo=ZoneInfo("Asia/Shanghai"))
        except ValueError:
            pass
    return datetime.now(ZoneInfo("Asia/Shanghai"))

