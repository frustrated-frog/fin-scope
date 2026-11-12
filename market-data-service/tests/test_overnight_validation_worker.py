import asyncio
from concurrent.futures import ThreadPoolExecutor
from datetime import datetime, timedelta
from threading import Event, Thread
from types import SimpleNamespace

import pytest

from finscope_market_data.overnight.automation_store import AutomationStore
from finscope_market_data.overnight.joint_research import OvernightJointResearch
from finscope_market_data.overnight.joint_worker import OvernightJointWorker
from finscope_market_data.overnight.store import OvernightStore
from finscope_market_data.overnight.universe import POOL_KEY
from finscope_market_data.overnight.validation_worker import OvernightValidationWorker, needs_refresh

KEYS = ('errorAudit', 'weeklyDirectionAudit', 'marketForward', 'freshForward', 'selectionForward', 'candidateForward', 'productionDirection', 'jointForward', 'closeDirectionForward', 'contextDirectionForward')


def environment(tmp_path):
    store = OvernightStore(tmp_path / 'validation.db')
    now = [datetime(2026, 11, 5, 14, 30)]
    auto = SimpleNamespace(store=AutomationStore(store), service=SimpleNamespace(store=store), clock=lambda: now[0])
    research = OvernightJointResearch(store, auto.store)
    return auto, research, now


def test_refreshes_all_checks_in_trading_window_and_restarts_without_duplicate_work(tmp_path):
    auto, research, now = environment(tmp_path)
    worker = OvernightValidationWorker(auto, research)
    worker.tick()
    assert all(auto.store.get(key)['computedAt'] == now[0].isoformat() for key in KEYS)
    first = auto.store.get('validationWorker')
    assert first['status'] == 'IDLE' and first['refreshedKeys'] == list(KEYS)
    now[0] += timedelta(minutes=4, seconds=59)
    OvernightValidationWorker(auto, research).tick()
    assert auto.store.get('validationWorker')['refreshedKeys'] == []
    assert auto.store.get('validationWorker')['lastCompletedAt'] == first['lastCompletedAt']
    now[0] += timedelta(seconds=1)
    worker.tick()
    assert all(auto.store.get(key)['computedAt'] == now[0].isoformat() for key in KEYS)


def test_one_missing_statistic_is_not_blocked_by_fresh_error_audit(tmp_path):
    auto, research, now = environment(tmp_path)
    for key in KEYS:
        if key != 'marketForward':
            auto.store.put(key, {'computedAt': now[0].isoformat(), 'sentinel': True})
    OvernightValidationWorker(auto, research).tick()
    assert auto.store.get('validationWorker')['refreshedKeys'] == ['marketForward']
    assert auto.store.get('errorAudit')['sentinel']


def test_failure_keeps_last_good_value_and_does_not_block_other_checks(tmp_path, monkeypatch):
    auto, research, now = environment(tmp_path)
    old = {'computedAt': (now[0] - timedelta(minutes=10)).isoformat(), 'groups': ['previous']}
    auto.store.put('errorAudit', old)
    from finscope_market_data.overnight import validation_worker
    original = validation_worker.build_error_audit
    def fail(*args):
        raise ValueError('test failure')
    monkeypatch.setattr(validation_worker, 'build_error_audit', fail)
    worker = OvernightValidationWorker(auto, research)
    worker.tick()
    assert auto.store.get('errorAudit') == old
    status = auto.store.get('validationWorker')
    assert status['status'] == 'PARTIAL' and status['errors'] == {'errorAudit': 'ValueError'}
    assert status['lastCompletedAt'] is None
    assert auto.store.get('marketForward')['computedAt'] == now[0].isoformat()
    monkeypatch.setattr(validation_worker, 'build_error_audit', original)
    now[0] += timedelta(seconds=20)
    worker.tick()
    status = auto.store.get('validationWorker')
    assert status['status'] == 'IDLE' and status['errors'] == {}
    assert status['refreshedKeys'] == ['errorAudit']


def test_disable_stops_refresh_without_destroying_results(tmp_path):
    auto, research, now = environment(tmp_path)
    worker = OvernightValidationWorker(auto, research)
    worker.tick()
    old = auto.store.get('marketForward')
    auto.store.put('context', {'enabled': False})
    now[0] += timedelta(minutes=6)
    worker.tick()
    assert auto.store.get('marketForward') == old
    assert auto.store.get('validationWorker')['status'] == 'DISABLED'


@pytest.mark.parametrize('value', [None, {}, {'computedAt': 'invalid'}, {'computedAt': '2099-01-01T12:00:00'}])
def test_invalid_or_future_refresh_times_are_repaired(value):
    assert needs_refresh(value, datetime(2026, 11, 5))


def test_overlapping_ticks_do_not_duplicate_evaluation(tmp_path, monkeypatch):
    auto, research, _ = environment(tmp_path)
    worker = OvernightValidationWorker(auto, research)
    entered, release = Event(), Event()
    calls = []
    def block():
        calls.append(True)
        entered.set()
        assert release.wait(5)
    monkeypatch.setattr(worker, '_refresh', block)
    thread = Thread(target=worker.tick)
    thread.start()
    try:
        assert entered.wait(2)
        worker.tick()
        assert len(calls) == 1
    finally:
        release.set()
        thread.join(3)
    assert not thread.is_alive()


def test_validation_refreshes_while_training_occupies_entire_default_executor(tmp_path, monkeypatch):
    from finscope_market_data.overnight import joint_worker, validation_worker
    auto, research, now = environment(tmp_path)
    now[0] = now[0].replace(hour=21)
    codes = [f'600{i:03}.SH' for i in range(20)]
    auto.store.put(POOL_KEY, {'members': [{'instrumentCode': code} for code in codes]})
    monkeypatch.setattr(auto.service.store, 'coverage', lambda _: [{'instrumentCode': code, 'completeDays': 140} for code in codes])
    monkeypatch.setattr(joint_worker, 'build_panel', lambda *args: ([], {}, {}))
    monkeypatch.setattr(joint_worker, 'build_direction_panel', lambda *args: ([], {}))
    monkeypatch.setattr(joint_worker, 'fit_target', lambda *args: None)
    entered, release = Event(), Event()
    def blocked_fit(*args):
        entered.set()
        assert release.wait(10)
        return None
    monkeypatch.setattr(joint_worker, 'fit_direction', blocked_fit)
    monkeypatch.setattr(validation_worker, 'POLL_SECONDS', .01)
    async def until(predicate):
        async with asyncio.timeout(3):
            while not predicate():
                await asyncio.sleep(.01)
    async def scenario():
        asyncio.get_running_loop().set_default_executor(ThreadPoolExecutor(max_workers=1))
        stop = asyncio.Event()
        training = asyncio.create_task(OvernightJointWorker(auto, research).run(stop))
        validation = None
        try:
            await until(entered.is_set)
            validation = asyncio.create_task(OvernightValidationWorker(auto, research).run(stop))
            await until(lambda: auto.store.get('validationWorker') is not None
                        and auto.store.get('validationWorker')['status'] == 'IDLE')
            first = auto.store.get('marketForward')['computedAt']
            now[0] += timedelta(minutes=5)
            await until(lambda: auto.store.get('marketForward')['computedAt'] != first
                        and auto.store.get('validationWorker')['status'] == 'IDLE')
            assert not training.done() and not release.is_set()
            assert auto.store.jobs(phase='JOINT')[0]['status'] == 'RUNNING'
            assert all(auto.store.get(key)['computedAt'] == now[0].isoformat() for key in KEYS)
        finally:
            stop.set()
            try:
                if validation:
                    await asyncio.wait_for(validation, timeout=3)
            finally:
                release.set()
                await asyncio.wait_for(training, timeout=3)
    asyncio.run(scenario())
    assert auto.store.jobs(phase='JOINT')[0]['status'] == 'INSUFFICIENT_DATA'
