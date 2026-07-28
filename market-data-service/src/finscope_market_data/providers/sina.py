from __future__ import annotations

import json
import re
from datetime import datetime
from typing import Any
from zoneinfo import ZoneInfo

from finscope_market_data.models import (
    CapitalFlowData,
    CapitalFlowPoint,
    DataCapability,
    StockQuote,
    StockSymbol,
)
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


class SinaCapitalFlowProvider:
    """新浪日级资金流；与东财 push2 使用独立域名和风控面。"""

    provider_code = "SINA_CAPITAL_FLOW"
    provider_family = "SINA"
    priority = 20
    capabilities = {DataCapability.CAPITAL_FLOW}

    def priority_for(self, capability: DataCapability) -> int:
        return self.priority
    _endpoint = (
        "https://vip.stock.finance.sina.com.cn/quotes_service/api/json_v2.php/"
        "MoneyFlow.ssl_qsfx_zjlrqs"
    )

    def __init__(self, http: ProviderHttpClient | None = None) -> None:
        self.http = http or ProviderHttpClient(timeout_seconds=8)

    def supports(self, capability: DataCapability, symbol: StockSymbol) -> bool:
        return capability is DataCapability.CAPITAL_FLOW

    async def fetch(
        self,
        capability: DataCapability,
        symbol: StockSymbol,
        **kwargs: Any,
    ) -> CapitalFlowData:
        if capability is not DataCapability.CAPITAL_FLOW:
            raise ProviderError("UNSUPPORTED_CAPABILITY", capability.value, False)
        raw = await self.http.get_text(
            self.provider_code,
            self._endpoint,
            headers={"Referer": "https://finance.sina.com.cn/"},
            params={
                "page": 1,
                "num": min(max(int(kwargs.get("limit", 250)), 1), 500),
                "sort": "opendate",
                "asc": 0,
                "daima": symbol.prefixed_code,
            },
        )
        try:
            rows = json.loads(raw)
        except ValueError as error:
            raise ProviderError("SCHEMA_DRIFT", "新浪资金流返回无效 JSON", False) from error
        if not isinstance(rows, list):
            raise ProviderError("SCHEMA_DRIFT", "新浪资金流返回结构变化", False)
        points = self.map_rows(rows, symbol)
        if not points:
            raise ProviderError("EMPTY_DATA", "新浪日级资金流为空", True)
        return CapitalFlowData(
            daily_points=points,
            warnings=["SINA_DAILY_FLOW_ONLY", "分钟资金流暂不可用，当前使用新浪日级资金数据"],
        )

    @staticmethod
    def map_rows(rows: list[dict[str, Any]], symbol: StockSymbol) -> list[CapitalFlowPoint]:
        result: list[CapitalFlowPoint] = []
        for row in rows:
            try:
                observed = datetime.strptime(str(row["opendate"]), "%Y-%m-%d").replace(
                    hour=15,
                    tzinfo=ZoneInfo("Asia/Shanghai"),
                )
            except (KeyError, ValueError, TypeError):
                continue
            result.append(CapitalFlowPoint(
                symbol=symbol,
                granularity="DAY_1",
                observed_at=observed,
                price=_object_number(row.get("trade")),
                change_pct=_ratio_percent(row.get("changeratio")),
                main_net_inflow=_object_number(row.get("netamount")),
                main_net_inflow_ratio=_ratio_percent(row.get("ratioamount")),
                super_large_net_inflow=_object_number(row.get("r0_net")),
                super_large_net_inflow_ratio=_ratio_percent(row.get("r0_ratio")),
                turnover_rate=_object_number(row.get("turnover")),
                quality_status="PARTIAL",
            ))
        return result


def _number(value: str) -> float | None:
    try:
        return float(value.strip())
    except (TypeError, ValueError):
        return None


def _object_number(value: object) -> float | None:
    try:
        return None if value in {None, "", "-", "--"} else float(value)
    except (TypeError, ValueError):
        return None


def _ratio_percent(value: object) -> float | None:
    number = _object_number(value)
    return None if number is None else number * 100


def _observed_at(date_value: str, time_value: str) -> datetime:
    try:
        return datetime.strptime(f"{date_value} {time_value}", "%Y-%m-%d %H:%M:%S").replace(
            tzinfo=ZoneInfo("Asia/Shanghai")
        )
    except ValueError:
        return datetime.now(ZoneInfo("Asia/Shanghai"))
