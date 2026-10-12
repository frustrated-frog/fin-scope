"""Official date-specific TDX batches with bounded atomic disk caching."""
from __future__ import annotations

from dataclasses import dataclass
from datetime import date, datetime
import io
import math
import os
from pathlib import Path
import re
import struct
from threading import RLock
import zipfile
from zoneinfo import ZoneInfo

import httpx

from finscope_market_data.models import StockSymbol
from finscope_market_data.providers.base import ProviderError


@dataclass(frozen=True)
class TdxDailyRecord:
    symbol: StockSymbol
    business_date: date
    name: str
    previous_close: float
    open: float
    high: float
    low: float
    close: float
    volume: int
    amount: float

    @property
    def is_a_share(self):
        code, market = self.symbol.code, self.symbol.market.value
        return ((market == 'SH' and code.startswith(('60', '688', '689')))
            or (market == 'SZ' and code.startswith(('00', '30')))
            or (market == 'BJ' and code.startswith(('43', '83', '87', '92'))))

    @property
    def change_pct(self):
        return (self.close / self.previous_close - 1) * 100 if self.previous_close > 0 else None


class TdxDailyPackageService:
    source_code = 'TDX_OFFICIAL_DAILY_PACKAGE'
    source_family = 'TDX'
    max_download_bytes = 16 * 1024 * 1024
    max_expanded_bytes = 96 * 1024 * 1024

    def __init__(self, cache_dir, *, download=None, max_packages=7,
                 max_cache_bytes=64 * 1024 * 1024, minimum_a_shares=1000):
        self.cache_dir = Path(cache_dir)
        self.cache_dir.mkdir(parents=True, exist_ok=True)
        self.download = download or self._download
        self.max_packages = max_packages
        self.max_cache_bytes = max_cache_bytes
        self.minimum_a_shares = minimum_a_shares
        self._lock = RLock()

    def fetch(self, business_date: date):
        if business_date > datetime.now(ZoneInfo("Asia/Shanghai")).date():
            raise ProviderError('INVALID_DATE', '通达信盘后包不能请求未来日期', False)
        key = business_date.strftime('%Y%m%d')
        path = self.cache_dir / f'{key}.zip'
        # A single downloader bounds provider pressure and coalesces same-day calls.
        with self._lock:
            if path.exists() and not path.is_symlink():
                try:
                    if path.stat().st_size > self.max_download_bytes:
                        raise ValueError('oversized cached package')
                    return self._checked_parse(path.read_bytes(), business_date)
                except (ValueError, ProviderError):
                    path.unlink(missing_ok=True)
            content = self.download(business_date)
            records = self._checked_parse(content, business_date)
            temporary = self.cache_dir / f'.{key}.{os.getpid()}.tmp'
            try:
                temporary.write_bytes(content)
                temporary.replace(path)
                self._prune()
            finally:
                temporary.unlink(missing_ok=True)
            return records

    def _checked_parse(self, content, business_date):
        records = self.parse(content, business_date)
        counts = {market: sum(record.is_a_share and record.symbol.market.value == market
                             for record in records) for market in ('SH', 'SZ', 'BJ')}
        if sum(counts.values()) < self.minimum_a_shares or not counts['SH'] or not counts['SZ']:
            raise ProviderError('PARTIAL_PACKAGE', '通达信盘后包 A 股覆盖不足', False)
        if self.minimum_a_shares >= 1000 and (counts['SH'] < 500 or counts['SZ'] < 500):
            raise ProviderError('PARTIAL_PACKAGE', '通达信沪深市场覆盖不足', False)
        if business_date >= date(2022,5,6) and not counts['BJ']:
            raise ProviderError('PARTIAL_PACKAGE', '通达信盘后包缺少北交所 A 股', False)
        return records

    def _prune(self):
        paths = sorted((path for path in self.cache_dir.iterdir()
                        if re.fullmatch(r'\d{8}\.zip', path.name) and not path.is_symlink()), reverse=True)
        total = 0
        for index, path in enumerate(paths):
            size = path.stat().st_size
            if index >= self.max_packages or total + size > self.max_cache_bytes:
                path.unlink()
            else:
                total += size

    def _download(self, business_date):
        url = 'https://www.tdx.com.cn/products/data/data/g4day/' + business_date.strftime('%Y%m%d') + '.zip'
        try:
            with httpx.Client(trust_env=False, timeout=httpx.Timeout(20, connect=4), follow_redirects=True) as client:
                with client.stream('GET', url) as response:
                    if response.status_code == 404:
                        raise ProviderError('PACKAGE_NOT_READY', '通达信该日期盘后包尚未发布或不存在', False)
                    if response.status_code != 200:
                        raise ProviderError('PACKAGE_HTTP_ERROR', f'通达信盘后包 HTTP {response.status_code}',
                                            response.status_code in {429,500,502,503,504})
                    content = bytearray()
                    for chunk in response.iter_bytes():
                        content.extend(chunk)
                        if len(content) > self.max_download_bytes:
                            raise ProviderError('PACKAGE_TOO_LARGE', '通达信盘后包超过下载限额', False)
                    return bytes(content)
        except httpx.HTTPError as error:
            raise ProviderError('PACKAGE_CONNECTION_ERROR', '通达信盘后包连接失败') from error

    @classmethod
    def parse(cls, content, business_date):
        if len(content) > cls.max_download_bytes:
            raise ProviderError('PACKAGE_TOO_LARGE', '通达信压缩包超过限额', False)
        key = business_date.strftime('%Y%m%d')[2:]
        try:
            with zipfile.ZipFile(io.BytesIO(content)) as archive:
                members = archive.infolist()
                if len(members) > 20 or sum(item.file_size for item in members) > cls.max_expanded_bytes:
                    raise ValueError('expanded archive limit')
                if len({item.filename for item in members}) != len(members):
                    raise ValueError('duplicate archive members')
                names = {item.filename for item in members}
                records = []
                for market in ('sh','sz','bj'):
                    cod_name, bars_name = f'{market}{key}.cod', f'{market}{key}.md1'
                    if market == 'bj' and business_date < date(2022,5,6) and cod_name not in names and bars_name not in names:
                        continue
                    cod, blocks = archive.read(cod_name), archive.read(bars_name)
                    if len(cod) % 150 or len(blocks) % 512 or len(cod)//150 != len(blocks)//512:
                        raise ValueError('unaligned mapping')
                    codes, sequences = set(), set()
                    for offset in range(0,len(cod),150):
                        row = cod[offset:offset+150]
                        code = row[:6].decode('ascii')
                        sequence = struct.unpack_from('<H',row,32)[0]
                        if not re.fullmatch('[0-9]{6}',code) or code in codes or sequence in sequences:
                            raise ValueError('invalid or duplicate mapping')
                        codes.add(code)
                        sequences.add(sequence)
                        start = sequence*512
                        if start+512 > len(blocks):
                            raise ValueError('block out of bounds')
                        previous,opening,high,low,close = struct.unpack_from('<5d',blocks,start+4)
                        volume = struct.unpack_from('<Q',blocks,start+56)[0]
                        amount = struct.unpack_from('<d',blocks,start+72)[0]
                        if not all(math.isfinite(value) for value in (previous,opening,high,low,close,amount)) or amount < 0:
                            raise ValueError('invalid numeric payload')
                        if close <= 0:
                            continue
                        if high > 0 and low > 0 and (low > min(opening,close) or high < max(opening,close)):
                            raise ValueError('OHLC conflict')
                        name = row[40:72].split(b'\0')[0].decode('gbk').strip()
                        if not name:
                            raise ValueError('empty security name')
                        records.append(TdxDailyRecord(StockSymbol(market=market.upper(),code=code),business_date,
                            name,previous,opening,high,low,close,volume,amount))
                return records
        except (ValueError,KeyError,UnicodeError,struct.error,zipfile.BadZipFile,RuntimeError,EOFError) as error:
            raise ProviderError('PACKAGE_SCHEMA_DRIFT', '通达信盘后包损坏、日期错配或格式变化', False) from error
