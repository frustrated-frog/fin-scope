from datetime import datetime, timedelta
from types import SimpleNamespace
from zoneinfo import ZoneInfo

import pytest
from fastapi.testclient import TestClient

from finscope_market_data.overnight.automation import OvernightAutomation
from finscope_market_data.overnight.automation_models import AutomationContext
from finscope_market_data.overnight.scanner import OvernightCandidateScanner
from finscope_market_data.overnight.store import OvernightStore


@pytest.fixture
def flow(tmp_path):
    now = [datetime(2026, 9, 21, 14, 20)]
    calls = []
    scans = []
    def generate(request, freeze_all, settle_cached):
        assert not settle_cached
        calls.append(request)
        return {'id': request.instrument_code, 'status': 'INSUFFICIENT_DATA',
                'evidenceKind': 'FORWARD', 'warnings': ['样本不足']}
    def scan(at, limit):
        scans.append(at)
        return {'candidates': [{'instrumentCode': '605058.SH', 'instrumentName': '澳弘电子'}]}
    service = SimpleNamespace(store=OvernightStore(tmp_path / 'auto.db'), clock=lambda: now[0], generate=generate)
    auto = OvernightAutomation(service, SimpleNamespace(scan=scan))
    return auto, now, calls, scans


def test_discovers_without_a_manual_list_and_freezes_at_each_cutoff(flow):
    auto, now, calls, scans = flow
    auto.tick()
    assert len(scans) == 1 and not calls
    now[0] = now[0].replace(minute=30)
    auto.tick()
    auto.tick()
    assert len(calls) == 1 and calls[0].cutoff == '14:30'
    now[0] = now[0].replace(minute=40)
    auto.tick()
    now[0] = now[0].replace(minute=45)
    auto.tick()
    assert [call.cutoff for call in calls] == ['14:30', '14:45']
    assert all(call.mode == 'TAIL_ENTRY' for call in calls)
    assert auto.store.job('2026-09-21|TAIL|14:30')['results'][0]['status'] == 'INSUFFICIENT_DATA'


def test_restart_retains_claims_and_records_downtime_without_late_predictions(flow):
    auto, now, calls, scans = flow
    auto.tick()
    now[0] = datetime(2026, 9, 22, 16)
    restarted = OvernightAutomation(auto.service, auto.scanner)
    restarted.tick()
    assert not calls and len(scans) == 1
    assert len([j for j in restarted.store.jobs() if j['status'] == 'MISSED']) == 4
    restarted.tick()
    assert len(restarted.store.jobs()) == 5


def test_starting_at_cutoff_does_not_use_a_later_market_scan(flow):
    auto, now, calls, scans = flow
    now[0] = now[0].replace(minute=31)
    auto.tick()
    assert not scans and not calls
    assert auto.store.job('2026-09-21|TAIL|14:30')['status'] == 'MISSED'
    assert auto.status()['nextTailAt'] == '2026-09-21T14:45:00'


def test_holiday_status_names_the_next_real_session(flow):
    auto, now, _, _ = flow
    now[0] = datetime(2026, 10, 4, 16)
    state = auto.status()
    assert state['calendarAvailable'] and not state['tradingDay']
    assert state['nextTailAt'] == '2026-10-08T14:30:00'


def test_slow_scan_cannot_be_promoted_to_a_predecision_snapshot(flow):
    auto, now, calls, _ = flow
    def scan(at, limit):
        now[0] = now[0].replace(minute=31)
        return {'candidates': [{'instrumentCode': '605058.SH'}]}
    auto.scanner.scan = scan
    auto.tick()
    auto.tick()
    assert not calls
    assert auto.store.job('2026-09-21|SCAN|14:30')['status'] == 'MISSED'


@pytest.mark.parametrize('at', [datetime(2026, 10, 5, 14, 20), datetime(2027, 1, 5, 14, 20)])
def test_holidays_and_unverified_calendar_never_scan(flow, at):
    auto, now, calls, scans = flow
    now[0] = at
    auto.tick()
    assert not calls and not scans and not auto.store.jobs()


def test_disabled_automation_does_not_scan(flow):
    auto, _, calls, scans = flow
    auto.sync_context(AutomationContext(enabled=False))
    auto.tick()
    assert not calls and not scans


def context(**kwargs):
    return AutomationContext(positions=[{'instrumentCode': '605058.SH', 'quantity': 100,
        'averageCost': 20, 'openedOn': '2026-09-21', **kwargs}])


def test_after_close_requires_fresh_real_ledger_and_keeps_cost_and_idempotency(flow):
    auto, now, calls, _ = flow
    auto.sync_context(context())
    now[0] = datetime(2026, 9, 21, 15, 10)
    auto.tick()
    assert not calls and auto.status()['holdingStatus'] == 'WAITING_LEDGER'
    auto.sync_context(context())
    auto.tick()
    auto.tick()
    assert len(calls) == 1
    assert calls[0].mode == 'AFTER_CLOSE_HOLDING' and calls[0].cutoff == '15:00'
    assert calls[0].cost_basis == 20 and calls[0].quantity == 100
    assert calls[0].cost_bps == 10
    auto.sync_context(context(quantity=200))
    auto.tick()
    assert len(calls) == 2 and calls[-1].quantity == 200


def test_invalid_position_does_not_block_other_positions_or_fabricate_cost(flow):
    auto, now, calls, _ = flow
    now[0] = datetime(2026, 9, 21, 15, 10)
    auto.sync_context(AutomationContext(positions=[
        {'instrumentCode': '605058.SH', 'quantity': 100, 'averageCost': 0, 'openedOn': '2026-09-21'},
        {'instrumentCode': '600001.SH', 'quantity': 100, 'averageCost': 15, 'openedOn': '2026-09-21'}]))
    auto.tick()
    assert [r.instrument_code for r in calls] == ['600001.SH']
    assert any(j['status'] == 'SKIPPED' for j in auto.store.jobs())


def test_job_lease_prevents_duplicate_execution_and_late_worker_overwrite(flow):
    auto, now, _, _ = flow
    first = auto.store.claim('one', now[0], {})
    assert auto.store.claim('one', now[0], {}) is None
    now[0] += timedelta(minutes=11)
    second = auto.store.claim('one', now[0], {})
    auto.store.finish(second, now[0], status='COMPLETED')
    auto.store.finish(first, now[0], status='FAILED')
    assert auto.store.job('one')['status'] == 'COMPLETED'
    assert auto.store.claim('one', now[0], {}) is None


def test_failed_scan_retries_with_a_bounded_budget(flow):
    auto, now, calls, _ = flow
    scans = []
    def fail(*args):
        scans.append(1)
        raise ValueError('upstream unavailable')
    auto.scanner.scan = fail
    for minute in (20, 20, 21, 22, 23):
        now[0] = now[0].replace(minute=minute)
        auto.tick()
    assert len(scans) == 3 and not calls
    assert auto.store.job('2026-09-21|SCAN|14:30')['status'] == 'FAILED'


def test_one_failed_stock_does_not_block_the_remaining_candidates(flow):
    auto, now, _, _ = flow
    auto.scanner.scan = lambda at, limit: {'candidates': [{'instrumentCode': '605058.SH'}, {'instrumentCode': '600001.SH'}]}
    generate = auto.service.generate
    def isolated(request, **kwargs):
        if request.instrument_code == '605058.SH':
            raise ValueError('bad upstream')
        return generate(request, **kwargs)
    auto.service.generate = isolated
    auto.tick()
    now[0] = now[0].replace(minute=30)
    auto.tick()
    job = auto.store.job('2026-09-21|TAIL|14:30')
    assert job['status'] == 'PARTIAL'
    assert [r['status'] for r in job['results']] == ['FAILED', 'INSUFFICIENT_DATA']


def quote(code='605058', **kwargs):
    return {'f12': code, 'f14': '澳弘电子', 'f2': 23, 'f3': 5, 'f6': 200_000_000,
            'f10': 2, 'f15': 23.2, 'f16': 20, 'f17': 21,
            'f124': datetime(2026, 9, 21, 14, 20, tzinfo=ZoneInfo('Asia/Shanghai')).timestamp(), **kwargs}


def test_candidate_filter_rejects_stale_halted_limit_up_and_unsupported_rows():
    now = datetime(2026, 9, 21, 14, 20)
    rows = [quote(), quote('600001', f3=9.8), quote('600002', f6=0), quote('600003', f14='ST测试'),
            quote('688001'), quote('600004', f124=1), quote('600005', f124=None),
            quote('600006', f124=quote()['f124'] + 5)]
    result = OvernightCandidateScanner.select(rows, now, 6)
    assert [r['instrumentCode'] for r in result['candidates']] == ['605058.SH']
    with pytest.raises(ValueError, match='时间戳'):
        OvernightCandidateScanner.select([quote(f124=1)], now, 6)


def test_scan_retains_losers_and_capacity_rejections_for_cohort_audits():
    result = OvernightCandidateScanner.select([quote(), quote('600001'), quote('600002', f3=-2)],
        datetime(2026, 9, 21, 14, 20), 1)
    observed = {row['instrumentCode']: row for row in result['observations']}
    assert len(observed) == 3
    assert sum(row['selected'] for row in observed.values()) == 1
    assert observed['600002.SH']['rejectionReasons'] == ['CHANGE_OUTSIDE_RULE']
    assert observed['605058.SH']['rejectionReasons'] == ['ACQUISITION_LIMIT']


def test_automation_routes_are_read_only_until_worker_runs(tmp_path):
    from finscope_market_data.app import create_app
    from finscope_market_data.settings import Settings
    client = TestClient(create_app(settings=Settings(data_dir=tmp_path)))
    initial = client.get('/v1/quant/overnight/automation').json()
    assert initial['enabled'] and initial['jobs'] == []
    assert not initial['ledgerFresh']
    response = client.post('/v1/quant/overnight/automation/context', json=context().model_dump(mode='json', by_alias=True))
    assert response.status_code == 200
    assert client.get('/v1/quant/overnight/automation').json()['positionCount'] == 1
    assert client.get('/v1/calendar/next-session?after=2026-09-30').json() == {'next_session': '2026-10-08'}
    assert client.get('/v1/calendar/next-session?after=2027-01-01').status_code == 503
