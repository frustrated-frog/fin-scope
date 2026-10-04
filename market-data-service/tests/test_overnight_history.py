from datetime import datetime, timedelta
import json
from types import SimpleNamespace

import pytest

from finscope_market_data.overnight.automation import OvernightAutomation
from finscope_market_data.overnight.automation_models import AutomationContext
from finscope_market_data.overnight.history_backfill import OvernightHistoryBackfill
from finscope_market_data.overnight.models import MinuteBar
from finscope_market_data.overnight.store import OvernightStore
from finscope_market_data.providers.baostock_minutes import BaostockMinuteHistoryProvider, FIELDS
from finscope_market_data.providers.base import ProviderError


def bar(stamp, price=10):
    return MinuteBar(ended_at=datetime.fromisoformat(stamp), open=price, high=price + 1,
                     low=price - 1, close=price, amount=10000)


def test_sdk_contract_enforces_raw_identity_cutoff_and_conflicts():
    row = ['2026-09-21', '20260921093500000', 'sh.605058', '10', '11', '9', '10', '1000', '10000', '3']
    def payload(rows):
        return {'fields': list(FIELDS), 'rows': rows}
    through = datetime(2026, 9, 21, 15)
    assert BaostockMinuteHistoryProvider.parse(payload([row, row]), '605058.SH', through) == [bar('2026-09-21T09:35:00')]
    for column, value in [(2, 'sh.600000'), (9, '2'), (0, '2026-09-20'), (1, '20260921120000000')]:
        invalid = row.copy()
        invalid[column] = value
        with pytest.raises(ValueError):
            BaostockMinuteHistoryProvider.parse(payload([invalid]), '605058.SH', through)
    with pytest.raises(ValueError):
        BaostockMinuteHistoryProvider.parse(payload([row]), '605058.SH', through - timedelta(days=1))
    changed = row.copy()
    changed[6] = '10.5'
    with pytest.raises(ValueError, match='冲突'):
        BaostockMinuteHistoryProvider.parse(payload([row, changed]), '605058.SH', through)
    placeholder = row[:3] + ['0'] * 6 + ['3']
    assert BaostockMinuteHistoryProvider.parse(payload([row, placeholder]), '605058.SH', through) == [bar('2026-09-21T09:35:00')]
    placeholder[8] = '100'
    with pytest.raises(ValueError):
        BaostockMinuteHistoryProvider.parse(payload([placeholder]), '605058.SH', through)


def test_sdk_timeout_and_invalid_output_do_not_escape_as_success(monkeypatch):
    import subprocess
    def timeout(*args, **kwargs):
        assert kwargs['timeout'] == 45 and kwargs['capture_output']
        raise subprocess.TimeoutExpired(args[0], 45)
    monkeypatch.setattr(subprocess, 'run', timeout)
    with pytest.raises(ProviderError, match='超时'):
        BaostockMinuteHistoryProvider().fetch('605058.SH', datetime(2026, 9, 21, 15))
    monkeypatch.setattr(subprocess, 'run', lambda *a, **k: SimpleNamespace(returncode=0, stdout='not json'))
    with pytest.raises(ProviderError, match='无效'):
        BaostockMinuteHistoryProvider().fetch('605058.SH', datetime(2026, 9, 21, 15))


def test_import_is_atomic_keeps_captured_days_and_never_changes_frozen_records(tmp_path):
    store = OvernightStore(tmp_path / 'history.db')
    now = datetime(2026, 9, 23, 16)
    captured = bar('2026-09-22T09:35:00')
    store.save_bars('605058.SH', [captured])
    with store.connect() as db:
        db.execute('INSERT INTO overnight_prediction VALUES(?,?,?,?,?)', ('old', 'old', str(now), json.dumps({'status': 'OLD'}), '[]'))
    previous = bar('2026-09-21T09:35:00')
    with pytest.raises(ValueError, match='价格冲突'):
        store.import_history('605058.SH', [previous, bar('2026-09-22T09:35:00', 20)], 'TEST', now)
    assert store.bars('605058.SH', now) == [captured]
    report = store.import_history('605058.SH', [previous, captured, bar('2026-09-22T09:40:00')], 'TEST', now)
    assert report['addedBars'] == 1 and report['overlapBars'] == 1
    assert store.bars('605058.SH', now) == [previous, captured]
    assert store.import_history('605058.SH', [previous, captured], 'TEST', now)['addedBars'] == 0
    assert store.history()[0]['status'] == 'OLD'


@pytest.fixture
def flow(tmp_path):
    now = [datetime(2026, 9, 23, 13)]
    store = OvernightStore(tmp_path / 'auto.db')
    auto = OvernightAutomation(SimpleNamespace(store=store, clock=lambda: now[0]), None)
    auto.sync_context(AutomationContext(positions=[{'instrumentCode': '605058.SH', 'quantity': 100,
        'averageCost': 10, 'openedOn': '2026-09-22'}]))
    calls = []
    def fetch(code, through):
        calls.append((code, through))
        return [bar('2026-09-22T09:35:00')]
    task = OvernightHistoryBackfill(auto, SimpleNamespace(source='TEST', fetch=fetch))
    return task, auto, now, calls


def test_automatic_history_uses_ledger_candidates_and_cache_without_manual_action(flow):
    task, auto, now, calls = flow
    job = auto.store.claim('2026-09-23|SCAN|14:30', now[0], {'phase': 'DISCOVER'})
    auto.store.finish(job, now[0], status='COMPLETED', candidates=[{'instrumentCode': '600000.SH'}])
    auto.service.store.save_bars('000001.SZ', [bar('2026-09-22T09:35:00')])
    for _ in range(5):
        task.tick()
    assert [call[0] for call in calls] == ['605058.SH', '600000.SH', '000001.SZ']
    assert all(call[1] == datetime(2026, 9, 22, 15) for call in calls)
    assert len(auto.status()['history']['jobs']) == 3
    assert all(j['phase'] != 'HISTORY' for j in auto.status()['jobs'])
    restarted = OvernightHistoryBackfill(auto, task.provider)
    restarted.tick()
    assert len(calls) == 3


def test_history_respects_pause_windows_disable_and_bounded_retries(flow):
    task, auto, now, calls = flow
    now[0] = now[0].replace(hour=14, minute=24)
    task.tick()
    assert not calls
    now[0] = now[0].replace(hour=16)
    auto.sync_context(AutomationContext(enabled=False))
    task.tick()
    assert not calls
    auto.service.store.save_bars('000001.SZ', [bar('2026-09-22T09:35:00')])
    auto.sync_context(AutomationContext())
    def fail(*args):
        calls.append(args)
        raise ProviderError('TIMEOUT', 'private upstream text')
    task.provider.fetch = fail
    for _ in range(8):
        task.tick()
        now[0] += timedelta(seconds=61)
    assert len(calls) == 3
    assert auto.status()['history']['jobs'][0]['reason'] == '历史分钟源暂不可用；后台将有限重试'
