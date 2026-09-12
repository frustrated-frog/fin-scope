import asyncio
from types import SimpleNamespace
from unittest.mock import AsyncMock

import pytest

from test_daily_research import store, save, TODAY, NOW
from finscope_market_data.models import DataCapability, StockSymbol


def service(store, fetch=None, **kwargs):
    from finscope_market_data.research_members import ResearchMemberService
    return ResearchMemberService(SimpleNamespace(snapshots=store, fetch=fetch or AsyncMock()), now=lambda: NOW, **kwargs)


async def test_local_ready_does_not_fetch(store):
    save(store)
    subject = service(store)
    result = await subject.ensure(TODAY, '600001.SH')
    assert result.status == 'READY'
    assert result.valid_bars == 22
    subject.router.fetch.assert_not_called()


@pytest.mark.parametrize('options,reason,valid', [({'missing': True}, 'DATE_MISSING', 0), ({'gap': True}, 'HISTORY_GAP', 11), ({'adjustment': 'NONE'}, 'ADJUSTMENT_REQUIRED', 0)])
async def test_partial_requires_persisted_continuous_qfq(store, options, reason, valid):
    save(store, **options)
    symbol = StockSymbol(market='SH', code='600001')
    fetch = AsyncMock(return_value=store.load(DataCapability.DAILY_BARS, symbol))
    result = await service(store, fetch).ensure(TODAY, '600001.SH')
    assert (result.status, result.reason, result.valid_bars) == ('PARTIAL', reason, valid)


async def test_fetch_persists_and_duplicate_calls_coalesce(store):
    async def fetch(*args, **kwargs):
        await asyncio.sleep(.01)
        save(store)
        return store.load(DataCapability.DAILY_BARS, StockSymbol(market='SH', code='600001'))
    remote = AsyncMock(side_effect=fetch)
    subject = service(store, remote)
    results = await asyncio.gather(*(subject.ensure(TODAY, '600001.SH') for _ in range(5)))
    assert all(result.status == 'READY' for result in results)
    assert remote.call_count == 1
    assert remote.call_args.kwargs['limit'] >= 250


@pytest.mark.parametrize('timeout', [False, True])
async def test_failure_timeout_and_cooldown(store, timeout):
    async def fetch(*args, **kwargs):
        if timeout:
            await asyncio.sleep(1)
        raise RuntimeError('secret-token')
    remote = AsyncMock(side_effect=fetch)
    subject = service(store, remote, timeout_seconds=.01)
    result = await subject.ensure(TODAY, '600001.SH')
    assert (result.status, result.reason) == ('FAILED', 'UPSTREAM_FAILED')
    assert 'secret' not in result.message
    await subject.ensure(TODAY, '600001.SH')
    assert remote.call_count == 1


async def test_invalid_dates_no_network_and_strict_symbol(store):
    from datetime import date
    subject = service(store)
    for day in [date(2026, 8, 22), date(2027, 1, 1), date.min]:
        assert (await subject.ensure(day, '600001.SH')).status == 'SKIPPED'
    for code in ['600001', '600001.SZ', '510300.SH', '000001.SH', '600001.sh', '600０01.SH']:
        with pytest.raises(ValueError):
            await subject.ensure(TODAY, code)
    subject.router.fetch.assert_not_called()


def test_member_endpoint_and_reused_cache_service(store):
    from fastapi.testclient import TestClient
    from finscope_market_data.app import create_app
    from unittest.mock import patch
    save(store)
    app = create_app(router=SimpleNamespace(snapshots=store, fetch=AsyncMock()))
    client = TestClient(app)
    response = client.post('/v1/markets/CN-A/daily-research/members/600001.SH?business_date=2026-08-21')
    assert response.status_code == 200
    assert response.json()['status'] == 'READY'
    assert client.post('/v1/markets/CN-A/daily-research/members/510300.SH?business_date=2026-08-21').status_code == 422
    with patch.object(store, 'load_daily_bar_panel', wraps=store.load_daily_bar_panel) as scan:
        for _ in range(2):
            assert client.get('/v1/markets/CN-A/daily-research?business_date=2026-08-21').status_code == 200
        assert scan.call_count == 1


async def test_bounded_concurrency_cooldown_expiry_and_history_limit(store):
    active = 0
    peak = 0
    async def fetch(*args, **kwargs):
        nonlocal active, peak
        active += 1
        peak = max(peak, active)
        await asyncio.sleep(.01)
        active -= 1
        raise RuntimeError('offline')
    remote = AsyncMock(side_effect=fetch)
    subject = service(store, remote, cooldown_seconds=.01)
    await asyncio.gather(*(subject.ensure(TODAY, f'60000{i}.SH') for i in range(5)))
    assert peak == 2
    await asyncio.sleep(.02)
    await subject.ensure(TODAY, '600004.SH')
    assert remote.call_count == 6
    save(store, missing=True)
    symbol = StockSymbol(market='SH', code='600001')
    envelope = store.load(DataCapability.DAILY_BARS, symbol)
    envelope.data = envelope.data * 20
    store.save(envelope)
    await service(store, remote).ensure(TODAY, '600001.SH')
    assert remote.call_args.kwargs['limit'] == 420


async def test_wrong_symbol_and_success_without_persistence_not_ready(store):
    save(store)
    symbol = StockSymbol(market='SH', code='600001')
    envelope = store.load(DataCapability.DAILY_BARS, symbol)
    for bar in envelope.data:
        bar.symbol = StockSymbol(market='SH', code='600002')
    store.save(envelope)
    result = await service(store, AsyncMock(return_value=envelope)).ensure(TODAY, '600001.SH')
    assert result.status == 'PARTIAL'
    assert result.valid_bars == 0
    assert result.reason == 'DATE_MISSING'


async def test_shorter_provider_response_keeps_existing_history(store):
    from test_router import FakeProvider, daily_bars
    from finscope_market_data.router import ProviderRouter
    from finscope_market_data.health import ProviderHealthRegistry
    symbol = StockSymbol(market='SH', code='600001')
    save(store, missing=True)
    envelope = store.load(DataCapability.DAILY_BARS, symbol)
    envelope.data = daily_bars(symbol, 420)
    store.save(envelope)
    provider = FakeProvider('SHORT', 'SHORT', 1, [daily_bars(symbol, 250)])
    provider.capabilities = {DataCapability.DAILY_BARS}
    router = ProviderRouter([provider], store, ProviderHealthRegistry(), max_retries=0)
    from finscope_market_data.research_members import ResearchMemberService
    result = await ResearchMemberService(router, now=lambda: NOW).ensure(TODAY, '600001.SH')
    assert result.status != 'READY'
    assert len(store.load(DataCapability.DAILY_BARS, symbol).data) == 420
    assert provider.requests[0]['limit'] == 420


async def test_malformed_local_bar_does_not_crash_endpoint(store):
    import sqlite3
    save(store)
    with sqlite3.connect(store.path) as connection:
        connection.execute("UPDATE market_data_snapshot SET payload_json=json_set(payload_json, '$.data[0].close', NULL)")
    result = await service(store, AsyncMock(side_effect=RuntimeError('offline'))).ensure(TODAY, '600001.SH')
    assert result.status == 'FAILED'
    assert result.reason == 'UPSTREAM_FAILED'


async def test_corrupt_local_data_can_recover_from_provider(store):
    import sqlite3
    from test_router import FakeProvider
    from finscope_market_data.router import ProviderRouter
    from finscope_market_data.health import ProviderHealthRegistry
    from finscope_market_data.research_members import ResearchMemberService
    save(store)
    symbol = StockSymbol(market='SH', code='600001')
    healthy = store.load(DataCapability.DAILY_BARS, symbol).data
    with sqlite3.connect(store.path) as connection:
        connection.execute("UPDATE market_data_snapshot SET payload_json=json_set(payload_json, '$.data[0].close', NULL)")
    provider = FakeProvider('REPAIR', 'REPAIR', 1, [healthy])
    provider.capabilities = {DataCapability.DAILY_BARS}
    router = ProviderRouter([provider], store, ProviderHealthRegistry(), max_retries=0)
    result = await ResearchMemberService(router, now=lambda: NOW).ensure(TODAY, '600001.SH')
    assert result.status == 'READY'
    assert result.valid_bars == 22
    assert provider.calls == 1


async def test_timeout_includes_semaphore_queue(store):
    subject = service(store, timeout_seconds=.01)
    await subject._semaphore.acquire()
    await subject._semaphore.acquire()
    try:
        result = await asyncio.wait_for(subject.ensure(TODAY, '600001.SH'), timeout=.1)
        assert result.status == 'FAILED'
        assert result.reason == 'UPSTREAM_FAILED'
        subject.router.fetch.assert_not_called()
    finally:
        subject._semaphore.release()
        subject._semaphore.release()
