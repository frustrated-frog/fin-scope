"""Paced batch Tencent market quotes using the official disclosure directory."""
from __future__ import annotations

from concurrent.futures import ThreadPoolExecutor, as_completed, TimeoutError as BatchTimeout
from datetime import date, datetime, time as daytime
import json
from pathlib import Path
import re
from threading import Lock
import time
from zoneinfo import ZoneInfo

import httpx
import pandas as pd

from finscope_market_data.models import StockSymbol
from finscope_market_data.providers.base import ProviderError
from finscope_market_data.providers.tencent import TencentQuoteProvider


class TencentMarketSnapshotService:
    def __init__(self, directory_cache, *, transport=None, now=None, minimum_coverage=0.8, universe_loader=None):
        self.directory_cache = Path(directory_cache)
        self.transport = transport
        self.now = now or (lambda:datetime.now(ZoneInfo('Asia/Shanghai')))
        self.minimum_coverage = minimum_coverage
        self.universe_loader = universe_loader
        self._fetch_lock = Lock()
        self._pace_lock = Lock()
        self._last_started = 0.0
        self._cached = None

    def _client(self):
        return httpx.Client(trust_env=False,timeout=httpx.Timeout(8,connect=4),transport=self.transport,
            headers={'Referer':'https://gu.qq.com/','User-Agent':'Mozilla/5.0 FinScope-Market-Data/0.1'})

    def _symbols(self,client):
        cached = None
        if self.directory_cache.exists() and self.directory_cache.stat().st_size <= 2*1024*1024:
            try:
                cached = json.loads(self.directory_cache.read_text())
                if time.time()-self.directory_cache.stat().st_mtime < 86400:
                    return self.parse_directory(cached)
            except (ValueError,KeyError,TypeError):
                cached = None
        try:
            response = client.get('https://www.cninfo.com.cn/new/data/szse_stock.json')
            response.raise_for_status()
            if len(response.content) > 2*1024*1024:
                raise ValueError('directory size limit')
            payload = response.json()
            symbols = self.parse_directory(payload)
            self.directory_cache.parent.mkdir(parents=True,exist_ok=True)
            temporary = self.directory_cache.with_suffix('.tmp')
            temporary.write_text(json.dumps(payload,ensure_ascii=False))
            temporary.replace(self.directory_cache)
            return symbols
        except (httpx.HTTPError,ValueError,KeyError,TypeError):
            if cached is not None and time.time()-self.directory_cache.stat().st_mtime < 7*86400:
                return self.parse_directory(cached)
            raise ProviderError('DIRECTORY_UNAVAILABLE','官方证券目录暂不可用，无法构建腾讯全市场批量行情')

    @staticmethod
    def parse_directory(payload):
        entries = payload['stockList']
        if not isinstance(entries,list) or len(entries) > 10000:
            raise ValueError('directory layout')
        grouped = {}
        for entry in entries:
            if not isinstance(entry, dict):
                raise ValueError('directory entry layout')
            code = entry.get('code','')
            if entry.get('category') != 'A股' or not re.fullmatch(r'\d{6}',code):
                continue
            if code.startswith(('60','688','689')):
                market = 'SH'
            elif code.startswith(('00','30')):
                market = 'SZ'
            elif code.startswith(('43','83','87','92')):
                market = 'BJ'
            else:
                continue
            # CNINFO may retain both codes around BSE code migrations.
            key = entry.get('orgId') or market+code
            candidate = StockSymbol(market=market,code=code)
            if key not in grouped or code.startswith('92'):
                grouped[key] = candidate
        symbols = sorted(set(grouped.values()),key=lambda symbol:symbol.cache_key)
        if not symbols:
            raise ValueError('empty A share directory')
        return symbols

    def fetch(self,business_date:date):
        with self._fetch_lock:
            now = self.now()
            if self._cached is not None and self._cached[0] == business_date and time.monotonic()-self._cached[1] < 15:
                return self._cached[2].copy()
            with self._client() as client:
                universe_warning = None
                try:
                    symbols = self.universe_loader(business_date) if self.universe_loader is not None else self._symbols(client)
                except (ProviderError, OSError):
                    symbols = self._symbols(client)
                    universe_warning = '官方盘后证券列表暂缺，使用巨潮登记目录；上市状态覆盖待补充'
                batches = [symbols[index:index+80] for index in range(0,len(symbols),80)]
                rows,errors = [],[]
                def batch(values):
                    with self._pace_lock:
                        wait = 0.15-(time.monotonic()-self._last_started)
                        if wait > 0:
                            time.sleep(wait)
                        self._last_started = time.monotonic()
                    response = client.get('https://qt.gtimg.cn/q='+','.join(symbol.prefixed_code for symbol in values))
                    response.raise_for_status()
                    raw = response.content.decode('gb18030')
                    lines = {match.group(1):match.group(0) for match in re.finditer(r'v_([a-z]{2}\d{6})="[^"\n]*";',raw)}
                    result = []
                    for symbol in values:
                        line = lines.get(symbol.prefixed_code)
                        if not line:
                            continue
                        try:
                            quote = TencentQuoteProvider.parse(line,symbol)
                        except ProviderError:
                            continue
                        observed = quote.observed_at.astimezone(ZoneInfo('Asia/Shanghai'))
                        if observed.date() != business_date:
                            continue
                        intraday = business_date == now.date() and (daytime(9,30) <= now.time().replace(tzinfo=None) <= daytime(11,30)
                            or daytime(13,0) <= now.time().replace(tzinfo=None) <= daytime(15,0))
                        if intraday and not -60 <= (now-observed).total_seconds() <= 180:
                            continue
                        if quote.change_pct is None or quote.amount is None:
                            continue
                        result.append({'code':symbol.prefixed_code,'trade':quote.price,
                                       'change_pct':quote.change_pct,'amount':quote.amount})
                    return result
                with ThreadPoolExecutor(max_workers=4,thread_name_prefix='tencent-market') as pool:
                    futures = [pool.submit(batch,values) for values in batches]
                    try:
                        for future in as_completed(futures, timeout=35):
                            try:
                                rows.extend(future.result())
                            except (httpx.HTTPError,UnicodeError,ProviderError) as error:
                                errors.append(type(error).__name__)
                    except BatchTimeout:
                        errors.append("BATCH_TIME_BUDGET")
                        for future in futures:
                            future.cancel()
            coverage = len(rows)/len(symbols)
            if coverage < self.minimum_coverage:
                raise ProviderError('MARKET_COVERAGE_LOW',f'腾讯当日有效报价覆盖 {len(rows)}/{len(symbols)}，尝试其他来源')
            frame = pd.DataFrame(rows)
            frame.attrs['warnings'] = [f'腾讯按官方证券目录批量采集，有效当日报价 {len(rows)}/{len(symbols)}；过期报价不参与统计']
            if universe_warning:
                frame.attrs['warnings'].append(universe_warning)
            if errors:
                frame.attrs['warnings'].append(f'{len(errors)} 个行情批次暂缺，其余批次已保留')
            frame.attrs['partial'] = len(rows) < len(symbols) or universe_warning is not None
            self._cached = (business_date,time.monotonic(),frame)
            return frame.copy()
