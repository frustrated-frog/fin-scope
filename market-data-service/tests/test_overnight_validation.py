from datetime import datetime, timedelta
from types import SimpleNamespace

import pytest

from finscope_market_data.overnight.capture import OvernightCapture
from finscope_market_data.overnight.models import CapturePlan
from finscope_market_data.overnight.store import OvernightStore
from finscope_market_data.overnight.validation import summarize
from test_overnight import request, history
from finscope_market_data.overnight.engine import predict, settle
from finscope_market_data.overnight.service import OvernightService


def test_capture_records_downtime_without_backfill_and_is_idempotent(tmp_path):
    store = OvernightStore(tmp_path / 'capture.db')
    now = [datetime(2026, 9, 21, 14, 0)]
    calls = []
    def generate(req, freeze_all):
        calls.append(req)
        return {'id': req.instrument_code, 'status': 'INSUFFICIENT_DATA', 'evidenceKind': 'FORWARD',
                'dataThrough': f'{req.signal_date}T{req.cutoff}:00', 'generatedAt': now[0].isoformat(), 'warnings': []}
    service = SimpleNamespace(store=store, clock=lambda: now[0], generate=generate, refresh_outcomes=lambda: [])
    capture = OvernightCapture(service)
    capture.configure(CapturePlan(enabled=True, instrumentCodes=['605058', '605058.SH']))
    now[0] = datetime(2026, 9, 22, 14, 31)
    capture.tick()
    capture.tick()
    assert len(calls) == 1
    assert len(store.runs()) == 3
    assert [r['status'] for r in store.runs()].count('MISSED') == 2
    assert store.runs()[0]['results'][0]['status'] == 'INSUFFICIENT_DATA'
    now[0] = datetime(2026, 9, 22, 14, 46)
    capture.tick()
    assert calls[-1].cutoff == '14:45'


def test_holiday_unknown_calendar_and_disabled_plan_do_not_capture(tmp_path):
    store = OvernightStore(tmp_path / 'capture.db')
    now = [datetime(2026, 9, 25, 12)]
    service = SimpleNamespace(store=store, clock=lambda: now[0], refresh_outcomes=lambda: [])
    capture = OvernightCapture(service)
    capture.configure(CapturePlan(enabled=True, instrumentCodes=['605058']))
    now[0] = datetime(2026, 9, 25, 14, 31)
    capture.tick()
    assert store.runs() == []
    capture.configure(CapturePlan(enabled=False))
    now[0] = datetime(2027, 1, 5, 14, 31)
    capture.tick()
    assert not capture.status()['calendarAvailable']
    assert store.runs() == []


def test_plan_change_preserves_old_universe_for_missed_slots(tmp_path):
    store = OvernightStore(tmp_path / 'capture.db')
    now = [datetime(2026, 9, 21, 14)]
    capture = OvernightCapture(SimpleNamespace(store=store, clock=lambda: now[0]))
    capture.configure(CapturePlan(enabled=True, instrumentCodes=['605058']))
    now[0] = datetime(2026, 9, 22, 12)
    capture.configure(CapturePlan(enabled=True, instrumentCodes=['600001']))
    assert len(store.runs()) == 2
    assert all(r['instrumentCodes'] == ['605058.SH'] for r in store.runs())


def test_all_history_date_weighted_and_frozen_baseline_separated(tmp_path):
    store = OvernightStore(tmp_path / 'audit.db')
    # 60 stocks on one date must not overwhelm a single stock on a second date.
    for index in range(61):
        day = '2026-09-21' if index < 60 else '2026-09-22'
        req = request(day).model_copy(update={'instrument_code': f'600{index:03d}.SH'})
        report = predict(req, [], datetime.fromisoformat(f'{day}T14:31:00'))
        report.update(status='WATCH', targets=[{'target': 'OPEN', 'status': 'WATCH',
            'upProbability': .8, 'baselineProbability': .5}])
        frozen = store.freeze(req, report, [])
        store.outcome(frozen['id'], datetime(2026, 9, 23, 16), {'status': 'SETTLED',
            'targets': [{'target': 'OPEN', 'actualNetReturn': .02 if index < 60 else -.02}]})
    result = summarize(store)
    assert result['recordCount'] == 61
    target = result['groups'][0]['targets'][0]
    assert target['days'] == 2
    assert target['accuracy'] == .5
    assert target['brier'] == pytest.approx(.34)
    assert target['baselineBrier'] == .25
    assert target['meanNetReturn'] == pytest.approx(0)
    assert target['bins'][-1]['actual'] == .5


def test_old_baseline_not_reconstructed_and_retrospective_not_pooled(tmp_path):
    store = OvernightStore(tmp_path / 'audit.db')
    for version, hour in [('overnight-local-v1', 14), ('overnight-local-v2', 16)]:
        req = request('2026-09-21')
        report = predict(req, [], datetime(2026, 9, 21, hour, 31))
        report.update(modelVersion=version, status='WATCH', targets=[{'target': 'OPEN', 'status': 'WATCH', 'upProbability': .4}])
        frozen = store.freeze(req, report, [])
        store.outcome(frozen['id'], datetime(2026, 9, 22, 16), {'status': 'SETTLED',
            'targets': [{'target': 'OPEN', 'actualNetReturn': -.02}]})
    groups = summarize(store)['groups']
    assert len(groups) == 2
    for group in groups:
        target = group['targets'][0]
        assert target['baselineCount'] == 0
        assert target['baselineBrier'] is None
        assert target['selectedNetReturn'] == 0


def test_failed_scheduled_capture_freezes_inputs_and_first_attempt(tmp_path):
    store = OvernightStore(tmp_path / 'fail.db')
    class Provider:
        source = 'TEST'
        def fetch(self, *args):
            raise TimeoutError()
    service = OvernightService(store, Provider(), lambda: datetime(2026, 9, 21, 14, 31))
    report = service.generate(request('2026-09-21'), freeze_all=True)
    assert report['status'] == 'DATA_UNAVAILABLE'
    assert len(store.history()) == 1
    assert summarize(store)['groups'][0]['targets'] == []
    assert summarize(store)['groups'][0]['missingReasons']['PREDICTION:DATA_UNAVAILABLE'] == 1


def test_frozen_prior_and_invalid_exit_are_not_profitable_fills(tmp_path):
    bars = history()
    day = bars[-49].ended_at.date()
    req = request(day)
    report = predict(req, bars, datetime.fromisoformat(f'{day}T14:31:00'))
    assert all(0 <= t['baselineProbability'] <= 1 for t in report['targets'])
    store = OvernightStore(tmp_path / 'prices.db')
    report = store.freeze(req, report, [])
    next_open = bars[-48]
    changed = [b.model_copy(update={'high': b.open, 'low': b.open, 'close': b.open})
               if b == next_open else b for b in bars]
    outcome = settle(report, changed, bars[-1].ended_at)
    assert outcome['status'] == 'PARTIAL'
    assert 'OPEN:EXIT_UNVERIFIED' in outcome['missingReasons']
    assert 'OPEN' not in [t['target'] for t in outcome['targets']]


def test_settlement_rotates_archives_older_than_display_limit(tmp_path):
    store = OvernightStore(tmp_path / 'old.db')
    class Provider:
        source = 'TEST'
        calls = []
        def fetch(self, code, now):
            self.calls.append(code)
            return []
    for index in range(55):
        req = request('2026-09-21').model_copy(update={'instrument_code': f'600{index:03d}.SH'})
        report = predict(req, [], datetime(2026, 9, 21, 14, 31) + timedelta(seconds=index))
        frozen = store.freeze(req, report, [])
        if index > 0:
            store.outcome(frozen['id'], datetime(2026, 9, 22, 16), {'status': 'SETTLED', 'targets': []})
    provider = Provider()
    service = OvernightService(store, provider, lambda: datetime(2026, 9, 23, 16))
    service.refresh_outcomes()
    assert provider.calls == ['600000.SH']


def test_audit_routes_validate_plan_and_persist_across_app_instances(tmp_path):
    from fastapi.testclient import TestClient
    from finscope_market_data.app import create_app
    from finscope_market_data.settings import Settings
    config = Settings(data_dir=tmp_path)
    client = TestClient(create_app(settings=config))
    assert client.get('/v1/quant/overnight/validation').json()['recordCount'] == 0
    assert client.post('/v1/quant/overnight/capture', json={'enabled': True}).status_code == 422
    assert client.post('/v1/quant/overnight/capture', json={
        'enabled': True, 'instrumentCodes': ['605058'], 'costBps': 20}).status_code == 200
    restarted = TestClient(create_app(settings=config))
    state = restarted.get('/v1/quant/overnight/capture').json()
    assert state['plan']['instrumentCodes'] == ['605058.SH']
    assert state['slots'] == ['14:30', '14:45']
    assert restarted.post('/v1/quant/overnight/capture', json={
        'enabled': True, 'instrumentCodes': ['INVALID']}).status_code == 422
    assert restarted.get('/v1/quant/overnight/capture').json()['plan']['instrumentCodes'] == ['605058.SH']
