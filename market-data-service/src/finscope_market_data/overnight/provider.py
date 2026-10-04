import asyncio
from datetime import datetime, timedelta

from finscope_market_data.overnight.models import MinuteBar
from finscope_market_data.providers.http import ProviderHttpClient


class OvernightMinuteProvider:
    source = 'EASTMONEY_5M_RAW'

    def fetch(self, code: str, through: datetime):
        return asyncio.run(self.fetch_async(code, through))

    async def fetch_async(self, code: str, through: datetime):
        return await self._fetch(code, through)

    async def _fetch(self, code, through):
        http = ProviderHttpClient(timeout_seconds=12)
        try:
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
            return [bar for bar in self.parse(rows, through) if bar.ended_at >= through - timedelta(days=180)]
        finally:
            await http.aclose()

    @staticmethod
    def parse(rows, through):
        bars = {}
        for row in rows:
            bar = MinuteBar(ended_at=datetime.fromisoformat(str(row['时间'])),
                open=row['开盘'], close=row['收盘'], high=row['最高'], low=row['最低'], amount=row['成交额'])
            if bar.ended_at > through:
                continue
            if bar.ended_at in bars and bars[bar.ended_at] != bar:
                raise ValueError('同一时点存在冲突分钟线')
            bars[bar.ended_at] = bar
        return sorted(bars.values(), key=lambda x: x.ended_at)
