"""Independent daily history with cancellable SDK process isolation."""
from __future__ import annotations

import asyncio
from datetime import date, datetime, timedelta
import json
import math
import sys
from zoneinfo import ZoneInfo

from finscope_market_data.models import DailyBar, DataCapability
from finscope_market_data.providers.base import ProviderError

FIELDS = ('date', 'code', 'open', 'high', 'low', 'close', 'volume', 'amount', 'adjustflag', 'tradestatus')


class BaostockDailyBarProvider:
    provider_code = "BAOSTOCK_DAILY_QFQ"
    provider_family = "BAOSTOCK"
    priority = 15
    capabilities = {DataCapability.DAILY_BARS}

    def __init__(self, *, timeout_seconds=35, today=None):
        self.timeout_seconds = timeout_seconds
        self.today = today or (lambda: datetime.now(ZoneInfo('Asia/Shanghai')).date())

    def supports(self, capability, symbol):
        return capability is DataCapability.DAILY_BARS and symbol.market.value in {'SH', 'SZ'}

    async def fetch(self, capability, symbol, **kwargs):
        if not self.supports(capability, symbol):
            raise ProviderError('UNSUPPORTED_MARKET', 'BaoStock 不支持该市场', False)
        count = min(max(int(kwargs.get('limit', 250)), 1), 5000)
        end = self.today()
        start = end - timedelta(days=min(10000, count * 2 + 90))
        process = await asyncio.create_subprocess_exec(sys.executable, '-m',
            'finscope_market_data.providers.baostock_daily_worker', symbol.prefixed_code,
            str(start), str(end), stdout=asyncio.subprocess.PIPE, stderr=asyncio.subprocess.DEVNULL)
        try:
            output, _ = await asyncio.wait_for(process.communicate(), self.timeout_seconds)
        except (TimeoutError, asyncio.CancelledError) as error:
            if process.returncode is None:
                process.kill()
            await process.wait()
            if isinstance(error, asyncio.CancelledError):
                raise
            raise ProviderError('TIMEOUT', 'BaoStock 日线超时，已终止采集进程') from error
        if process.returncode != 0:
            raise ProviderError('BAOSTOCK_UNAVAILABLE', 'BaoStock 日线暂不可用')
        try:
            return self.parse(json.loads(output), symbol, end)[-count:]
        except (ValueError, KeyError, TypeError) as error:
            raise ProviderError('SCHEMA_DRIFT', 'BaoStock 日线协议或数值无效', False) from error

    @staticmethod
    def parse(payload, symbol, through):
        if payload.get('fields') != list(FIELDS) or not isinstance(payload.get('rows'), list):
            raise ValueError('daily schema mismatch')
        if len(payload['rows']) > 10000:
            raise ValueError('daily row limit')
        bars = {}
        for values in payload['rows']:
            if len(values) != len(FIELDS):
                raise ValueError('daily row width')
            row = dict(zip(FIELDS, values))
            day = date.fromisoformat(row['date'])
            if row['code'] != f'{symbol.market.value.lower()}.{symbol.code}' or row['adjustflag'] != '2' or day > through:
                raise ValueError('daily identity or adjustment mismatch')
            if row['tradestatus'] == '0':
                continue
            prices = {name: float(row[name]) for name in ('open', 'high', 'low', 'close', 'volume', 'amount')}
            if not all(math.isfinite(value) for value in prices.values()):
                raise ValueError('daily nonfinite')
            if min(prices[name] for name in ('open', 'high', 'low', 'close')) <= 0:
                raise ValueError('daily invalid price')
            if prices['volume'] < 0 or prices['amount'] < 0:
                raise ValueError('daily invalid activity')
            if prices['low'] > min(prices['open'], prices['close']) or prices['high'] < max(prices['open'], prices['close']):
                raise ValueError('daily OHLC mismatch')
            bar = DailyBar(symbol=symbol, trade_date=str(day), adjustment='QFQ', **prices)
            if str(day) in bars and bars[str(day)] != bar:
                raise ValueError('daily conflicting duplicate')
            bars[str(day)] = bar
        if not bars:
            raise ValueError('daily empty history')
        return [bars[day] for day in sorted(bars)]
