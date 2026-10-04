import asyncio
from datetime import date, datetime, timedelta
from types import SimpleNamespace
from zoneinfo import ZoneInfo

import pytest

from finscope_market_data.forecast.industry_features import IndustryMembership
from finscope_market_data.overnight.context_features import context_features, stock_context, context_time, build_context_panel
from finscope_market_data.overnight.context_store import OvernightContextStore
from finscope_market_data.overnight.context_capture import OvernightContextCapture
from finscope_market_data.overnight.automation import OvernightAutomation
from finscope_market_data.overnight.engine import group_bars
from finscope_market_data.overnight.store import OvernightStore
from finscope_market_data.overnight.universe import POOL_KEY
from finscope_market_data.providers.tencent import _parse_time
from finscope_market_data.providers.base import ProviderError


def snapshot(day='2026-09-21', cutoff='14:30'):
    observed = context_time(day, cutoff).isoformat()
    received = (context_time(day, cutoff) + timedelta(minutes=1)).isoformat()
    return {'signalDate': day, 'cutoff': cutoff, 'observedAt': observed, 'receivedAt': received,
            'expectedSymbols': 20, 'pool': {f'600{i:03}.SH': {'return': i / 1000, 'intraday': .01,
                'last30': .001, 'observedAt': observed, 'receivedAt': received} for i in range(20)}, 'indices': {}}


def test_context_is_immutable_and_cannot_be_received_after_cutoff(tmp_path):
    store = OvernightContextStore(OvernightStore(tmp_path / 'context.db'))
    value = snapshot()
    first = store.freeze(value)
    value['pool']['600000.SH']['return'] = 100
    assert store.freeze(value) == first
    assert store.at('2026-09-21', '14:30', datetime(2026, 9, 21, 14, 20)) is None
    for stamp in ('2026-09-21T14:31:00', '2026-09-21T15:00:00'):
        with pytest.raises(ValueError):
            store.freeze({**value, 'receivedAt': stamp})
    value['pool']['600000.SH']['observedAt'] = '2026-09-22T14:20:00'
    with pytest.raises(ValueError):
        store.freeze(value)


def test_context_excludes_self_future_memberships_and_missing_coverage():
    value = snapshot()
    own = value['pool']['600000.SH'].copy()
    members = [IndustryMembership('881001', '2026-09-22', ('600000.SH', '600001.SH', '600002.SH'))]
    before = context_features('600000.SH', own, value, members)
    assert before[0] == pytest.approx(.01)
    assert before[-7] == 0
    value['pool']['600000.SH']['return'] = 999
    assert context_features('600000.SH', own, value, members) == before
    members[0] = IndustryMembership('881001', '2026-09-21', members[0].codes)
    assert context_features('600000.SH', own, value, members)[-7] == 1
    value['expectedSymbols'] = 120
    assert context_features('600000.SH', own, value, members) is None


def test_historical_context_never_sees_cutoff_or_after_close_prices():
    from test_overnight import history
    bars = history(12)
    day = bars[-1].ended_at.date()
    before = stock_context(group_bars(bars), day, '14:30')
    for bar in bars:
        if bar.ended_at > datetime.combine(day, datetime.min.time()).replace(hour=14, minute=20):
            bar.close *= 4
    assert stock_context(group_bars(bars), day, '14:30') == before


def test_historical_panel_keeps_missing_context_as_missing():
    from test_overnight import history
    bars = history(12)
    fake = SimpleNamespace(bars=lambda code, through: bars)
    codes = [f'600{i:03}.SH' for i in range(20)]
    rows, audit = build_context_panel(fake, codes, '14:30', bars[-1].ended_at + timedelta(days=1))
    assert rows and len(rows[0]['features']) == 44
    assert audit['indexRows'] == audit['industryRows'] == 0
    assert audit['reconstructedDays'] > 0
    assert not build_context_panel(fake, codes[:19], '14:30', bars[-1].ended_at + timedelta(days=1))[0]


def test_automatic_capture_receives_before_decision_and_does_not_run_after_hours(tmp_path):
    from test_overnight import history
    bars = history(12)
    day = bars[-1].ended_at.date()
    now = [datetime.combine(day, datetime.min.time()).replace(hour=14, minute=20, second=30)]
    calls = []
    async def fetch(code, through):
        calls.append(code)
        return [bar for bar in bars if bar.ended_at <= through]
    async def index(*args, **kwargs):
        return SimpleNamespace(data=SimpleNamespace(observed_at=now[0].replace(tzinfo=ZoneInfo('Asia/Shanghai')),
            price=101, previous_close=100), quality_status='FRESH_PRIMARY', source_code='TENCENT_QUOTE')
    store = OvernightStore(tmp_path / 'auto.db')
    auto = OvernightAutomation(SimpleNamespace(store=store, clock=lambda: now[0],
        provider=SimpleNamespace(fetch_async=fetch, source='TEST')), None)
    auto.store.put(POOL_KEY, {'fingerprint': 'pool', 'members': [{'instrumentCode': f'600{i:03}.SH'} for i in range(20)]})
    task = OvernightContextCapture(auto, SimpleNamespace(fetch=index), tmp_path / 'none.json', tmp_path / 'history.json')
    asyncio.run(task.tick())
    frozen = task.contexts.at(day, '14:30', now[0])
    assert len(frozen['pool']) == 20 and len(frozen['indices']) == 3
    assert len(calls) == 20
    asyncio.run(task.tick())
    now[0] = now[0].replace(hour=18)
    asyncio.run(task.tick())
    assert len(calls) == 20


def test_stale_index_and_missing_timestamp_are_never_fresh_context(tmp_path):
    async def quote(*args, **kwargs):
        return SimpleNamespace(data=SimpleNamespace(observed_at=datetime(2026, 9, 18, 15, tzinfo=ZoneInfo('Asia/Shanghai')),
            price=101, previous_close=100), quality_status='STALE_FALLBACK', source_code='TENCENT_QUOTE')
    now = datetime(2026, 9, 21, 14, 20, 30)
    auto = OvernightAutomation(SimpleNamespace(store=OvernightStore(tmp_path / 'stale.db'), clock=lambda: now), None)
    capture = OvernightContextCapture(auto, SimpleNamespace(fetch=quote), tmp_path / 'none.json', tmp_path / 'h.json')
    result = asyncio.run(capture.collect([], now.replace(second=0), '14:30', now + timedelta(minutes=5)))
    assert not result['indices']
    with pytest.raises(ProviderError):
        _parse_time('')


def test_forward_challenger_must_beat_incumbent_not_only_prior(tmp_path):
    from finscope_market_data.overnight.direction_validation import summarize_direction
    from finscope_market_data.overnight.direction_selection import PROTOCOL
    from finscope_market_data.overnight.direction_dataset import TARGET
    reports = []
    for i in range(60):
        day = date(2026, 1, 1) + timedelta(days=i)
        p = .8 if i % 2 else .2
        reports.append({'id': str(i), 'generatedAt': f'{day}T14:31:00', 'signalDate': str(day),
            'targetDate': str(day + timedelta(days=1)), 'instrumentCode': '600000.SH', 'mode': 'TAIL_ENTRY',
            'cutoff': '14:30', 'evidenceKind': 'FORWARD', 'jointResearch': {'cohort': 'AUTOMATIC'},
            'closeDirection': {'challenger': {'protocol': PROTOCOL, 'target': TARGET, 'artifactId': 'test',
                'upProbability': p, 'incumbentProbability': p, 'baselineProbability': .5}},
            'outcome': {'closeDirection': {'status': 'SETTLED', 'actualUp': p > .5}}})
    result = summarize_direction(SimpleNamespace(iter_history=lambda: iter(reports)), datetime(2026, 4, 1), challenger=True)
    group = result['groups'][0]
    assert group['metrics']['accuracy'] == 1
    assert group['metrics']['comparisons']['INCUMBENT']['accuracyDifference'] == 0
    assert not group['eligible']


def test_adoption_requires_past_forward_approval_and_reverts_without_it(tmp_path, monkeypatch):
    from test_overnight import history, request
    from finscope_market_data.overnight.context_research import attach_context_direction
    from finscope_market_data.overnight.direction_selection import PROTOCOL
    from finscope_market_data.overnight.automation_store import AutomationStore
    bars = history(12)
    day = bars[-1].ended_at.date()
    store = OvernightStore(tmp_path / 'adopt.db')
    contexts, meta = OvernightContextStore(store), AutomationStore(store)
    value = snapshot(str(day))
    value.update(universeFingerprint='pool', memberships=[])
    contexts.freeze(value)
    artifact = {'id': 'model', 'createdAt': f'{day}T12:00:00', 'universeFingerprint': 'pool',
                'contextDirection': {'protocol': PROTOCOL, 'data': {'fingerprint': 'data'}}}
    def forecast(fitted, x, gate):
        assert len(x) == 44
        return {'protocol': PROTOCOL, 'target': 'NEXT_SESSION_CLOSE_VS_SIGNAL_CLOSE', 'upProbability': .7, 'status': 'SHADOW'}
    monkeypatch.setattr('finscope_market_data.overnight.context_research.direction_prediction', forecast)
    def report():
        return {'dataThrough': f'{day}T14:30:00', 'jointResearch': {'cohort': 'AUTOMATIC'},
                'closeDirection': {'protocol': 'overnight-close-direction-v1',
                                   'target': 'NEXT_SESSION_CLOSE_VS_SIGNAL_CLOSE', 'upProbability': .4}}
    gate = {'key': 'TAIL_ENTRY|14:30', 'eligible': True, 'status': 'QUALIFIED', 'dayCount': 60}
    meta.put('contextDirectionForward', {'computedAt': f'{day}T14:31:00', 'groups': [gate]})
    future = report()
    attach_context_direction(future, request(day), group_bars(bars), artifact, contexts, meta)
    assert future['closeDirection']['upProbability'] == .4
    meta.put('contextDirectionForward', {'computedAt': f'{day}T12:00:00', 'groups': [gate]})
    adopted = report()
    attach_context_direction(adopted, request(day), group_bars(bars), artifact, contexts, meta)
    assert adopted['closeDirection']['upProbability'] == .7
    assert adopted['closeDirection']['incumbentPrediction']['upProbability'] == .4
    assert adopted['closeDirection']['challenger']['incumbentProbability'] == .4
    gate.update(eligible=False, status='MONITORING_DEGRADED')
    meta.put('contextDirectionForward', {'computedAt': f'{day}T12:00:00', 'groups': [gate]})
    reverted = report()
    attach_context_direction(reverted, request(day), group_bars(bars), artifact, contexts, meta)
    assert reverted['closeDirection']['upProbability'] == .4
    assert reverted['closeDirection']['challenger']['upProbability'] == .7
