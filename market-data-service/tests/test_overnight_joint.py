from copy import deepcopy
from datetime import date, datetime, timedelta
import gzip
import json
from types import SimpleNamespace

import numpy as np
import pytest

from finscope_market_data.overnight.automation import rank_candidates
from finscope_market_data.overnight.automation_store import AutomationStore
from finscope_market_data.overnight.joint_dataset import (FEATURES, PROTOCOL, JointProfile,
    build_panel, current_features, date_weights, split_dates)
from finscope_market_data.overnight.joint_learning import fit_before, fit_target, prediction
from finscope_market_data.overnight.joint_research import OvernightJointResearch
from finscope_market_data.overnight.joint_store import OvernightJointStore
from finscope_market_data.overnight.joint_validation import assess, summarize_forward
from finscope_market_data.overnight.store import OvernightStore


@pytest.fixture(scope='module')
def panel_rows():
    rng = np.random.default_rng(42)
    rows = []
    for day in range(150):
        signal = date(2026, 1, 1) + timedelta(days=day)
        common = rng.normal(0, .003)
        for stock in range(20):
            x = rng.normal(size=len(FEATURES))
            rows.append({'instrumentCode': f'600{stock:03}.SH', 'signalDate': str(signal),
                         'exitAt': f'{signal + timedelta(days=1)}T10:00:00', 'features': x.tolist(),
                         'actualNetReturn': float(.005 * x[0] + common + rng.normal(0, .01))})
    return rows


@pytest.fixture(scope='module')
def fitted(panel_rows):
    return fit_target(panel_rows, '2026-06-01T14:30:00')


def test_dates_are_purged_and_each_day_has_equal_total_weight(panel_rows):
    training, calibration, test = split_dates(panel_rows, '2026-06-01T14:30:00')
    assert max(row['exitAt'][:10] for row in training) < min(row['signalDate'] for row in calibration)
    assert max(row['exitAt'][:10] for row in calibration) < min(row['signalDate'] for row in test)
    assert len({row['signalDate'] for row in calibration}) == 20
    assert len({row['signalDate'] for row in test}) == 20
    assert split_dates(panel_rows[:121 * 20], '2026-06-01T14:30:00') is None
    rows = panel_rows[:21] + panel_rows[:20] * 10
    weights = date_weights(rows)
    for day in {row['signalDate'] for row in rows}:
        assert sum(weight for weight, row in zip(weights, rows) if row['signalDate'] == day) == pytest.approx(1)


def test_future_test_outcomes_never_select_coefficients_or_calibration(panel_rows, fitted):
    changed = deepcopy(panel_rows)
    for row in changed:
        if row['signalDate'] >= fitted['audit']['testStart']:
            row['actualNetReturn'] = -row['actualNetReturn'] * 2
    cutoff = fitted['audit']['testStart'] + 'T14:30:00'
    assert fit_before(changed, cutoff) == fit_before(panel_rows, cutoff)
    assert fitted['audit']['calibrationThrough'] == max(row['exitAt'] for row in panel_rows)
    for fold in fitted['audit']['folds']:
        assert fold['trainingThrough'][:10] < fold['calibrationStart']
        assert fold['calibrationThrough'] < fold['cutoff']
    assert not fitted['audit']['historical']['eligible']
    result = prediction(json.loads(json.dumps(fitted['model'])), panel_rows[0]['features'])
    assert 0 < result['upProbability'] < 1
    assert 0 < result['downsideProbability'] < 1
    assert result['lowerNetReturn'] <= result['upperNetReturn']
    assert fitted['audit']['calibrationDays'] == 20


def test_panel_uses_previous_pool_context_and_excludes_future_features():
    from test_overnight import history
    from finscope_market_data.overnight.engine import group_bars
    from finscope_market_data.forecast.trading_calendar import previous_session
    bars = history(10)
    day = bars[-1].ended_at.date()
    through = datetime.fromisoformat(f'{day}T14:30:00')
    store = SimpleNamespace(bars=lambda code, through: [bar for bar in bars if bar.ended_at <= through])
    codes = [f'600{stock:03}.SH' for stock in range(20)]
    rows, context, audit = build_panel(store, codes, JointProfile('TAIL_ENTRY', '14:30', 20), through)
    x = current_features(group_bars(bars), day, '14:30', codes[0], context)
    assert x is not None and len(x) == len(FEATURES)
    assert str(day) not in context and str(previous_session(day)) in context
    assert rows and audit['symbolCount'] == 20
    assert all(row['exitAt'] < through.isoformat() for row in rows)
    for bar in bars:
        if bar.ended_at > through:
            bar.close *= 10
    assert current_features(group_bars(bars), day, '14:30', codes[0], context) == x
    assert current_features(group_bars(bars), day, '14:30', codes[0], {}) is None


def test_artifact_is_atomic_immutable_and_unavailable_before_creation(tmp_path, fitted, panel_rows):
    store = OvernightStore(tmp_path / 'models.db')
    models = OvernightJointStore(store)
    artifact = {'protocol': PROTOCOL, 'profile': JointProfile('TAIL_ENTRY', '14:30', 20).dump(),
                'createdAt': '2026-06-01T12:00:00', 'labelsThrough': '2026-05-31T15:00:00',
                'targets': {'10:00': fitted}, 'context': {}, 'data': {'fingerprint': 'input'}}
    first = models.publish(artifact, panel_rows[:2])
    assert models.publish(artifact, panel_rows[:2]) == first
    assert models.latest(artifact['profile']['key'], datetime(2026, 6, 1, 11)) is None
    assert models.latest(artifact['profile']['key'], datetime(2026, 6, 1, 14)) == first
    assert models.latest(artifact['profile']['key'], datetime(2026, 6, 20, 14)) is None
    with store.connect() as db:
        stored = db.execute('SELECT panel FROM overnight_joint_artifact').fetchone()[0]
    assert json.loads(gzip.decompress(stored)) == panel_rows[:2]


def forward_rows(days=60, inverse=False):
    rows = []
    for day in range(days):
        up = day % 2 == 0
        rows.append({'signalDate': str(date(2026, 1, 1) + timedelta(days=day)), 'instrumentCode': '605058.SH',
            'upProbability': (.2 if up else .8) if inverse else (.8 if up else .2),
            'actualNetReturn': .02 if up else -.02, 'incumbentProbability': .5, 'baselineProbability': .5,
            'qualified': up, 'rankScore': .01, 'calibrationStatus': 'FITTED'})
    return rows


def test_forward_requires_independent_days_and_may_abstain_or_degrade():
    early = forward_rows(2) * 300
    assert assess(early, len(early))['status'] == 'ACCUMULATING'
    assert assess(forward_rows(), 60)['status'] == 'QUALIFIED'
    none = [{**row, 'qualified': False} for row in forward_rows()]
    assert assess(none, 60)['status'] == 'CHECKPOINT_FAILED'
    assert assess(forward_rows(), 90)['status'] == 'MONITORING_DEGRADED'
    failed = assess(forward_rows(inverse=True), 60)
    assert failed['status'] == 'CHECKPOINT_FAILED'
    assert assess(forward_rows(120), 120, checkpoint=failed)['status'] == 'CHECKPOINT_FAILED'


def test_ranker_keeps_shadow_separate_and_allows_zero_qualified_opportunities():
    joint = {'status': 'AVAILABLE', 'adopted': False, 'qualified': True, 'rankScore': .01}
    rows = [{'instrumentCode': '605058.SH', 'reportId': 'one', 'evidenceKind': 'FORWARD', 'joint': joint}]
    assert rank_candidates(rows)['status'] == 'SHADOW'
    joint.update(adopted=True, qualified=False)
    assert rank_candidates(rows)['opportunityStatus'] == 'NO_QUALIFIED'
    joint['qualified'] = True
    rows[0]['evidenceKind'] = 'RETROSPECTIVE'
    assert rank_candidates(rows)['evaluatedCount'] == 0


def test_insufficient_tail_risk_calibration_cannot_qualify_a_stock(monkeypatch):
    monkeypatch.setattr('finscope_market_data.overnight.joint_learning.predict_many',
                        lambda *args: (np.array([.8]), np.array([.01]), np.array([.1])))
    model = {'residualLow': -.02, 'residualHigh': .02, 'baselineProbability': .5,
             'calibration': {'status': 'FITTED'}, 'downsideCalibration': {'status': 'UNAVAILABLE'}}
    assert not prediction(model, [0])['qualified']
    model['downsideCalibration']['status'] = 'FITTED'
    assert prediction(model, [0])['qualified']


def test_frozen_shadow_adopts_only_a_past_gate_and_does_not_refit(tmp_path, fitted):
    from test_overnight import history, request
    from finscope_market_data.forecast.trading_calendar import previous_session
    bars = history(145)
    day = bars[-1].ended_at.date()
    cutoff = datetime.fromisoformat(f'{day}T14:30:00')
    previous = previous_session(day)
    store = OvernightStore(tmp_path / 'live.db')
    meta = AutomationStore(store)
    research = OvernightJointResearch(store, meta)
    req = request(day)
    profile = JointProfile('TAIL_ENTRY', '14:30', 20)
    artifact = research.models.publish({'protocol': PROTOCOL, 'profile': profile.dump(),
        'createdAt': f'{day}T12:00:00', 'labelsThrough': f'{previous}T15:00:00',
        'context': {str(previous): {'mean': .01, 'upShare': .6, 'dispersion': .02}},
        'data': {'fingerprint': 'test', 'symbolCount': 20}, 'targets': {'10:00': fitted}}, [])
    report = {'dataThrough': cutoff.isoformat(), 'referencePrice': 20, 'jointResearch': {'cohort': 'AUTOMATIC'}, 'targets': [
        {'target': '10:00', 'status': 'WATCH', 'upProbability': .4, 'expectedNetReturn': -.001}]}
    shadow = deepcopy(report)
    research.attach(shadow, req, bars)
    assert shadow['targets'][0]['upProbability'] == .4
    assert shadow['targets'][0]['joint']['artifactId'] == artifact['id']
    assert not shadow['targets'][0]['joint']['adopted']
    gate = {'key': f'{profile.key}|10:00', 'eligible': True, 'dayCount': 60, 'status': 'QUALIFIED'}
    meta.put('jointForward', {'computedAt': f'{day}T15:00:00', 'groups': [gate]})
    research.attach(deepcopy(report), req, bars)
    late = deepcopy(report)
    research.attach(late, req, bars)
    assert not late['targets'][0]['joint']['adopted']
    meta.put('jointForward', {'computedAt': f'{day}T12:00:00', 'groups': [gate]})
    promoted = deepcopy(report)
    research.attach(promoted, req, bars)
    assert promoted['targets'][0]['probabilitySource'] == 'JOINT_MODEL'
    assert promoted['targets'][0]['joint']['incumbentProbability'] == .4


def test_duplicate_holdings_revisions_and_retrospective_runs_never_inflate_forward_days(tmp_path):
    from finscope_market_data.overnight.engine import TARGETS
    store = OvernightStore(tmp_path / 'forward.db')
    meta = AutomationStore(store)
    rows = forward_rows()
    reports = []
    for index, row in enumerate(rows):
        signal = date.fromisoformat(row['signalDate'])
        report = {'id': str(index), 'generatedAt': f'{signal}T14:31:00', 'signalDate': str(signal),
            'targetDate': str(signal + timedelta(days=1)), 'instrumentCode': row['instrumentCode'],
            'mode': 'TAIL_ENTRY', 'cutoff': '14:30', 'costBps': 20, 'evidenceKind': 'FORWARD',
            'jointResearch': {'protocol': PROTOCOL, 'artifactId': 'one', 'cohort': 'AUTOMATIC'},
            'targets': [{'target': target, 'joint': {**row, 'status': 'AVAILABLE'}} for target in TARGETS],
            'outcome': {'targets': [{'target': target, 'actualNetReturn': row['actualNetReturn']} for target in TARGETS]}}
        reports.extend([report, {**report, 'id': f'{index}-revision'},
            {**report, 'id': f'{index}-custom', 'instrumentCode': '600000.SH',
             'jointResearch': {**report['jointResearch'], 'cohort': 'CUSTOM'}},
            {**report, 'id': f'{index}-retro', 'evidenceKind': 'RETROSPECTIVE'}])
    fake_store = SimpleNamespace(iter_history=lambda: iter(reports))
    summary = summarize_forward(fake_store, datetime(2026, 4, 1), meta)
    assert len(summary['groups']) == 4
    assert all(group['pairedCount'] == 60 and group['dayCount'] == 60 for group in summary['groups'])
    checkpoint = summary['groups'][0]['checkpointDays']
    # An older day's late result must not move a fixed checkpoint's boundary.
    old = deepcopy(reports[0])
    old.update(id='older', signalDate='2025-12-31', generatedAt='2025-12-31T14:31:00', targetDate='2026-01-01')
    reports.append(old)
    second = summarize_forward(fake_store, datetime(2026, 4, 2), meta)
    assert second['groups'][0]['checkpointDays'] == checkpoint


def test_old_worker_cannot_publish_after_lease_is_reclaimed(tmp_path, fitted):
    store = OvernightStore(tmp_path / 'lease.db')
    meta = AutomationStore(store)
    models = OvernightJointStore(store)
    now = datetime(2026, 6, 1, 10)
    old = meta.claim('train', now, {'phase': 'JOINT'})
    newer = meta.claim('train', now + timedelta(minutes=11), {'phase': 'JOINT'})
    artifact = {'protocol': PROTOCOL, 'profile': JointProfile('TAIL_ENTRY', '14:30', 20).dump(),
                'createdAt': '2026-06-01T12:00:00', 'labelsThrough': '2026-05-31T15:00:00',
                'targets': {'10:00': fitted}, 'context': {}, 'data': {}, 'trainingJobKey': 'train'}
    with pytest.raises(ValueError, match='租约'):
        models.publish(artifact, [], claim=old)
    assert not models.for_job('train')
    result = models.publish(artifact, [], claim=newer)
    assert models.for_job('train') == result['id']


def test_joint_failure_cannot_leave_half_promoted_report(tmp_path, monkeypatch):
    from finscope_market_data.overnight.service import OvernightService
    from test_overnight import request
    original = {'targets': [{'upProbability': .4}], 'generatedAt': '2026-09-21T14:31:00',
        'status': 'INSUFFICIENT_DATA', 'warnings': []}
    monkeypatch.setattr('finscope_market_data.overnight.service.predict', lambda *args: deepcopy(original))
    def fail(report, *args):
        report['targets'][0]['upProbability'] = .9
        raise ValueError('invalid model')
    service = OvernightService(OvernightStore(tmp_path / 'fallback.db'),
        SimpleNamespace(fetch=lambda *args: [], source='TEST'), clock=lambda: datetime(2026, 9, 21, 14, 31),
        joint=SimpleNamespace(attach=fail))
    report = service.generate(request(date(2026, 9, 21)), settle_cached=False)
    assert report['targets'][0]['upProbability'] == .4
    assert report['jointResearch']['status'] == 'FAILED'


def test_shared_worker_recovers_committed_artifact_and_respects_critical_window(tmp_path, monkeypatch):
    from finscope_market_data.overnight.automation import OvernightAutomation
    from finscope_market_data.overnight.joint_worker import OvernightJointWorker
    store = OvernightStore(tmp_path / 'worker.db')
    now = [datetime(2026, 9, 21, 14, 30)]
    automation = OvernightAutomation(SimpleNamespace(store=store, clock=lambda: now[0]), None)
    research = OvernightJointResearch(store, automation.store)
    worker = OvernightJointWorker(automation, research)
    job = automation.store.claim('old', now[0] - timedelta(minutes=15), {'phase': 'JOINT'})
    monkeypatch.setattr(research.models, 'for_job', lambda key: 'saved-artifact')
    worker.tick()
    assert automation.store.job(job['key'])['status'] == 'RUNNING'
    now[0] = now[0].replace(hour=18)
    worker.tick()
    assert automation.store.job(job['key'])['status'] == 'COMPLETED'
    assert automation.store.get('jointCoverage')['readySymbols'] == 0
