"""Versioned proportional forward adjustment, independent of quote availability."""
from __future__ import annotations

import asyncio
from bisect import bisect_right
from collections import OrderedDict
from datetime import date
import json
import math
import re
from time import monotonic

from finscope_market_data.models import DailyBar, StockSymbol
from finscope_market_data.providers.base import ProviderError
from finscope_market_data.providers.http import ProviderHttpClient


class DailyAdjustmentService:
    source = "SINA_QFQ_FACTOR"

    def __init__(self, http=None, *, ttl_seconds=300, max_symbols=256, clock=monotonic):
        self.http = http or ProviderHttpClient(minimum_interval_seconds=0.15)
        self.ttl_seconds = ttl_seconds
        self.max_symbols = max_symbols
        self.clock = clock
        self._cache = OrderedDict()
        self._inflight = {}

    async def factors(self, symbol: StockSymbol):
        key = symbol.cache_key
        cached = self._cache.get(key)
        if cached and self.clock() - cached[0] < self.ttl_seconds:
            self._cache.move_to_end(key)
            return cached[1]
        task = self._inflight.get(key)
        if task is None:
            task = asyncio.create_task(self._load(symbol))
            self._inflight[key] = task
            task.add_done_callback(lambda done: self._inflight.pop(key, None))
        return await asyncio.shield(task)

    async def _load(self, symbol):
        raw = await self.http.get_text(self.source,
            f"https://finance.sina.com.cn/realstock/company/{symbol.prefixed_code}/qfq.js",
            headers={"Referer": "https://finance.sina.com.cn/"})
        try:
            if not re.match(r"var\s+" + re.escape(symbol.prefixed_code + "qfq") + r"\s*=", raw.strip()):
                raise ValueError("factor identity mismatch")
            offset = raw.index("{")
            payload, _ = json.JSONDecoder().raw_decode(raw[offset:])
            rows = payload["data"]
            if not isinstance(rows, list) or not rows:
                raise ValueError("empty factors")
            factors = {}
            for row in rows:
                day = date.fromisoformat(row["d"])
                value = float(row["f"])
                if not math.isfinite(value) or value <= 0:
                    raise ValueError("invalid factor")
                if day in factors and factors[day] != value:
                    raise ValueError("conflicting factors")
                factors[day] = value
            result = sorted(factors.items())
        except (ValueError, KeyError, TypeError) as error:
            raise ProviderError("ADJUSTMENT_UNAVAILABLE", "比例复权因子无效或缺失", False) from error
        self._cache[symbol.cache_key] = (self.clock(), result)
        self._cache.move_to_end(symbol.cache_key)
        while len(self._cache) > self.max_symbols:
            self._cache.popitem(last=False)
        return result

    async def adjust(self, symbol: StockSymbol, bars: list[DailyBar]):
        factors = await self.factors(symbol)
        dates = [item[0] for item in factors]
        adjusted = []
        for bar in bars:
            index = bisect_right(dates, date.fromisoformat(bar.trade_date)) - 1
            if index < 0:
                raise ProviderError("ADJUSTMENT_COVERAGE", "复权因子未覆盖历史日期", False)
            factor = factors[index][1]
            adjusted.append(bar.model_copy(update={
                **{field: round(getattr(bar, field) / factor, 6)
                   for field in ("open", "high", "low", "close")},
                "adjustment": "QFQ",
            }))
        return adjusted

    async def aclose(self):
        tasks = list(self._inflight.values())
        for task in tasks:
            task.cancel()
        await asyncio.gather(*tasks, return_exceptions=True)
        await self.http.aclose()
