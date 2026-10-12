"""Tencent raw daily prices and real amount with independent ratio adjustment."""
from __future__ import annotations

from datetime import date, datetime, timedelta
import math
from typing import Any
from zoneinfo import ZoneInfo

from finscope_market_data.models import DailyBar, DataCapability, StockSymbol
from finscope_market_data.providers.base import ProviderError
from finscope_market_data.providers.daily_adjustment import DailyAdjustmentService
from finscope_market_data.providers.http import ProviderHttpClient


class TencentDailyBarProvider:
    provider_code = "TENCENT_DAILY_RATIO_QFQ"
    provider_family = "TENCENT"
    priority = 4
    timeout_seconds = 25
    max_retries = 0
    capabilities = {DataCapability.DAILY_BARS}
    endpoints = (
        "https://proxy.finance.qq.com/ifzqgtimg/appstock/app/newfqkline/get",
        "https://ifzq.gtimg.cn/appstock/app/newfqkline/get",
    )

    def __init__(self, http=None, adjustment=None, *, today=None, now=None):
        self.http = http or ProviderHttpClient(minimum_interval_seconds=0.15)
        self.adjustment = adjustment or DailyAdjustmentService()
        self.now = now or (lambda: datetime.now(ZoneInfo("Asia/Shanghai")))
        self.today = today or (lambda: self.now().date())

    def supports(self, capability, symbol):
        return capability is DataCapability.DAILY_BARS and symbol.market.value in {"SH", "SZ"}

    async def fetch(self, capability, symbol: StockSymbol, **kwargs: Any):
        if not self.supports(capability, symbol):
            raise ProviderError("UNSUPPORTED_MARKET", "腾讯历史日线暂不支持该市场", False)
        limit = min(max(int(kwargs.get("limit", 250)), 1), 5000)
        end = self.today()
        local_now = self.now().astimezone(ZoneInfo("Asia/Shanghai"))
        include_current = kwargs.get("include_current")
        if include_current is None:
            include_current = symbol.is_market_pulse_index
        intraday = local_now.date() == end and local_now.hour * 60 + local_now.minute < 15 * 60 + 15
        if intraday and not include_current:
            end -= timedelta(days=1)
        bars = {}
        # A bounded backward cursor avoids downloading overlapping years twice.
        for _ in range(9):
            rows = await self._page(symbol, end, min(640, max(250, limit - len(bars))))
            if not rows:
                break
            parsed = self.parse(rows, symbol, self.today())
            earliest = min(date.fromisoformat(bar.trade_date) for bar in parsed)
            previous_count = len(bars)
            for bar in parsed:
                day = date.fromisoformat(bar.trade_date)
                if day > end:
                    raise ProviderError("DATE_MISMATCH", "腾讯日线超出请求截止日期", False)
                if bar.trade_date in bars and bars[bar.trade_date] != bar:
                    raise ProviderError("BAR_CONFLICT", "腾讯重复日期行情冲突", False)
                bars[bar.trade_date] = bar
            if len(bars) >= limit or len(rows) < min(640, max(250, limit - previous_count)):
                break
            if len(bars) == previous_count:
                raise ProviderError("PAGINATION_STALLED", "腾讯历史翻页未推进", False)
            end = earliest - timedelta(days=1)
        if not bars:
            raise ProviderError("EMPTY_DATA", "腾讯日线为空", True)
        result = sorted(bars.values(), key=lambda bar: bar.trade_date)[-limit:]
        if intraday:
            result = [bar.model_copy(update={"is_complete": False})
                      if bar.trade_date == self.today().isoformat() else bar for bar in result]
        if symbol.is_market_pulse_index:
            return [bar.model_copy(update={"adjustment": "QFQ"}) for bar in result]
        return await self.adjustment.adjust(symbol, result)

    async def _page(self, symbol, end, count):
        error = None
        for endpoint in self.endpoints:
            try:
                payload = await self.http.get_json(self.provider_code, endpoint,
                    params={"param": f"{symbol.prefixed_code},day,,{end.isoformat()},{count},"},
                    headers={"Referer": "https://gu.qq.com/"})
                if payload.get("code") != 0:
                    raise ProviderError("UPSTREAM_ERROR", "腾讯日线业务错误", True)
                entry = payload.get("data", {}).get(symbol.prefixed_code)
                if not isinstance(entry, dict) or not isinstance(entry.get("day"), list):
                    raise ProviderError("SCHEMA_DRIFT", "腾讯原始日线字段缺失", False)
                return entry["day"]
            except ProviderError as failure:
                error = failure
        raise error or ProviderError("UNAVAILABLE", "腾讯日线入口不可用")

    @staticmethod
    def parse(rows, symbol, today):
        result = []
        for row in rows:
            try:
                if not isinstance(row, list) or len(row) < 6:
                    raise ValueError("row layout")
                day = date.fromisoformat(row[0])
                values = [float(row[index]) for index in (1, 2, 3, 4, 5)]
                if day > today or not all(math.isfinite(value) for value in values):
                    raise ValueError("future/nonfinite")
                opening, close, high, low, volume = values
                if min(opening, close, high, low) <= 0 or volume < 0:
                    raise ValueError("invalid prices")
                if low > min(opening, close) or high < max(opening, close) or high < low:
                    raise ValueError("OHLC conflict")
                # The new endpoint exposes turnover rate at 7, amount (10k CNY) at 8.
                amount = float(row[8]) * 10000 if len(row) > 8 and row[8] not in (None, "", "--") else None
                if amount is not None and (not math.isfinite(amount) or amount < 0):
                    raise ValueError("invalid amount")
                share_volume = volume if symbol.code.startswith(("688", "689")) or symbol.is_market_pulse_index else volume * 100
                result.append(DailyBar(symbol=symbol, trade_date=str(day), open=opening,
                    close=close, high=high, low=low, volume=share_volume, amount=amount,
                    adjustment="NONE"))
            except (ValueError, TypeError, IndexError) as error:
                raise ProviderError("SCHEMA_DRIFT", "腾讯日线日期、价格或单位无效", False) from error
        return result

    def warnings_for(self, capability, symbol, data):
        warnings = [] if symbol.is_market_pulse_index else ["前复权价格使用腾讯原始日线与新浪比例复权因子"]
        if any(not bar.is_complete for bar in data):
            warnings.append("当日日线尚未收盘，当前结果包含明确标记的盘中截面")
        if any(bar.amount is None for bar in data):
            warnings.append("部分日线缺少真实成交额，价格与成交量仍可展示")
        return warnings

    async def aclose(self):
        await self.http.aclose()
        await self.adjustment.aclose()
