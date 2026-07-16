from __future__ import annotations

import asyncio
import importlib.util
import math
from datetime import datetime
from typing import Any
from zoneinfo import ZoneInfo

from finscope_market_data.models import (
    CapitalFlowData,
    CapitalFlowPoint,
    DailyBar,
    DataCapability,
    StockProfile,
    StockSymbol,
)
from finscope_market_data.providers.base import ProviderError


class AkshareProvider:
    provider_code = "AKSHARE"
    provider_family = "EASTMONEY"
    priority = 10
    capabilities = {DataCapability.DAILY_BARS, DataCapability.CAPITAL_FLOW, DataCapability.PROFILE}

    def supports(self, capability: DataCapability, symbol: StockSymbol) -> bool:
        return capability in self.capabilities and importlib.util.find_spec("akshare") is not None

    async def fetch(self, capability: DataCapability, symbol: StockSymbol, **kwargs: Any) -> Any:
        try:
            import akshare as ak
        except ImportError as error:
            raise ProviderError("PROVIDER_DISABLED", "AkShare 未安装", False) from error
        try:
            if capability is DataCapability.DAILY_BARS:
                start_date = kwargs.get("start_date", "19900101")
                end_date = kwargs.get("end_date", "20500101")
                limit = min(max(int(kwargs.get("limit", 250)), 1), 1000)
                frame = await asyncio.to_thread(
                    ak.stock_zh_a_hist,
                    symbol=symbol.code,
                    period="daily",
                    start_date=start_date,
                    end_date=end_date,
                    adjust="qfq",
                )
                records = frame.to_dict(orient="records")
                return self.map_daily_records(records[-limit:], symbol)
            if capability is DataCapability.CAPITAL_FLOW:
                if kwargs.get("require_minute"):
                    raise ProviderError(
                        "UNSUPPORTED_GRANULARITY",
                        "AkShare provider only supplies daily capital flow",
                        False,
                    )
                frame = await asyncio.to_thread(
                    ak.stock_individual_fund_flow,
                    stock=symbol.code,
                    market=symbol.market.value.lower(),
                )
                points = self.map_flow_records(frame.to_dict(orient="records"), symbol)
                return CapitalFlowData(daily_points=points)
            if capability is DataCapability.PROFILE:
                frame = await asyncio.to_thread(ak.stock_individual_info_em, symbol=symbol.code)
                return self.map_profile_records(frame.to_dict(orient="records"), symbol)
        except ProviderError:
            raise
        except Exception as error:
            raise ProviderError("AKSHARE_ERROR", f"AkShare 获取失败：{error}") from error
        raise ProviderError("UNSUPPORTED_CAPABILITY", capability.value, False)

    @staticmethod
    def map_daily_records(records: list[dict[str, Any]], symbol: StockSymbol) -> list[DailyBar]:
        return [
            DailyBar(
                symbol=symbol,
                trade_date=str(record["日期"]),
                open=_value(record.get("开盘")) or 0,
                close=_value(record.get("收盘")) or 0,
                high=_value(record.get("最高")) or 0,
                low=_value(record.get("最低")) or 0,
                volume=_value(record.get("成交量")) or 0,
                amount=_value(record.get("成交额")),
                amplitude=_value(record.get("振幅")),
                change_pct=_value(record.get("涨跌幅")),
                change=_value(record.get("涨跌额")),
                turnover_rate=_value(record.get("换手率")),
            )
            for record in records
        ]

    @staticmethod
    def map_flow_records(records: list[dict[str, Any]], symbol: StockSymbol) -> list[CapitalFlowPoint]:
        result: list[CapitalFlowPoint] = []
        for record in records:
            observed = datetime.strptime(str(record["日期"]), "%Y-%m-%d").replace(
                hour=15,
                tzinfo=ZoneInfo("Asia/Shanghai"),
            )
            result.append(
                CapitalFlowPoint(
                    symbol=symbol,
                    granularity="DAY_1",
                    observed_at=observed,
                    price=_value(record.get("收盘价")),
                    change_pct=_value(record.get("涨跌幅")),
                    main_net_inflow=_value(record.get("主力净流入-净额")),
                    main_net_inflow_ratio=_value(record.get("主力净流入-净占比")),
                    super_large_net_inflow=_value(record.get("超大单净流入-净额")),
                    super_large_net_inflow_ratio=_value(record.get("超大单净流入-净占比")),
                    large_net_inflow=_value(record.get("大单净流入-净额")),
                    large_net_inflow_ratio=_value(record.get("大单净流入-净占比")),
                    medium_net_inflow=_value(record.get("中单净流入-净额")),
                    medium_net_inflow_ratio=_value(record.get("中单净流入-净占比")),
                    small_net_inflow=_value(record.get("小单净流入-净额")),
                    small_net_inflow_ratio=_value(record.get("小单净流入-净占比")),
                )
            )
        return result

    @staticmethod
    def map_profile_records(records: list[dict[str, Any]], symbol: StockSymbol) -> StockProfile:
        fields = {str(item.get("item")): item.get("value") for item in records}
        return StockProfile(
            symbol=symbol,
            name=_string(fields.get("股票简称")),
            industry=_string(fields.get("行业")),
            listing_date=_string(fields.get("上市时间")),
            total_shares=_value(fields.get("总股本")),
            circulating_shares=_value(fields.get("流通股")),
            fields={key: value for key, value in fields.items() if value is None or isinstance(value, (str, int, float))},
        )


def _value(value: object) -> float | None:
    try:
        result = float(value)  # type: ignore[arg-type]
        return None if math.isnan(result) else result
    except (TypeError, ValueError):
        return None


def _string(value: object) -> str | None:
    return None if value is None else str(value)
