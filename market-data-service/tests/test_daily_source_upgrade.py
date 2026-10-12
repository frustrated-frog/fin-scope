from datetime import date
import asyncio
import json

import httpx
import pytest

from finscope_market_data.models import DailyBar, DataCapability, StockSymbol
from finscope_market_data.providers.base import ProviderError
from finscope_market_data.providers.daily_adjustment import DailyAdjustmentService
from finscope_market_data.providers.tencent_daily import TencentDailyBarProvider
from finscope_market_data.providers.baostock_daily import BaostockDailyBarProvider, FIELDS
from finscope_market_data.providers.http import ProviderHttpClient

SYMBOL = StockSymbol(market='SH', code='600519')


@pytest.mark.asyncio
async def test_tencent_keeps_real_amount_and_applies_ratio_not_difference():
    calls = []
    def respond(request):
        calls.append(request)
        if request.url.path.endswith('qfq.js'):
            return httpx.Response(200, text='var sh600519qfq={"data":[{"d":"1900-01-01","f":"2"},{"d":"2026-10-12","f":"1"}]} /* comment */')
        assert request.url.params['param'].endswith(',250,')
        return httpx.Response(200, json={'code': 0, 'data': {'sh600519': {'day': [
            ['2026-10-09', '100', '102', '104', '99', '12', {}, '0.2', '3.4'],
            ['2026-10-12', '50', '51', '52', '49', '13', {}, '0.3', '3.5'],
        ]}}})
    async with httpx.AsyncClient(transport=httpx.MockTransport(respond)) as client:
        http = ProviderHttpClient(client=client)
        provider = TencentDailyBarProvider(http, DailyAdjustmentService(http), today=lambda: date(2026,10,12))
        bars = await provider.fetch(DataCapability.DAILY_BARS, SYMBOL, limit=2)
    assert bars[0].close == 51
    assert bars[0].volume == 1200
    assert bars[0].amount == 34000
    assert bars[1].close == 51
    assert all(bar.adjustment == 'QFQ' for bar in bars)
    assert len(calls) == 2


@pytest.mark.asyncio
async def test_factor_requests_coalesce_and_cache_with_eviction():
    calls = 0
    async def respond(request):
        nonlocal calls
        calls += 1
        await asyncio.sleep(0.01)
        symbol = request.url.path.split('/')[-2]
        return httpx.Response(200, text='var ' + symbol + 'qfq={"data":[{"d":"1900-01-01","f":"1"}]}')
    async with httpx.AsyncClient(transport=httpx.MockTransport(respond)) as client:
        adjustment = DailyAdjustmentService(ProviderHttpClient(client=client), max_symbols=1)
        await asyncio.gather(*[adjustment.factors(SYMBOL) for _ in range(6)])
        await adjustment.factors(SYMBOL)
        assert calls == 1
        await adjustment.factors(StockSymbol(market='SZ', code='000001'))
        assert len(adjustment._cache) == 1


@pytest.mark.asyncio
async def test_empty_factors_cannot_label_raw_prices_as_qfq():
    async with httpx.AsyncClient(transport=httpx.MockTransport(
            lambda _: httpx.Response(200, text='var sh600519qfq={"data":[]}'))) as client:
        adjustment = DailyAdjustmentService(ProviderHttpClient(client=client))
        with pytest.raises(ProviderError, match='比例复权'):
            await adjustment.adjust(SYMBOL, [DailyBar(symbol=SYMBOL, trade_date='2026-10-12',
                open=10,high=11,low=9,close=10,volume=1)])


def test_tencent_kcb_volume_and_missing_amount_are_not_fabricated():
    symbol = StockSymbol(market='SH', code='688981')
    bars = TencentDailyBarProvider.parse([['2026-10-12','10','11','12','9','123']], symbol, date(2026,10,12))
    assert bars[0].volume == 123
    assert bars[0].amount is None


@pytest.mark.parametrize('row', [
    ['2026-10-13','10','11','12','9','123'],
    ['2026-10-12','10','11','8','9','123'],
    ['2026-10-12','nan','11','12','9','123'],
])
def test_tencent_rejects_future_nonfinite_or_conflicting_rows(row):
    with pytest.raises(ProviderError):
        TencentDailyBarProvider.parse([row], SYMBOL, date(2026,10,12))


def test_baostock_preserves_yuan_amount_and_adjustment():
    payload = {'fields': list(FIELDS), 'rows': [
        ['2026-10-09','sh.600519','50','52','49','51','1200','34567','2','1'],
        ['2026-10-12','sh.600519','51','53','50','52','1300','35678','2','1'],
    ]}
    bars = BaostockDailyBarProvider.parse(payload, SYMBOL, date(2026,10,12))
    assert bars[0].amount == 34567
    assert bars[0].adjustment == 'QFQ'
    payload['rows'][0][8] = '3'
    with pytest.raises(ValueError, match='adjustment'):
        BaostockDailyBarProvider.parse(payload, SYMBOL, date(2026,10,12))


@pytest.mark.asyncio
async def test_new_sources_do_not_claim_bj_history():
    symbol = StockSymbol(market='BJ', code='920002')
    provider = TencentDailyBarProvider()
    try:
        assert not provider.supports(DataCapability.DAILY_BARS, symbol)
    finally:
        await provider.aclose()
    assert not BaostockDailyBarProvider().supports(DataCapability.DAILY_BARS, symbol)


@pytest.mark.asyncio
async def test_tencent_pages_backwards_without_duplicate_dates():
    class Http:
        calls = 0
        async def get_json(self, code, url, **kwargs):
            self.calls += 1
            parts = kwargs['params']['param'].split(',')
            end = date.fromisoformat(parts[3])
            count = int(parts[4])
            rows = []
            from datetime import timedelta
            for offset in reversed(range(count)):
                day = end - timedelta(days=offset)
                rows.append([str(day),'10','10','11','9','1',{},'0','1'])
            return {'code':0,'data':{'sh600519':{'day':rows}}}
    class Adjustment:
        async def adjust(self, symbol, bars):
            return [bar.model_copy(update={'adjustment':'QFQ'}) for bar in bars]
    http = Http()
    provider = TencentDailyBarProvider(http, Adjustment(), today=lambda:date(2026,10,12))
    bars = await provider.fetch(DataCapability.DAILY_BARS, SYMBOL, limit=700)
    assert len(bars) == len({bar.trade_date for bar in bars}) == 700
    assert http.calls == 2
    assert bars[-1].trade_date == '2026-10-12'


@pytest.mark.asyncio
async def test_baostock_timeout_kills_and_reaps_worker(monkeypatch):
    class Process:
        returncode = None
        killed = False
        reaped = False
        async def communicate(self):
            await asyncio.Event().wait()
        def kill(self):
            self.killed = True
            self.returncode = -9
        async def wait(self):
            self.reaped = True
    process = Process()
    async def spawn(*args, **kwargs):
        return process
    monkeypatch.setattr(asyncio, 'create_subprocess_exec', spawn)
    provider = BaostockDailyBarProvider(timeout_seconds=0.01)
    with pytest.raises(ProviderError) as raised:
        await provider.fetch(DataCapability.DAILY_BARS, SYMBOL)
    assert raised.value.error_type == 'TIMEOUT'
    assert process.killed and process.reaped


@pytest.mark.asyncio
async def test_baostock_cancellation_does_not_leave_worker_running(monkeypatch):
    started = asyncio.Event()
    class Process:
        returncode = None
        killed = False
        reaped = False
        async def communicate(self):
            started.set()
            await asyncio.Event().wait()
        def kill(self):
            self.killed = True
            self.returncode = -9
        async def wait(self):
            self.reaped = True
    process = Process()
    async def spawn(*args, **kwargs):
        return process
    monkeypatch.setattr(asyncio, 'create_subprocess_exec', spawn)
    task = asyncio.create_task(BaostockDailyBarProvider().fetch(DataCapability.DAILY_BARS,SYMBOL))
    await started.wait()
    task.cancel()
    with pytest.raises(asyncio.CancelledError):
        await task
    assert process.killed and process.reaped
