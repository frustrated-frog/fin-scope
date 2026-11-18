"""Read dated THS index bars, including the separately published closing bar."""
from __future__ import annotations

import asyncio
from datetime import date, datetime, time
import json
import math
import re
from typing import Any, Callable
from zoneinfo import ZoneInfo

import pandas as pd

from finscope_market_data.providers.base import ProviderError
from finscope_market_data.providers.http import ProviderHttpClient
from finscope_market_data.providers.tonghuashun_sectors import TonghuashunSectorSource


class TonghuashunSectorHistorySource:
    def __init__(self, http_factory=None, now_provider: Callable[[], datetime] | None = None):
        self.http_factory = http_factory or (lambda: ProviderHttpClient(timeout_seconds=8))
        self.now_provider = now_provider or (lambda: datetime.now(ZoneInfo("Asia/Shanghai")))

    @staticmethod
    def headers() -> dict[str, str]:
        return TonghuashunSectorSource._headers()

    def history(self, code: str, start: date, end: date, headers: dict[str, str]) -> Any:
        if not re.fullmatch(r"\d{6}", code):
            raise ValueError("Invalid THS industry code")
        return asyncio.run(self._bounded(code, start, end, headers))

    async def _bounded(self, code, start, end, headers):
        try:
            return await asyncio.wait_for(self._history(code, start, end, headers), timeout=30)
        except TimeoutError as error:
            raise ProviderError("TIMEOUT", "同花顺行业历史读取超过30秒总时限") from error

    async def _history(self, code, start, end, headers):
        http = self.http_factory()
        try:
            recent = await self._payload(http, code, "last.js", headers)
            latest = self._bars(recent)
            bars: dict[date, float] = {}
            if not latest or start < min(latest):
                for year in range(start.year, end.year + 1):
                    annual = await self._payload(http, code, f"{year}.js", headers)
                    bars.update(self._bars(annual))
            bars.update(latest)
            now = self.now_provider()
            if end == now.date() and now.time().replace(tzinfo=None) < time(15):
                bars.pop(end, None)
            # 年度与last文件常在下一日归档，当日收盘单独发布；只接受对应日期的收盘数据。
            if end not in bars:
                today = await self._payload(http, code, "today.js", headers)
                current = today.get(f"bk_{code}")
                close = self._closing_bar(current, end)
                if close is not None:
                    bars[end] = close
            return pd.DataFrame([
                {"日期": day, "收盘价": close}
                for day, close in sorted(bars.items()) if start <= day <= end
            ], columns=["日期", "收盘价"])
        finally:
            await http.aclose()

    async def _payload(self, http, code, filename, headers):
        text = await http.get_text("TONGHUASHUN_SECTOR_HISTORY",
            f"https://d.10jqka.com.cn/v4/line/bk_{code}/01/{filename}", headers=headers)
        if len(text.encode("utf-8")) > 2 * 1024 * 1024:
            raise ProviderError("RESPONSE_TOO_LARGE", "同花顺行业历史响应超过体积限制", False)
        try:
            value = json.loads(text[text.index("{"):text.rindex("}") + 1])
        except (ValueError, TypeError) as error:
            raise ProviderError("SCHEMA_DRIFT", "同花顺行业历史未返回有效JSONP", False) from error
        if not isinstance(value, dict):
            raise ProviderError("SCHEMA_DRIFT", "同花顺行业历史响应不是对象", False)
        return value

    def _bars(self, payload):
        data = payload.get("data")
        if not isinstance(data, str):
            raise ProviderError("SCHEMA_DRIFT", "同花顺行业历史缺少日线数据", False)
        result = {}
        for row in data.split(";"):
            fields = row.split(",")
            if len(fields) < 5:
                continue
            try:
                day = datetime.strptime(fields[0], "%Y%m%d").date()
                close = float(fields[4])
            except (ValueError, TypeError):
                continue
            if math.isfinite(close) and close > 0:
                result[day] = close
        return result

    def _closing_bar(self, payload, requested):
        if not isinstance(payload, dict) or str(payload.get("1")) != requested.strftime("%Y%m%d"):
            return None
        now = self.now_provider()
        if now.date() < requested or now.date() == requested and now.time().replace(tzinfo=None) < time(15):
            return None
        stamp = str(payload.get("dt", ""))
        if not re.fullmatch(r"\d{4}", stamp):
            return None
        try:
            if datetime.strptime(stamp, "%H%M").time() < time(15):
                return None
        except ValueError:
            return None
        try:
            close = float(payload.get("11"))
            volume = float(payload.get("13"))
        except (ValueError, TypeError):
            return None
        return close if math.isfinite(close) and close > 0 and math.isfinite(volume) and volume > 0 else None
