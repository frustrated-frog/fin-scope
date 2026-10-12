from concurrent.futures import ThreadPoolExecutor
from datetime import date
import io
import struct
import time
import zipfile

import pandas as pd
import pytest

from finscope_market_data.providers.base import ProviderError
from finscope_market_data.providers.tdx_package import TdxDailyPackageService
from finscope_market_data.breadth import MarketBreadthService

DAY = date(2026,10,12)


def package(day=DAY, missing_market=None, malformed_sequence=False):
    result = io.BytesIO()
    with zipfile.ZipFile(result,'w',zipfile.ZIP_DEFLATED) as archive:
        for market, code, closing in [('sh','600519',11),('sz','000001',9),('bj','920002',10)]:
            if market == missing_market:
                continue
            cod = bytearray(150)
            cod[:6] = code.encode()
            struct.pack_into('<H',cod,32,3 if malformed_sequence else 0)
            name = '样本'.encode('gbk')
            cod[40:40+len(name)] = name
            bars = bytearray(512)
            struct.pack_into('<5d',bars,4,10,10,12,8,closing)
            struct.pack_into('<Q',bars,56,123456)
            struct.pack_into('<d',bars,72,345678.9)
            suffix = day.strftime('%y%m%d')
            archive.writestr(f'{market}{suffix}.cod',cod)
            archive.writestr(f'{market}{suffix}.md1',bars)
    return result.getvalue()


def test_real_binary_layout_preserves_units_and_exchanges():
    rows = TdxDailyPackageService.parse(package(),DAY)
    assert len(rows) == 3
    assert all(row.is_a_share for row in rows)
    assert rows[0].volume == 123456
    assert rows[0].amount == 345678.9
    assert rows[0].change_pct == pytest.approx(10)
    assert rows[1].change_pct == pytest.approx(-10)


@pytest.mark.parametrize('content', [b'html', package(missing_market='bj'), package(malformed_sequence=True)])
def test_corrupt_or_partial_archive_is_rejected(content):
    with pytest.raises(ProviderError):
        TdxDailyPackageService.parse(content,DAY)


def test_date_specific_member_names_prevent_wrong_day_payload():
    with pytest.raises(ProviderError):
        TdxDailyPackageService.parse(package(),date(2026,10,9))


def test_concurrent_requests_download_once_and_cache_atomically(tmp_path):
    calls = []
    def download(day):
        calls.append(day)
        time.sleep(0.01)
        return package(day)
    service = TdxDailyPackageService(tmp_path,download=download,minimum_a_shares=3)
    with ThreadPoolExecutor(max_workers=4) as pool:
        results = list(pool.map(service.fetch,[DAY]*4))
    assert len(calls) == 1
    assert all(len(rows) == 3 for rows in results)
    assert [path.name for path in tmp_path.iterdir()] == ['20261012.zip']


def test_cache_has_count_and_space_limits_and_preserves_unrelated_files(tmp_path):
    (tmp_path/'user-note.txt').write_text('keep')
    service = TdxDailyPackageService(tmp_path,download=package,max_packages=2,minimum_a_shares=3)
    for number in (8,9,12):
        service.fetch(date(2026,10,number))
    assert sorted(path.name for path in tmp_path.glob('*.zip')) == ['20261009.zip','20261012.zip']
    assert (tmp_path/'user-note.txt').read_text() == 'keep'
    service.max_cache_bytes = 1
    service._prune()
    assert not list(tmp_path.glob('*.zip'))


def test_failed_download_never_creates_cache(tmp_path):
    service = TdxDailyPackageService(tmp_path,download=lambda _:b'bad',minimum_a_shares=3)
    with pytest.raises(ProviderError):
        service.fetch(DAY)
    assert not list(tmp_path.iterdir())


def test_official_breadth_is_available_without_live_sources_or_calendar():
    def fail(*args):
        raise RuntimeError('online unavailable')
    service = MarketBreadthService(tdx_loader=lambda day:TdxDailyPackageService.parse(package(day),day),
        eastmoney_loader=fail,sina_loader=fail,calendar_loader=fail,
        limit_up_loader=fail,limit_down_loader=fail,today_provider=lambda:date(2026,10,13))
    result = service.fetch(DAY)
    assert result.source_family == 'TDX'
    assert result.valid_count == 3
    assert result.advance_count == result.decline_count == result.flat_count == 1
    assert result.total_amount == pytest.approx(3*345678.9)
    assert result.quality_status == 'PARTIAL_FRESH'
