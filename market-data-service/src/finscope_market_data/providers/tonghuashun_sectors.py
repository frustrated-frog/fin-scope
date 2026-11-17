"""Bounded access to the public THS sector pages using the existing HTTP client."""
import asyncio
from io import StringIO
from pathlib import Path
import re

import pandas as pd
from bs4 import BeautifulSoup

from finscope_market_data.providers.base import ProviderError
from finscope_market_data.providers.http import ProviderHttpClient

SUMMARY_COLUMNS = ('序号', '板块', '涨跌幅', '总成交量', '总成交额', '净流入',
                   '上涨家数', '下跌家数', '均价', '领涨股', '领涨股-最新价', '领涨股-涨跌幅')


class TonghuashunSectorSource:
    def __init__(self, http_factory=None):
        self.http_factory = http_factory or (lambda: ProviderHttpClient(timeout_seconds=8))

    def names(self, category):
        if category not in {'INDUSTRY', 'CONCEPT'}:
            raise ValueError('Unsupported sector category')
        return asyncio.run(self._bounded(self._names(category)))

    def summary(self):
        return asyncio.run(self._bounded(self._summary()))

    async def _bounded(self, operation):
        try:
            return await asyncio.wait_for(operation, timeout=25)
        except TimeoutError as error:
            raise ProviderError('TIMEOUT', '同花顺板块读取超过25秒总时限') from error

    async def _names(self, category):
        suffix = 'thshy/detail/code/881272/' if category == 'INDUSTRY' else 'gn/detail/code/301558/'
        http = self.http_factory()
        try:
            text = await self._page(http, suffix, self._headers())
            links = BeautifulSoup(text, 'lxml').select('div.cate_inner a[href]')
            rows = []
            for link in links:
                match = re.search(r'/code/(\d{6})/', link['href'])
                if match and link.get_text(strip=True):
                    rows.append({'name': link.get_text(strip=True), 'code': match[1]})
            if not rows:
                raise ProviderError('SCHEMA_DRIFT', '同花顺板块名称页未提供有效目录', False)
            return pd.DataFrame(rows).drop_duplicates('code')
        finally:
            await http.aclose()

    async def _summary(self):
        http = self.http_factory()
        try:
            headers = self._headers()
            first = await self._page(http, 'thshy/index/field/199112/order/desc/page/1/ajax/1/', headers)
            info = BeautifulSoup(first, 'lxml').select_one('span.page_info')
            match = re.fullmatch(r'\s*1\s*/\s*(\d+)\s*', info.get_text() if info else '')
            if not match or not 1 <= int(match[1]) <= 5:
                raise ProviderError('SCHEMA_DRIFT', '同花顺行业榜页码缺失或超过上限', False)
            pages = [first]
            for page in range(2, int(match[1]) + 1):
                pages.append(await self._page(http, f'thshy/index/field/199112/order/desc/page/{page}/ajax/1/', headers))
            frames = []
            for page in pages:
                try:
                    frame = pd.read_html(StringIO(page))[0]
                    if len(frame.columns) != len(SUMMARY_COLUMNS):
                        raise ValueError('Unexpected columns')
                    frame.columns = SUMMARY_COLUMNS
                    frames.append(frame)
                except (ValueError, IndexError) as error:
                    raise ProviderError('SCHEMA_DRIFT', '同花顺行业榜未返回完整表格', False) from error
            result = pd.concat(frames, ignore_index=True)
            for column in SUMMARY_COLUMNS:
                if column not in ('板块', '领涨股'):
                    result[column] = pd.to_numeric(result[column], errors='coerce')
            return result
        finally:
            await http.aclose()

    async def _page(self, http, suffix, headers):
        text = await http.get_text('TONGHUASHUN_SECTOR', 'https://q.10jqka.com.cn/' + suffix, headers=headers)
        if len(text.encode('utf-8')) > 2 * 1024 * 1024:
            raise ProviderError('RESPONSE_TOO_LARGE', '同花顺板块页面超过体积限制', False)
        return text

    @staticmethod
    def _headers():
        # Reuse AKShare's bundled public-site client script, without changing any global sessions.
        from akshare.datasets import get_ths_js
        from py_mini_racer import MiniRacer
        engine = MiniRacer()
        try:
            engine.eval(Path(get_ths_js('ths.js')).read_text(encoding='utf-8'))
            cookie = engine.call('v')
        finally:
            engine.close()
        return {'User-Agent': 'Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 Chrome/131.0.0.0 Safari/537.36',
                'Cookie': f'v={cookie}', 'Referer': 'https://q.10jqka.com.cn/'}
