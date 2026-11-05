from copy import deepcopy
from datetime import datetime, timedelta
from types import SimpleNamespace
import json

import pytest

from finscope_market_data.overnight.market_context import GROUPS
from finscope_market_data.overnight.market_research import PROTOCOL, attach_market_direction
from finscope_market_data.overnight.market_validation import summarize_market_forward
from finscope_market_data.overnight.direction_dataset import FEATURES, TARGET
from finscope_market_data.overnight.joint_store import OvernightJointStore
from finscope_market_data.overnight.joint_dataset import JointProfile, PROTOCOL as JOINT_PROTOCOL
from finscope_market_data.overnight.store import OvernightStore
from test_overnight_error_audit import scan, report
from test_overnight_multiscale import history_rows


def forecast(name='MARKET', **changes):
    return {'group': name, 'protocol': PROTOCOL, 'target': TARGET, 'status': 'SHADOW', 'artifactId': 'model',
            'contextFingerprint': 'context', 'trainedAt': '2026-09-20T18:00:00',
            'contextReceivedAt': '2026-09-21T14:21:00', 'referenceAt': '2026-09-21T14:30:00',
            'referencePrice': 10, 'upProbability': .3, 'matchedBaselineProbability': .7,
            'liveProbability': .6, **changes}


def reports():
    values = [report(f'60000{i}.SH', .6) for i in range(3)]
    for value in values:
        value['closeDirection']['marketGroups'] = [forecast(name) for name in GROUPS]
        value['outcome']['closeDirection'].update(referenceAt='2026-09-21T14:30:00', referencePrice=10)
    return values


def audit(values, now=datetime(2026, 9, 23), holdings=None, scans=None):
    meta = SimpleNamespace(iter_jobs=lambda phase: iter(([scan()] if scans is None else scans)
                                                      if phase == 'DISCOVER' else holdings or []))
    return summarize_market_forward(SimpleNamespace(iter_history=lambda: iter(values)), meta, now)


def test_full_actual_cohort_is_paired_and_never_adopted():
    groups = audit(reports())['groups']
    assert len(groups) == 3
    for group in groups:
        assert group['dayCount'] == 1 and group['pairedCount'] == group['scheduledCount'] == 3
        assert group['metrics']['accuracy'] == 1
        assert group['metrics']['comparisons']['MATCHED_BASELINE']['accuracy'] == 0
        assert group['metrics']['comparisons']['LIVE_FORECAST']['accuracy'] == 0
        assert not group['eligible']


def test_failed_missing_candidate_keeps_denominator_and_invalidates_entire_day():
    values = reports()[:2]
    group = next(r for r in audit(values)['groups'] if r['group'] == 'MARKET')
    assert group['coverage'] == pytest.approx(2/3)
    assert group['missing'] == {'MISSING_PREDICTION': 1}
    assert group['dayCount'] == 0 and group['metrics'] is None
    assert group['upCandidates']['precision'] is None


@pytest.mark.parametrize('change,reason', [({'trainedAt': '2026-09-21T14:30:00'}, 'INVALID_EVIDENCE'),
    ({'contextReceivedAt': '2026-09-21T15:00:00'}, 'INVALID_EVIDENCE'),
    ({'upProbability': float('nan')}, 'MISSING_PROBABILITY'),
    ({'protocol': 'old'}, 'MISSING_MODEL'), ({'status': 'MISSING_FEATURES'}, 'MISSING_FEATURES')])
def test_invalid_or_future_forecasts_never_score(change, reason):
    values = reports()
    values[0]['closeDirection']['marketGroups'][0].update(change)
    group = next(r for r in audit(values)['groups'] if r['group'] == 'MARKET')
    assert group['dayCount'] == 0 and group['missing'][reason] == 1
    assert next(r for r in audit(values)['groups'] if r['group'] == 'SENTIMENT')['dayCount'] == 1


def test_original_prediction_is_not_replaced_by_duplicate_and_labels_must_match_reference():
    values = reports()
    late = deepcopy(values[0])
    late.update(id='late', generatedAt='2026-09-21T14:32:00')
    late['closeDirection']['marketGroups'][0]['upProbability'] = .9
    assert next(r for r in audit([late, *values])['groups'] if r['group'] == 'MARKET')['metrics']['accuracy'] == 1
    assert audit(values, datetime(2026, 9, 22, 14))['groups'] == []
    values[0]['outcome']['closeDirection']['referencePrice'] = 11
    assert all(r['dayCount'] == 0 for r in audit(values)['groups'])


def test_holdings_separate_and_skipped_positions_remain_in_denominator():
    jobs = [{'mode': 'AFTER_CLOSE_HOLDING', 'signalDate': '2026-09-21', 'instrumentCode': f'60000{i}.SH'} for i in range(2)]
    value = reports()[0]
    value.update(cutoff='15:00', mode='AFTER_CLOSE_HOLDING')
    value['outcome']['closeDirection']['referenceAt'] = '2026-09-21T15:00:00'
    for f in value['closeDirection']['marketGroups']:
        f['referenceAt'] = '2026-09-21T15:00:00'
    result = audit([value], holdings=jobs, scans=[])['groups']
    assert all(r['mode'] == 'AFTER_CLOSE_HOLDING' and r['pairedCount'] == 1 and r['scheduledCount'] == 2 for r in result)


def test_actual_training_publication_reload_and_live_inference(tmp_path, monkeypatch, history_rows):
    from finscope_market_data.overnight import market_ablation
    from finscope_market_data.overnight.engine import group_bars
    from test_overnight import history, request
    from test_overnight_context import snapshot
    from test_overnight_market_context import broad
    from finscope_market_data.overnight.market_context import market_snapshot
    from zoneinfo import ZoneInfo
    base = [{**row, 'features': row['features'][:len(FEATURES)]} for row in history_rows]
    enhanced = [{**row, 'features': row['features'] + history_rows[i]['features'][-3:]} for i, row in enumerate(base)]
    monkeypatch.setattr(market_ablation, 'paired_market_panels', lambda *args: {'MARKET': (base, enhanced)})
    models = {}
    result = market_ablation.evaluate_market_groups(base, None, '14:30', datetime(2026, 7, 1), models=models)
    assert result['groups'][0]['status'] == 'RESEARCH_ONLY'
    bars = history(140)
    day = bars[-1].ended_at.date()
    created = datetime.combine(day, datetime.min.time())
    profile = JointProfile('TAIL_ENTRY', '14:30', 20)
    store = OvernightJointStore(OvernightStore(tmp_path / 'model.db'))
    artifact = store.publish({'protocol': JOINT_PROTOCOL, 'profile': profile.dump(), 'targets': {}, 'context': {},
        'data': {'fingerprint': 'base'}, 'createdAt': created.isoformat(), 'labelsThrough': '2026-06-30T15:00:00',
        'marketModels': models}, {'directionRows': base})
    decision = datetime.fromisoformat(f'{day}T14:30:00')
    loaded = store.latest(profile.key, decision)
    assert json.loads(json.dumps(artifact)) == loaded
    assert 'marketModels' not in store.summaries(decision)[0]
    context = snapshot(str(day))
    frame, _ = broad()
    observed = datetime.fromisoformat(context['observedAt'])
    frame['observed_at'] = observed.replace(tzinfo=ZoneInfo('Asia/Shanghai'))
    context.update(market=market_snapshot(frame, observed, observed + timedelta(seconds=30)), fingerprint='context')
    value = {'dataThrough': decision.isoformat(), 'closeDirection': {'upProbability': .61}}
    contexts = SimpleNamespace(at=lambda *args: context)
    attach_market_direction(value, request(day), group_bars(bars), loaded, contexts)
    assert value['closeDirection']['upProbability'] == .61
    forecasted = value['closeDirection']['marketGroups'][0]
    assert forecasted['status'] == 'SHADOW' and not forecasted['validated']
    assert 0 <= forecasted['upProbability'] <= 1 and 0 <= forecasted['matchedBaselineProbability'] <= 1
    assert forecasted['liveProbability'] == .61
    attach_market_direction(value, request(day), group_bars(bars), loaded, SimpleNamespace(at=lambda *args: None))
    assert value['closeDirection']['marketGroups'][0]['status'] == 'MISSING_FEATURES'
    loaded['createdAt'] = decision.isoformat()
    attach_market_direction(value, request(day), group_bars(bars), loaded, contexts)
    assert value['closeDirection']['marketGroups'][0]['status'] == 'WAITING_MODEL'
    artifact['marketModels']['MARKET']['enhanced']['audit']['testThrough'] = '2099-01-01'
    with pytest.raises(ValueError, match='标签时点'):
        store.publish(artifact, [])
