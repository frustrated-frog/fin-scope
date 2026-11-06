from copy import deepcopy
from datetime import datetime, timedelta
from types import SimpleNamespace

import pytest

from finscope_market_data.overnight.context_features import INDEX_CODES, context_time
from finscope_market_data.overnight.context_store import OvernightContextStore
from finscope_market_data.overnight.engine import group_bars
from finscope_market_data.overnight.fresh_direction import MIN_RESEARCH_DAYS
from finscope_market_data.overnight.market_context import market_groups, paired_market_panels
from finscope_market_data.overnight.market_research import attach_market_direction
from finscope_market_data.overnight.relative_strength import PROTOCOL, FEATURES, minute_state, relative_features, coverage
from finscope_market_data.overnight.store import OvernightStore
from test_overnight import history, request
from test_overnight_multiscale import history_rows


def captured(bars):
    day = bars[-1].ended_at.date()
    value = minute_state(group_bars(bars), day, '14:30')
    received = (context_time(day, '14:30') + timedelta(seconds=30)).isoformat()
    row = {**value, 'receivedAt': received, 'source': 'TEST'}
    return {'signalDate': str(day), 'cutoff': '14:30', 'observedAt': value['observedAt'], 'receivedAt': received,
            'pool': {'605058.SH': row}, 'indexMinutes': {code: deepcopy(row) for code in INDEX_CODES},
            'relativeStrength': {'protocol': PROTOCOL}}


def test_relative_windows_are_aligned_and_never_see_later_prices():
    bars = history(12)
    day = bars[-1].ended_at.date()
    value = captured(bars)
    original = minute_state(group_bars(bars), day, '14:30')
    assert relative_features('605058.SH', value) == [0.] * len(FEATURES)
    value['pool']['605058.SH']['last30'] += .02
    value['pool']['605058.SH']['drawdown'] -= .01
    assert relative_features('605058.SH', value)[:5] == pytest.approx([0., .02, 0., 0., -.01])
    for bar in bars:
        if bar.ended_at > context_time(day, '14:30'):
            bar.close *= 4
            bar.high *= 5
    assert minute_state(group_bars(bars), day, '14:30') == original
    missing = [bar for bar in bars if bar.ended_at != context_time(day, '14:30') - timedelta(minutes=60)]
    assert minute_state(group_bars(missing), day, '14:30') is None


@pytest.mark.parametrize('change', [{'observedAt': '2099-01-01T14:20:00'}, {'receivedAt': '2099-01-01T15:00:00'},
                                   {'last60': None}, {'return': float('nan')}])
def test_missing_late_or_misaligned_index_never_becomes_zero(change):
    value = captured(history(12))
    value['indexMinutes'][INDEX_CODES[0]].update(change)
    assert relative_features('605058.SH', value) is None
    assert coverage(value)['pairedSymbols'] == 0


def test_relative_capture_is_independent_of_broad_quotes_and_never_backfilled(tmp_path):
    value = captured(history(12))
    value['relativeStrength'] = coverage(value)
    assert set(market_groups('605058.SH', value)) == {'RELATIVE'}
    store = OvernightContextStore(OvernightStore(tmp_path / 'context.db'))
    original = store.freeze(value)
    value['indexMinutes'][INDEX_CODES[0]]['last30'] += .1
    assert store.freeze(value) == original
    assert 'indexMinutes' not in store.recent()[0]
    assert store.recent()[0]['relativeStrength']['pairedSymbols'] == 1
    value['indexMinutes'][INDEX_CODES[0]]['receivedAt'] = '2099-01-01T15:00:00'
    with pytest.raises(ValueError, match='接收时间'):
        store.freeze(value)
    rows = [{'signalDate': original['signalDate'], 'instrumentCode': '605058.SH', 'features': [1.] * 26}]
    base, enhanced = paired_market_panels(rows, store, '14:30')['RELATIVE']
    assert base == rows and len(enhanced[0]['features']) == 26 + len(FEATURES)
    assert paired_market_panels(rows, SimpleNamespace(at=lambda *args: None), '14:30')['RELATIVE'] == ([], [])


def test_relative_ablation_uses_same_full_feature_recipe_and_attaches_shadow(tmp_path, monkeypatch, history_rows):
    from finscope_market_data.overnight import market_ablation
    from finscope_market_data.overnight.joint_dataset import JointProfile
    from finscope_market_data.overnight.joint_store import OvernightJointStore
    base = [{**row, 'features': row['features'][:26]} for row in history_rows]
    enhanced = [{**row, 'features': row['features'] + history_rows[i]['features'][-len(FEATURES):]}
                for i, row in enumerate(base)]
    monkeypatch.setattr(market_ablation, 'paired_market_panels', lambda *args: {'RELATIVE': (base, enhanced)})
    models = {}
    audit = market_ablation.evaluate_market_groups(base, None, '14:30', datetime(2026, 7, 1), models=models)
    assert audit['groups'][0]['requiredDays'] == MIN_RESEARCH_DAYS
    assert audit['groups'][0]['status'] == 'RESEARCH_ONLY'
    pair = models['RELATIVE']
    for left, right in zip(pair['baseline']['audit']['folds'], pair['enhanced']['audit']['folds']):
        assert left['selected'] == right['selected'] == 'SHAPE_ENSEMBLE'
        assert left['trainingThrough'] == right['trainingThrough'] < left['predictionAt']
        assert left['trainingRows'] == right['trainingRows']
    bars = history(180)
    day = bars[-1].ended_at.date()
    decision = datetime.fromisoformat(f'{day}T14:30:00')
    store = OvernightJointStore(OvernightStore(tmp_path / 'model.db'))
    artifact = store.publish({'profile': JointProfile('TAIL_ENTRY', '14:30', 20).dump(), 'targets': {},
        'createdAt': f'{day}T08:00:00', 'labelsThrough': '2026-06-30T15:00:00', 'marketModels': models}, [])
    snapshot = captured(bars)
    contexts = OvernightContextStore(store.store)
    contexts.freeze(snapshot)
    report = {'dataThrough': decision.isoformat(), 'closeDirection': {'upProbability': .62}}
    attach_market_direction(report, request(day), group_bars(bars), artifact, contexts)
    result = next(row for row in report['closeDirection']['marketGroups'] if row['group'] == 'RELATIVE')
    assert result['status'] == 'SHADOW' and not result['validated']
    assert report['closeDirection']['upProbability'] == .62
    assert 0 <= result['upProbability'] <= 1
