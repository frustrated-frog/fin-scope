from copy import deepcopy
from datetime import date, datetime, timedelta
from types import SimpleNamespace
import json

import numpy as np
import pytest

from test_overnight_direction import direction_rows
from test_overnight_error_audit import scan
from test_overnight_market_research import reports
from finscope_market_data.overnight.automation_store import AutomationStore
from finscope_market_data.overnight.engine import group_bars
from finscope_market_data.overnight.direction_dataset import direction_features
from finscope_market_data.overnight.direction_learning import direction_prediction
from finscope_market_data.overnight.fresh_research import GROUPS, PROTOCOL, train_fresh_research, attach_fresh_research
from finscope_market_data.overnight.fresh_validation import summarize_fresh_forward
from finscope_market_data.overnight.index_history import (
    INDEX_CODES, KEY, OvernightIndexHistory, index_features, paired_index_rows, history_revision,
)
from finscope_market_data.overnight.joint_dataset import JointProfile
from finscope_market_data.overnight.joint_store import OvernightJointStore
from finscope_market_data.overnight.store import OvernightStore


@pytest.fixture(scope='module')
def index_history():
    rng = np.random.default_rng(17)
    days = [str(date(2024, 1, 1) + timedelta(days=i)) for i in range(1100)]
    return {code: {'closes': dict(zip(days, (100 * np.cumprod(1 + rng.normal(0, .01, len(days)))).tolist())),
                   'receivedAt': '2026-06-30T20:00:00', 'throughDate': '2026-06-30'} for code in INDEX_CODES}


def test_index_features_use_exact_previous_sessions_and_never_current_or_missing(index_history, direction_rows):
    before = index_features(index_history, '2026-06-30')
    changed = deepcopy(index_history)
    for item in changed.values():
        for day in item['closes']:
            if day >= '2026-06-30':
                item['closes'][day] *= 100
    assert index_features(changed, '2026-06-30') == before
    del changed[INDEX_CODES[0]]['closes']['2026-06-29']
    assert index_features(changed, '2026-06-30') is None
    base, enhanced = paired_index_rows(direction_rows, index_history)
    assert 0 < len(base) == len(enhanced) < len(direction_rows)
    assert all(a['features'] == b['features'][:-9] and a['exitAt'] == b['exitAt'] for a, b in zip(base, enhanced))
    assert history_revision({}) == history_revision({INDEX_CODES[0]: index_history[INDEX_CODES[0]]}) == 'PENDING'
    assert history_revision(index_history) == '2026-06-30'


@pytest.fixture(scope='module')
def research(direction_rows, index_history):
    # Calendar coverage starts in 2026. Leave a genuine 21-session warmup.
    rows = [{**r, 'signalDate': str(date.fromisoformat(r['signalDate']) + timedelta(days=40)),
             'exitAt': (datetime.fromisoformat(r['exitAt']) + timedelta(days=40)).isoformat()} for r in direction_rows]
    return train_fresh_research(rows, index_history, '2026-08-01T12:00:00', '14:30')


def test_real_paired_models_share_selection_and_labels(research):
    assert research['indexAudit']['status'] == 'SHADOW'
    assert research['indexAudit']['nonConstantFeatures'] == 9
    left, right = [research['models'][name] for name in ('INDEX_BASELINE', 'INDEX')]
    assert len(right['features']) == len(left['features']) + 9
    for a, b in zip(left['audit']['folds'], right['audit']['folds']):
        assert a['selected'] == b['selected'] == 'SHAPE_ENSEMBLE'
        assert a['trainingThrough'] == b['trainingThrough'] < b['predictionAt']
        assert a['trainingRows'] == b['trainingRows']
    assert 'MATCHED_BASELINE' in right['audit']['historical']['comparisons']
    assert not right['audit']['historical']['eligible']


def test_actual_publication_reload_attachment_preserves_incumbent(tmp_path, research, index_history):
    from test_overnight import history, request
    bars = history(180)
    day = bars[-1].ended_at.date()
    decision = datetime.fromisoformat(f'{day}T14:30:00')
    profile = JointProfile('TAIL_ENTRY', '14:30', 20)
    store = OvernightJointStore(OvernightStore(tmp_path / 'models.db'))
    artifact = store.publish({'protocol': 'test', 'profile': profile.dump(), 'targets': {},
        'createdAt': f'{day}T08:00:00', 'labelsThrough': '2026-07-31T15:00:00',
        'closeDirection': research['models']['INDEX_BASELINE'],
        'freshResearch': deepcopy(research)}, {'indexHistory': index_history})
    loaded = store.latest(profile.key, decision)
    assert json.loads(json.dumps(artifact)) == loaded
    assert all('model' not in value for value in store.summaries(decision)[0]['freshResearch']['models'].values())
    value = {'dataThrough': decision.isoformat(), 'closeDirection': {'upProbability': .61}}
    attach_fresh_research(value, request(day), group_bars(bars), loaded, index_history)
    parent = value['closeDirection']
    assert parent['upProbability'] == .61
    assert len(parent['freshModels']) == 3
    assert all(r['status'] == 'SHADOW' and not r['validated'] for r in parent['freshModels'])
    expected = direction_prediction(loaded['closeDirection'], direction_features(group_bars(bars), day, '14:30', '605058.SH'), {})
    assert parent['freshModels'][0]['matchedBaselineProbability'] == expected['rawUpProbability']
    assert parent['freshModels'][0]['liveProbability'] == .61
    assert parent['freshModels'][1]['matchedBaselineProbability'] == parent['freshModels'][0]['upProbability']
    late = deepcopy(index_history)
    late[INDEX_CODES[0]]['receivedAt'] = decision.isoformat()
    attach_fresh_research(value, request(day), group_bars(bars), loaded, late)
    assert parent['freshModels'][2]['status'] == 'MISSING_FEATURES'
    assert parent['freshModels'][0]['status'] == 'SHADOW'
    loaded['createdAt'] = decision.isoformat()
    attach_fresh_research(value, request(day), group_bars(bars), loaded, index_history)
    assert all(r['status'] == 'WAITING_MODEL' for r in parent['freshModels'])
    artifact['freshResearch']['models']['FRESH']['audit']['testThrough'] = '2099-01-01'
    with pytest.raises(ValueError, match='标签时点'):
        store.publish(artifact, [])


@pytest.mark.asyncio
async def test_automatic_history_retains_good_data_on_failure_and_retries(tmp_path):
    now = datetime(2026, 11, 6, 20)
    meta = AutomationStore(OvernightStore(tmp_path / 'collection.db'))
    previous = {INDEX_CODES[0]: {'closes': {'2026-11-04': 100}, 'throughDate': '2026-11-04'}}
    meta.put(KEY, previous)
    calls = []
    async def fetch(capability, symbol, **kwargs):
        calls.append(symbol.code)
        if len(calls) == 1:
            raise TimeoutError()
        rows = [SimpleNamespace(symbol=symbol, trade_date=str(date(2026, 9, 1) + timedelta(days=i)),
                                close=100 + i, is_complete=True) for i in range(67)]
        return SimpleNamespace(source_code='TENCENT_DAILY_RATIO_QFQ', quality_status='FRESH', data=rows)
    auto = SimpleNamespace(store=meta, clock=lambda: now)
    worker = OvernightIndexHistory(auto, SimpleNamespace(fetch=fetch))
    await worker.tick()
    assert meta.get(KEY) == previous
    now += timedelta(minutes=20)
    await worker.tick()
    result = meta.get(KEY)[INDEX_CODES[0]]
    assert result['throughDate'] == '2026-11-05'
    assert '2026-11-06' not in result['closes']
    assert not meta.jobs()  # Internal fetch jobs stay out of the user task list.
    now = now.replace(hour=14)
    await worker.tick()
    assert len(calls) == 2


def fresh_reports():
    values = reports()
    for value in values:
        value['closeDirection']['freshModels'] = [{**value['closeDirection']['marketGroups'][0],
            'group': name, 'protocol': PROTOCOL, 'featureFingerprint': 'input',
            'trainingThrough': '2026-09-18T15:00:00', 'calibrationThrough': '2026-09-18T15:00:00',
            'indexHistoryFingerprint': 'index', 'indexHistoryReceivedAt': '2026-09-21T08:00:00'} for name in GROUPS]
    return values


def fresh_audit(values):
    meta = SimpleNamespace(iter_jobs=lambda phase: iter([scan()] if phase == 'DISCOVER' else []))
    return summarize_fresh_forward(SimpleNamespace(iter_history=lambda: iter(values)), meta, datetime(2026, 9, 23))


def test_fresh_validation_requires_complete_original_cohort_and_separate_groups():
    values = fresh_reports()
    result = fresh_audit(values)
    assert result['protocol'] == PROTOCOL
    assert all(r['dayCount'] == 1 and r['metrics']['accuracy'] == 1 and not r['eligible'] for r in result['groups'])
    values[0]['closeDirection']['freshModels'][2]['indexHistoryReceivedAt'] = '2026-09-22T08:00:00'
    groups = {r['group']: r for r in fresh_audit(values)['groups']}
    assert groups['INDEX']['dayCount'] == 0 and groups['INDEX']['missing'] == {'INVALID_EVIDENCE': 1}
    assert groups['FRESH']['dayCount'] == 1
    assert all(r['dayCount'] == 0 and r['coverage'] == pytest.approx(2 / 3) for r in fresh_audit(values[1:])['groups'])
