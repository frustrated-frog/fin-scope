"""CNINFO historical industry changes; publication timestamps are not provided."""
from datetime import date, datetime

from finscope_market_data.industry_models import IndustryChange
from finscope_market_data.providers.http import ProviderHttpClient

URL = 'https://webapi.cninfo.com.cn/api/stock/p_stock2110'


def parse_industry_changes(payload, code, retrieved_at):
    records = payload.get('records')
    if (not isinstance(records, list) or int(payload.get('count', -1)) != len(records)
            or int(payload.get('total', -1)) != len(records)
            or str(payload.get('resultcode', 200)) != '200'):
        raise ValueError('行业变更响应不完整或失败，不能静默截断')
    datetime.fromisoformat(retrieved_at)
    result = {}
    for item in records:
        if item.get('SECCODE') != code:
            raise ValueError('行业历史证券代码不匹配')
        if item.get('F001V') != '008003':
            continue
        effective = date.fromisoformat(item['VARYDATE']).isoformat()
        industry = item.get('F004V')
        if not isinstance(industry, str) or not industry.strip():
            raise ValueError('行业历史缺少一级门类')
        event = IndustryChange(code, industry.strip(), effective, '008003', 'CNINFO', retrieved_at)
        if effective in result and result[effective] != event:
            raise ValueError('同一股票行业变更日期存在冲突')
        result[effective] = event
    return tuple(result[key] for key in sorted(result))


class CninfoIndustryHistoryProvider:
    def __init__(self, http: ProviderHttpClient):
        self.http = http
        self._signer = None

    async def fetch(self, code, end_date, retrieved_at):
        if len(code) != 6 or not code.isdigit():
            raise ValueError('行业查询需要六位股票代码')
        date.fromisoformat(end_date)
        # Reuse the existing optional AKShare ecosystem's public request signing.
        if self._signer is None:
            import py_mini_racer
            from akshare.stock.stock_industry_cninfo import _get_file_content_ths
            self._signer = py_mini_racer.MiniRacer()
            self._signer.eval(_get_file_content_ths('cninfo.js'))
        raw = await self.http.get_json('CNINFO_INDUSTRY_HISTORY', URL,
            params=dict(scode=code, sdate='1990-01-01', edate=end_date),
            headers={'Accept-Enckey': self._signer.call('getResCode1'),
                     'Referer': 'https://webapi.cninfo.com.cn/', 'Origin': 'https://webapi.cninfo.com.cn'})
        return raw, parse_industry_changes(raw, code, retrieved_at)
