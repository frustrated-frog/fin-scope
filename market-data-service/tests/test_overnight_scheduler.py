import asyncio
from concurrent.futures import ThreadPoolExecutor
from datetime import datetime
from threading import Event, Thread

from test_overnight_automation import flow


def test_prediction_scheduler_does_not_wait_for_shared_executor(flow, monkeypatch):
    from finscope_market_data.overnight import automation
    auto, now, calls, scans = flow
    now[0] = datetime(2026, 11, 11, 14, 40)
    entered, release = Event(), Event()
    def blocking_background():
        entered.set()
        assert release.wait(5)
    async def until(predicate):
        async with asyncio.timeout(3):
            while not predicate():
                await asyncio.sleep(.01)
    async def scenario():
        loop = asyncio.get_running_loop()
        loop.set_default_executor(ThreadPoolExecutor(max_workers=1))
        background = loop.run_in_executor(None, blocking_background)
        stop = asyncio.Event()
        task = None
        try:
            await until(entered.is_set)
            task = asyncio.create_task(auto.run(stop))
            await until(lambda: auto.store.job('2026-11-11|SCAN|14:45') is not None)
            now[0] = now[0].replace(minute=45)
            await until(lambda: bool(calls))
            assert calls[0].cutoff == '14:45' and not background.done()
            assert auto.store.get('heartbeat')['queueDelaySeconds'] is not None
        finally:
            stop.set()
            release.set()
            if task:
                await task
            await background
    monkeypatch.setattr(automation, 'POLL_SECONDS', .01)
    asyncio.run(scenario())


def test_gap_is_recorded_without_guessing_cause_or_backfilling(flow):
    auto, now, calls, _ = flow
    now[0] = datetime(2026, 11, 11, 14, 35)
    auto.tick()
    now[0] = now[0].replace(minute=54)
    auto.tick()
    job = auto.store.job('2026-11-11|TAIL|14:45')
    assert job['status'] == 'MISSED' and not calls
    assert job['reasonCode'] == 'SCAN_NOT_READY'
    assert job['windowDiagnostic']['gapSeconds'] == 19 * 60
    assert job['windowDiagnostic']['scanStatus'] == 'NOT_STARTED'
    auto.tick()
    assert auto.store.job(job['key']) == job


def test_concurrent_ticks_do_not_queue_duplicate_work(flow, monkeypatch):
    auto, _, _, _ = flow
    entered, release = Event(), Event()
    calls = []
    def blocked(queued_at):
        calls.append(True)
        entered.set()
        assert release.wait(3)
    monkeypatch.setattr(auto, '_tick', blocked)
    thread = Thread(target=auto.tick)
    thread.start()
    try:
        assert entered.wait(2)
        auto.tick()
        assert calls == [True]
    finally:
        release.set()
        thread.join(2)
