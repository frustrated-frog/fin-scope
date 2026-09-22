import asyncio
from unittest.mock import AsyncMock

import pytest
from pydantic import BaseModel

from finscope_market_data.discovery.schemas import DiscoveryRequest
from finscope_market_data.discovery.service import StockDiscoveryService


class Result(BaseModel):
    values: list[int]


@pytest.mark.asyncio
async def test_duplicate_requests_share_computation_and_reuse_success():
    service = StockDiscoveryService([], object())
    entered = asyncio.Event()
    release = asyncio.Event()

    async def compute(request):
        entered.set()
        await release.wait()
        return Result(values=[1])

    service._discover_once = AsyncMock(side_effect=compute)
    request = DiscoveryRequest(business_date="2026-09-22")
    first = asyncio.create_task(service.discover(request))
    await entered.wait()
    second = asyncio.create_task(service.discover(request))
    await asyncio.sleep(0)
    release.set()
    results = await asyncio.gather(first, second)
    results[0].values.append(2)

    assert results[1].values == [1]
    assert (await service.discover(request)).values == [1]
    assert service._discover_once.await_count == 1
    assert not service._discovery_tasks


@pytest.mark.asyncio
async def test_disconnected_waiter_does_not_cancel_the_running_job():
    service = StockDiscoveryService([], object())
    entered = asyncio.Event()
    release = asyncio.Event()

    async def compute(request):
        entered.set()
        await release.wait()
        return Result(values=[1])

    service._discover_once = AsyncMock(side_effect=compute)
    request = DiscoveryRequest(business_date="2026-09-22")
    waiter = asyncio.create_task(service.discover(request))
    await entered.wait()
    waiter.cancel()
    with pytest.raises(asyncio.CancelledError):
        await waiter
    retry = asyncio.create_task(service.discover(request))
    await asyncio.sleep(0)
    release.set()

    assert (await retry).values == [1]
    assert service._discover_once.await_count == 1


@pytest.mark.asyncio
async def test_failed_computation_can_retry_without_reusing_the_failure():
    service = StockDiscoveryService([], object())
    service._discover_once = AsyncMock(side_effect=[RuntimeError("upstream unavailable"), Result(values=[2])])
    request = DiscoveryRequest(business_date="2026-09-22")
    with pytest.raises(RuntimeError, match="upstream unavailable"):
        await service.discover(request)

    assert (await service.discover(request)).values == [2]
    assert service._discover_once.await_count == 2


@pytest.mark.asyncio
async def test_distinct_parameters_do_not_share_results_and_cache_is_bounded():
    service = StockDiscoveryService([], object())
    service._discover_once = AsyncMock(return_value=Result(values=[1]))
    for budget in range(6000, 6010):
        await service.discover(DiscoveryRequest(business_date="2026-09-22", budget=budget))
    assert service._discover_once.await_count == 10
    assert len(service._completed_discoveries) == 8


@pytest.mark.asyncio
async def test_capacity_limit_rejects_new_jobs_but_allows_existing_waiters():
    service = StockDiscoveryService([], object())
    release = asyncio.Event()

    async def compute(request):
        await release.wait()
        return Result(values=[1])

    service._discover_once = AsyncMock(side_effect=compute)
    requests = [DiscoveryRequest(business_date="2026-09-22", budget=6000 + i) for i in range(5)]
    waiters = [asyncio.create_task(service.discover(request)) for request in requests[:4]]
    try:
        await asyncio.sleep(0)
        with pytest.raises(RuntimeError, match="并发计算已满"):
            await service.discover(requests[4])
        duplicate = asyncio.create_task(service.discover(requests[0]))
        await asyncio.sleep(0)
        release.set()
        await asyncio.gather(*waiters, duplicate)
        assert service._discover_once.await_count == 4
    finally:
        release.set()
        await asyncio.gather(*waiters)


@pytest.mark.asyncio
async def test_expired_success_is_recomputed(monkeypatch):
    service = StockDiscoveryService([], object())
    service._discover_once = AsyncMock(return_value=Result(values=[1]))
    request = DiscoveryRequest(business_date="2026-09-22")
    await service.discover(request)
    key = next(iter(service._completed_discoveries))
    _, report = service._completed_discoveries[key]
    service._completed_discoveries[key] = (0, report)

    await service.discover(request)

    assert service._discover_once.await_count == 2
