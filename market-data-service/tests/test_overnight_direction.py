from copy import deepcopy
from datetime import date, datetime, timedelta
import json
from types import SimpleNamespace

import numpy as np
import pytest

from finscope_market_data.forecast.trading_calendar import next_session
from finscope_market_data.overnight.direction_dataset import FEATURES, build_direction_panel, close_outcome, direction_features
from finscope_market_data.overnight.direction_learning import fit_direction, fit_direction_before, direction_prediction
from finscope_market_data.overnight.engine import group_bars


def test_direction_labels_are_close_to_close_and_keep_one_price_days():
    from test_overnight import history
    bars = history(10)
    grouped = group_bars(bars)
    signal = sorted(grouped)[-2]
    following = next_session(signal)
    grouped[signal]['15:00'].close = 100
    end = grouped[following]['15:00']
    end.open = end.close = end.high = end.low = 101
    now = end.ended_at + timedelta(seconds=1)
    actual = close_outcome(grouped, signal, now)
    assert actual['actualReturn'] == pytest.approx(.01)
    assert actual['actualUp'] is True
    assert close_outcome(grouped, signal, end.ended_at)['status'] == 'PENDING'
    end.amount = 0
    assert close_outcome(grouped, signal, now)['status'] == 'UNVERIFIED'


def test_features_do_not_use_signal_close_or_future_market_data():
    from test_overnight import history
    bars = history(10)
    day = bars[-1].ended_at.date()
    grouped = group_bars(bars)
    value = direction_features(grouped, day, '14:30', '605058.SH')
    assert len(value) == len(FEATURES)
    for bar in bars:
        if bar.ended_at > datetime.fromisoformat(f'{day}T14:30:00'):
            bar.close *= 2
            bar.amount *= 10
    assert direction_features(group_bars(bars), day, '14:30', '605058.SH') == value
    store = SimpleNamespace(bars=lambda code, through: [b for b in bars if b.ended_at <= through])
    through = bars[-1].ended_at
    rows, audit = build_direction_panel(store, ['605058.SH'], '14:30', through)
    assert rows and audit['target'] == 'NEXT_SESSION_CLOSE_VS_DECISION_PRICE'
    assert all(r['exitAt'] < through.isoformat() for r in rows)
    assert not any('actualNetReturn' in r for r in rows)


@pytest.fixture(scope='module')
def direction_rows():
    rng = np.random.default_rng(37)
    rows = []
    for index in range(165):
        day = date(2026, 1, 1) + timedelta(days=index)
        for stock in range(20):
            x = rng.normal(size=len(FEATURES))
            rows.append({'signalDate': str(day), 'exitAt': f'{day + timedelta(days=1)}T15:00:00',
                'instrumentCode': f'600{stock:03}.SH', 'features': x.tolist(),
                'actualReturn': float(.03 * x[14] + rng.normal(0, .005))})
    return rows


@pytest.fixture(scope='module')
def direction_fit(direction_rows):
    return fit_direction(direction_rows, '2026-07-01T12:00:00', '14:30')


def test_direction_can_learn_both_classes_and_calibration_preserves_order(direction_rows, direction_fit):
    audit = direction_fit['audit']['historical']
    assert audit['accuracy'] > .8
    assert audit['balancedAccuracy'] > .8
    assert .2 < audit['predictedUpRate'] < .8
    assert not audit['eligible']
    assert direction_fit['model']['calibration']['slope'] == 1
    portable = json.loads(json.dumps(direction_fit))
    prediction = direction_prediction(portable, direction_rows[0]['features'])
    assert prediction['status'] == 'SHADOW'
    assert prediction['upProbability'] + prediction['notUpProbability'] == pytest.approx(1)
    assert prediction['modelFitThrough'] == direction_fit['audit']['selectionThrough']
    assert prediction['initialTrainingThrough'] == direction_fit['audit']['trainingThrough']
    assert prediction['modelFitThrough'] > prediction['initialTrainingThrough']


def test_direction_model_selection_and_calibration_never_see_future(direction_rows, direction_fit):
    first = direction_fit['audit']['folds'][0]
    changed = deepcopy(direction_rows)
    for row in changed:
        if row['exitAt'] >= first['cutoff']:
            row['actualReturn'] *= -5
            row['features'] = [100.] * len(FEATURES)
    assert fit_direction_before(changed, first['cutoff']) == fit_direction_before(direction_rows, first['cutoff'])
    for fold in direction_fit['audit']['folds']:
        assert fold['trainingThrough'][:10] < fold['selectionStart']
        assert fold['selectionThrough'][:10] < fold['calibrationStart']
        assert fold['calibrationThrough'] < fold['cutoff']
    assert fit_direction(direction_rows[:140 * 20], '2026-07-01T12:00:00', '14:30') is None


def test_single_class_does_not_fabricate_discrimination(direction_rows):
    rows = [{**r, 'actualReturn': -.01} for r in direction_rows]
    fitted = fit_direction_before(rows, '2026-07-01T12:00:00')
    assert fitted['model']['selected'] == 'PRIOR'
    assert fitted['model']['calibration']['status'] == 'NOT_FITTED'
    assert direction_prediction({**fitted, 'audit': {'historical': {}}}, rows[0]['features'])['direction'] == 'NOT_UP'
    assert direction_prediction({**fitted, 'audit': {'historical': {}}}, rows[0]['features'])['assessmentStatus'] == 'BASELINE_ONLY'


def test_old_artifacts_resolve_final_refit_without_rewriting_initial_training(direction_fit, direction_rows):
    legacy = deepcopy(direction_fit)
    del legacy['audit']['modelFitThrough']
    forecast = direction_prediction(legacy, direction_rows[0]['features'])
    assert forecast['modelFitThrough'] == legacy['audit']['selectionThrough']
    assert legacy['audit']['trainingThrough'] < forecast['modelFitThrough']


def test_sparse_latest_day_cannot_shift_training_or_daily_evaluation(direction_rows, direction_fit):
    partial = {**direction_rows[-1], 'signalDate': '2026-06-20', 'exitAt': '2026-06-21T15:00:00',
               'actualReturn': 10, 'features': [99.] * len(FEATURES)}
    modified = direction_rows + [partial]
    through = '2026-07-01T12:00:00'
    assert fit_direction_before(modified, through) == fit_direction_before(direction_rows, through)
    fitted = fit_direction(modified, through, '14:30')
    assert fitted['audit']['historical'] == direction_fit['audit']['historical']
    assert fitted['audit']['dateCoverage']['excludedDates'] == ['2026-06-20']
    assert fitted['audit']['dateCoverage']['requiredSymbolsPerDate'] == 20


def test_date_coverage_uses_only_matured_symbols_and_unique_stocks(direction_rows):
    from finscope_market_data.overnight.direction_learning import covered_matured_rows
    rows = direction_rows[:40]
    pending = [{**rows[0], 'instrumentCode': f'NEW{i}', 'exitAt': '2026-12-01T15:00:00'} for i in range(80)]
    usable, audit = covered_matured_rows(rows + pending, '2026-02-01T12:00:00')
    assert usable == rows
    assert audit['requiredSymbolsPerDate'] == 20
    # Repeating the same stock must not masquerade as a covered cross-section.
    assert not covered_matured_rows([rows[0]] * 40, '2026-02-01T12:00:00')[0]


def test_price_outcome_settles_even_when_a_trade_could_not_be_entered():
    from test_overnight import history, request
    from finscope_market_data.overnight.engine import settle
    bars = history(10)
    grouped = group_bars(bars)
    signal = sorted(grouped)[-2]
    entry = grouped[signal]['14:40']
    entry.high = entry.low = entry.close
    req = request(signal)
    report = {'request': req.model_dump(mode='json', by_alias=True), 'targets': [],
              'closeDirection': {'upProbability': .6}}
    result = settle(report, bars, bars[-1].ended_at + timedelta(seconds=1))
    assert result['status'] == 'ENTRY_UNVERIFIED'
    assert result['closeDirection']['status'] == 'SETTLED'
    assert isinstance(result['closeDirection']['correct'], bool)


def test_trade_settlement_does_not_hide_pending_direction():
    from finscope_market_data.overnight.service import needs_settlement
    report = {'outcome': {'status': 'SETTLED'}, 'closeDirection': {'upProbability': .6}}
    assert needs_settlement(report)
    report['outcome']['closeDirection'] = {'status': 'SETTLED'}
    assert not needs_settlement(report)
    assert not needs_settlement({'outcome': {'status': 'SETTLED'}})


def test_direction_has_its_own_forward_checkpoint_and_excludes_replays(tmp_path):
    from finscope_market_data.overnight.automation_store import AutomationStore
    from finscope_market_data.overnight.direction_dataset import PROTOCOL, TARGET
    from finscope_market_data.overnight.direction_validation import summarize_direction
    from finscope_market_data.overnight.store import OvernightStore
    meta = AutomationStore(OvernightStore(tmp_path / 'direction.db'))
    reports = []
    for i in range(60):
        day = date(2026, 1, 1) + timedelta(days=i)
        up = i % 2 == 0
        p = .8 if up else .2
        report = {'id': str(i), 'generatedAt': f'{day}T14:31:00', 'signalDate': str(day),
            'targetDate': str(day + timedelta(days=1)), 'instrumentCode': '605058.SH',
            'mode': 'TAIL_ENTRY', 'cutoff': '14:30', 'evidenceKind': 'FORWARD',
            'jointResearch': {'cohort': 'AUTOMATIC'},
            'closeDirection': {'protocol': PROTOCOL, 'target': TARGET, 'artifactId': 'one',
                'upProbability': p, 'baselineProbability': .5, 'rawUpProbability': p, 'calibratedUpProbability': p},
            'outcome': {'closeDirection': {'status': 'SETTLED', 'actualUp': up}}}
        reports.extend([report, {**report, 'id': f'{i}-revision'}, {**report, 'id': f'{i}-replay', 'evidenceKind': 'RETROSPECTIVE'},
                        {**report, 'id': f'{i}-manual', 'jointResearch': {'cohort': 'CUSTOM'}}])
    fake_store = SimpleNamespace(iter_history=lambda: iter(reports))
    result = summarize_direction(fake_store, datetime(2026, 4, 1), meta)['groups'][0]
    assert result['pairedCount'] == result['dayCount'] == 60
    assert result['eligible'] and result['metrics']['accuracy'] == pytest.approx(1)
    assert result['calibrationGate']['probabilitySource'] == 'RAW'
    old = deepcopy(reports[0])
    old.update(id='older', signalDate='2025-12-31', targetDate='2026-01-01', generatedAt='2025-12-31T14:31:00')
    reports.append(old)
    assert summarize_direction(fake_store, datetime(2026, 4, 2), meta)['groups'][0]['checkpointDays'] == result['checkpointDays']


@pytest.mark.parametrize('challenger', [False, True])
@pytest.mark.parametrize('now, matured', [
    ('2026-11-06T14:59:59', False),
    ('2026-11-06T15:00:00', False),
    ('2026-11-06T15:00:01', True),
    ('2026-11-07T00:00:00', True),
])
def test_forward_direction_counts_settled_results_after_target_close(now, matured, challenger):
    from finscope_market_data.overnight.direction_dataset import PROTOCOL, TARGET
    from finscope_market_data.overnight.direction_selection import PROTOCOL as CONTEXT_PROTOCOL
    from finscope_market_data.overnight.direction_validation import summarize_direction
    prediction = {'protocol': PROTOCOL, 'target': TARGET, 'artifactId': 'frozen-model',
                  'upProbability': .7, 'baselineProbability': .5}
    report = {'id': 'frozen', 'generatedAt': '2026-11-05T14:31:00', 'signalDate': '2026-11-05',
              'targetDate': '2026-11-06', 'instrumentCode': '605058.SH', 'mode': 'TAIL_ENTRY',
              'cutoff': '14:30', 'evidenceKind': 'FORWARD', 'jointResearch': {'cohort': 'AUTOMATIC'},
              'closeDirection': {**prediction, 'challenger': {
                  **prediction, 'protocol': CONTEXT_PROTOCOL, 'incumbentProbability': .6}},
              'outcome': {'closeDirection': {'status': 'SETTLED', 'actualUp': True}}}
    future = {**deepcopy(report), 'id': 'future', 'signalDate': '2026-11-06', 'targetDate': '2026-11-09'}
    reports = [report, future]
    store = SimpleNamespace(iter_history=lambda: iter(reports))
    result = summarize_direction(store, datetime.fromisoformat(now), challenger=challenger)
    if not matured:
        assert result['groups'] == []
        return
    group = result['groups'][0]
    assert group['recordCount'] == group['pairedCount'] == group['dayCount'] == 1
    assert group['metrics']['accuracy'] == 1
    assert group['status'] == 'ACCUMULATING'
    assert not group['eligible']

    # A date cannot enter the checkpoint until every frozen prediction has settled.
    pending = {**deepcopy(report), 'id': 'pending', 'instrumentCode': '600030.SH',
               'outcome': {'closeDirection': {'status': 'PENDING'}}}
    reports.append(pending)
    incomplete = summarize_direction(store, datetime.fromisoformat(now), challenger=challenger)['groups'][0]
    assert incomplete['recordCount'] == 2
    assert incomplete['pairedCount'] == 1
    assert incomplete['dayCount'] == 0
    assert 'metrics' not in incomplete


def test_calibration_requires_both_direction_and_probability_advantage(direction_fit, direction_rows):
    x = direction_rows[0]['features']
    raw = direction_prediction(direction_fit, x)
    assert raw['probabilitySource'] == 'RAW'
    assert raw['upProbability'] == raw['rawUpProbability']
    brier_only = {'directionSource': 'RAW', 'probabilitySource': 'INTERCEPT'}
    assert direction_prediction(direction_fit, x, brier_only)['upProbability'] == raw['upProbability']
    both = {'directionSource': 'INTERCEPT', 'probabilitySource': 'INTERCEPT'}
    assert direction_prediction(direction_fit, x, both)['upProbability'] == raw['calibratedUpProbability']


def test_direction_attaches_without_previous_pool_context(tmp_path, direction_fit):
    from test_overnight import history, request
    from finscope_market_data.overnight.automation_store import AutomationStore
    from finscope_market_data.overnight.joint_dataset import JointProfile, PROTOCOL
    from finscope_market_data.overnight.joint_research import OvernightJointResearch
    from finscope_market_data.overnight.store import OvernightStore
    bars = history(165)
    day = bars[-1].ended_at.date()
    # All labels precede this fixture's signal date.
    store = OvernightStore(tmp_path / 'attach.db')
    meta = AutomationStore(store)
    research = OvernightJointResearch(store, meta)
    fitted = {**direction_fit, 'data': {'fingerprint': 'direction-input'}}
    artifact = research.models.publish({'protocol': PROTOCOL, 'profile': JointProfile('TAIL_ENTRY', '14:30', 20).dump(),
        'createdAt': f'{day}T12:00:00', 'labelsThrough': '2026-06-15T15:00:00',
        'context': {}, 'targets': {}, 'data': {'fingerprint': 'trade', 'symbolCount': 20}, 'closeDirection': fitted}, [])
    report = {'dataThrough': f'{day}T14:30:00', 'jointResearch': {'cohort': 'AUTOMATIC'}, 'targets': []}
    research.attach(report, request(day), bars)
    assert report['closeDirection']['artifactId'] == artifact['id']
    assert report['closeDirection']['probabilitySource'] == 'RAW'
    assert not report['closeDirection']['validated']
    summary = research.models.summaries(datetime.fromisoformat(f'{day}T14:30:00'))[0]
    assert 'model' not in summary['closeDirection']


def test_background_price_model_is_not_blocked_by_unavailable_trade_labels(tmp_path, monkeypatch, direction_rows, direction_fit):
    from finscope_market_data.overnight.automation import OvernightAutomation
    from finscope_market_data.overnight.joint_research import OvernightJointResearch
    from finscope_market_data.overnight.joint_worker import OvernightJointWorker
    from finscope_market_data.overnight.store import OvernightStore
    from finscope_market_data.overnight.universe import POOL_KEY
    store = OvernightStore(tmp_path / 'worker.db')
    now = datetime(2026, 9, 21, 18)
    automation = OvernightAutomation(SimpleNamespace(store=store, clock=lambda: now), None)
    codes = sorted({r['instrumentCode'] for r in direction_rows})
    automation.store.put(POOL_KEY, {'members': [{'instrumentCode': code} for code in codes],
                                  'fingerprint': 'pool', 'createdAt': '2026-09-01T18:00:00'})
    monkeypatch.setattr(store, 'coverage', lambda now: [{'instrumentCode': code, 'completeDays': 165} for code in codes])
    monkeypatch.setattr('finscope_market_data.overnight.joint_worker.build_panel', lambda *args: ([], {}, {'rows': 0}))
    monkeypatch.setattr('finscope_market_data.overnight.joint_worker.fit_target', lambda *args: None)
    monkeypatch.setattr('finscope_market_data.overnight.joint_worker.build_direction_panel',
                        lambda *args: (direction_rows, {'fingerprint': 'price', 'symbolCount': 20}))
    monkeypatch.setattr('finscope_market_data.overnight.joint_worker.fit_direction', lambda *args: deepcopy(direction_fit))
    research = OvernightJointResearch(store, automation.store)
    OvernightJointWorker(automation, research).tick()
    job = automation.store.jobs(phase='JOINT')[0]
    assert job['status'] == 'COMPLETED'
    artifact = research.models.latest('TAIL_ENTRY|14:30|20', now)
    assert artifact['targets'] == {}
    assert artifact['closeDirection']['protocol'] == 'overnight-reference-direction-v2'


def test_tail_labels_and_frozen_settlement_use_decision_price_not_later_close():
    from test_overnight import history, request
    from finscope_market_data.overnight.engine import settle
    from finscope_market_data.overnight.direction_dataset import TARGET
    bars = history(10)
    grouped = group_bars(bars)
    day = sorted(grouped)[-2]
    grouped[day]['14:30'].close = 100
    grouped[day]['15:00'].close = 110
    grouped[next_session(day)]['15:00'].close = 105
    now = bars[-1].ended_at + timedelta(seconds=1)
    assert close_outcome(grouped, day, now, '14:30')['actualUp']
    assert not close_outcome(grouped, day, now)['actualUp']
    report = {'request': request(day).model_dump(mode='json', by_alias=True), 'targets': [],
              'closeDirection': {'target': TARGET, 'referencePrice': 100, 'upProbability': .7}}
    # A later source revision must not rewrite the original reference price.
    grouped[day]['14:30'].close = 120
    actual = settle(report, bars, now)['closeDirection']
    assert actual['actualReturn'] == pytest.approx(.05)
    assert actual['correct'] is True
    assert actual['referencePrice'] == 100
    legacy = {**report, 'closeDirection': {'target': 'NEXT_SESSION_CLOSE_VS_SIGNAL_CLOSE', 'upProbability': .7}}
    assert settle(legacy, bars, now)['closeDirection']['actualUp'] is False


def test_old_target_records_do_not_enter_new_forward_statistics():
    from finscope_market_data.overnight.direction_validation import summarize_direction
    from finscope_market_data.overnight.direction_dataset import PROTOCOL
    report = {'id': 'old', 'generatedAt': '2026-10-08T14:30:01', 'evidenceKind': 'FORWARD',
              'jointResearch': {'cohort': 'AUTOMATIC'}, 'closeDirection': {'protocol': PROTOCOL,
              'target': 'NEXT_SESSION_CLOSE_VS_SIGNAL_CLOSE', 'upProbability': .7}}
    assert summarize_direction(SimpleNamespace(iter_history=lambda: iter([report])), datetime(2026, 11, 4))['groups'] == []
