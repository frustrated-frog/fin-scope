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


def test_minute_aggregation_differences_are_audited_and_sources_never_mix_within_a_day(tmp_path):
    store = OvernightStore(tmp_path / 'sources.db')
    now = datetime(2026, 9, 23, 16)
    raw = bar('2026-09-22T09:35:00')
    store.save_bars('605058.SH', [raw])
    different = raw.model_copy(update={'high': 10.8, 'close': 10.1})
    older = bar('2026-09-21T09:35:00')
    result = store.import_history('605058.SH', [older, different, bar('2026-09-22T09:40:00')], 'TEST', now)
    assert result['minuteDifferenceCount'] == 1 and result['verifiedPriceAnchors'] == 1
    assert store.bars('605058.SH', now) == [older, raw]
    # Once live data arrives for an older day, choose that entire source/day.
    live = bar('2026-09-21T09:40:00')
    store.save_bars('605058.SH', [live])
    assert store.bars('605058.SH', now) == [live, raw]
    assert store.coverage(now)[0]['barCount'] == 2


def test_daily_amount_unit_conflicts_reject_the_entire_history_import(tmp_path):
    from finscope_market_data.overnight.engine import expected_times
    store = OvernightStore(tmp_path / 'amount.db')
    now = datetime(2026, 9, 23, 16)
    bars = [bar(f'2026-09-22T{stamp}:00') for stamp in expected_times('15:00')]
    store.save_bars('605058.SH', bars)
    wrong_units = [value.model_copy(update={'amount': value.amount * 100}) for value in bars]
    with pytest.raises(ValueError, match='全天成交额口径冲突'):
        store.import_history('605058.SH', [bar('2026-09-21T09:35:00'), *wrong_units], 'TEST', now)
    assert store.bars('605058.SH', now) == bars
    assert store.history_import('605058.SH') is None


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


def test_restart_recovers_a_committed_import_even_when_coverage_would_skip_the_stock(flow):
    from test_overnight import history
    task, auto, now, calls = flow
    key = f'{now[0].date()}|HISTORY|605058.SH'
    auto.store.claim(key, now[0] - timedelta(minutes=11), {'phase': 'HISTORY', 'instrumentCode': '605058.SH'})
    task.minutes.import_history('605058.SH', history(145), 'TEST', now[0] - timedelta(minutes=10))
    task.tick()
    assert not calls
    assert auto.store.job(key)['status'] == 'COMPLETED'
    assert auto.store.job(key)['reason'] == '任务中断后已核实历史数据落库'
