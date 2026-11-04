"""Bounded raw-minute acquisition with independently validated fallback data."""
import asyncio
from datetime import datetime, timedelta
import json

from finscope_market_data.forecast.trading_calendar import previous_session, next_session
from finscope_market_data.overnight.engine import expected_times
from finscope_market_data.overnight.models import MinuteBar
from finscope_market_data.providers.base import ProviderError
from finscope_market_data.providers.http import ProviderHttpClient


class MinuteBatch(list):
    """Request-local provenance; never mutate a shared provider's selected source."""
    def __init__(self, bars, source, failures):
        super().__init__(bars)
        self.source = source
        self.failures = failures


class OvernightMinuteProvider:
    source = 'EASTMONEY_5M_RAW'

    def __init__(self, http_factory=None):
        self.http_factory = http_factory or (lambda: ProviderHttpClient(timeout_seconds=8))

    def fetch(self, code: str, through: datetime):
        return asyncio.run(self.fetch_async(code, through))

    async def fetch_async(self, code: str, through: datetime):
        http = self.http_factory()
        failures = []
        try:
            for source, fetch in ((self.source, self._eastmoney), ('SINA_5M_RAW', self._sina)):
                try:
                    bars = await fetch(http, code, through)
                    self.validate_coverage(bars, through)
                    # A limited provider response often starts midway through its
                    # oldest day. Persisting that fragment would hide the complete
                    # historical day in overnight_effective_minute.
                    complete = set(expected_times('15:00'))
                    days = {}
                    for bar in bars:
                        days.setdefault(bar.ended_at.date(), set()).add(bar.ended_at.strftime('%H:%M'))
                    bars = [bar for bar in bars if bar.ended_at.date() == through.date()
                            or complete.issubset(days[bar.ended_at.date()])]
                    return MinuteBatch(bars, source, failures)
                except (ProviderError, ValueError, KeyError, TypeError) as error:
                    failures.append({'source': source, 'code': getattr(error, 'error_type', 'INVALID_DATA')})
            raise ProviderError('MINUTE_SOURCES_UNAVAILABLE', '; '.join(f"{row['source']}:{row['code']}" for row in failures))
        finally:
            await http.aclose()

    async def _eastmoney(self, http, code, through):
        payload = await http.get_json(self.source, 'https://push2his.eastmoney.com/api/qt/stock/kline/get', params={
            'fields1': 'f1,f2,f3,f4,f5,f6', 'fields2': 'f51,f52,f53,f54,f55,f56,f57,f58,f59,f60,f61',
            'klt': 5, 'fqt': 0, 'secid': ('1.' if code.endswith('.SH') else '0.') + code.split('.')[0],
            'beg': '0', 'end': through.strftime('%Y%m%d')})
        data = payload.get('data') or {}
        if data.get('code') != code.split('.')[0] or not isinstance(data.get('klines'), list):
            raise ValueError('分钟数据代码或行情结构不匹配')
        rows = []
        for line in data['klines']:
            cells = line.split(',')
            if len(cells) < 7:
                raise ValueError('分钟数据列不完整')
            rows.append(dict(zip(('时间', '开盘', '收盘', '最高', '最低', '成交量', '成交额'), cells)))
        return self.parse(rows, through)

    async def _sina(self, http, code, through):
        # This endpoint exposes actual per-bar amount; volume*price is never substituted.
        text = await http.get_text('SINA_5M_RAW',
            'https://quotes.sina.cn/cn/api/json_v2.php/CN_MarketDataService.getKLineData',
            params={'symbol': code[-2:].lower() + code[:6], 'scale': 5, 'ma': 'no', 'datalen': 1023},
            headers={'Referer': 'https://finance.sina.com.cn/'})
        data = json.loads(text)
        if not isinstance(data, list) or not data:
            raise ValueError('备用分钟数据结构无效')
        rows = [{'时间': row['day'], '开盘': row['open'], '收盘': row['close'],
                 '最高': row['high'], '最低': row['low'], '成交额': row['amount']} for row in data]
        return self.parse(rows, through)

    @staticmethod
    def validate_coverage(bars, through):
        day = through.date()
        if next_session(day - timedelta(days=1)) != day:
            day = previous_session(day)
        cutoff = through.strftime('%H:%M') if day == through.date() else '15:00'
        required = expected_times(cutoff)
        available = {bar.ended_at.strftime('%H:%M') for bar in bars if bar.ended_at.date() == day}
        if not required or not set(required).issubset(available):
            raise ProviderError('INCOMPLETE_SESSION', '行情未完整覆盖当前已结束的五分钟窗口')

    @staticmethod
    def parse(rows, through):
        bars = {}
        valid_times = set(expected_times('15:00'))
        for row in rows:
            stamp = datetime.fromisoformat(str(row['时间']))
            if stamp > through or stamp < through - timedelta(days=180):
                continue
            if stamp.strftime('%H:%M') not in valid_times or stamp.second or stamp.microsecond:
                raise ValueError('分钟时间不在完整五分钟交易区间')
            bar = MinuteBar(ended_at=stamp, open=row['开盘'], close=row['收盘'],
                high=row['最高'], low=row['最低'], amount=row['成交额'])
            if bar.ended_at in bars and bars[bar.ended_at] != bar:
                raise ValueError('同一时点存在冲突分钟线')
            bars[bar.ended_at] = bar
        return sorted(bars.values(), key=lambda x: x.ended_at)
