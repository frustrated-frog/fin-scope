from copy import deepcopy
from datetime import datetime, timedelta
import json
from types import SimpleNamespace

import numpy as np
import pytest

from finscope_market_data.overnight.candidate_evaluation import evaluate_candidates
from finscope_market_data.overnight.context_features import build_context_panel
from finscope_market_data.overnight.context_research import MODEL_FEATURES
from finscope_market_data.overnight.direction_candidates import (
    MULTISCALE_RECIPE, candidate_probabilities, fit_candidates, training_weights,
)
from finscope_market_data.overnight.direction_learning import fit_direction_before
from finscope_market_data.overnight.direction_selection import POLICY
from finscope_market_data.overnight.engine import group_bars
from finscope_market_data.overnight.multiscale_features import FEATURES, multiscale_features


@pytest.mark.parametrize('cutoff', ['14:30', '14:45', '15:00'])
def test_history_features_use_only_complete_past_sessions_and_cutoff(cutoff):
    from test_overnight import history
    bars = history(35)
    grouped = group_bars(bars)
    day = sorted(grouped)[-2]
    decision = datetime.fromisoformat(f'{day}T{cutoff}:00')
    values = multiscale_features(grouped, day, cutoff)
    assert len(values) == len(FEATURES) == 24
    assert np.all(np.isfinite(values))
    for bar in bars:
        if bar.ended_at > decision:
            bar.open = bar.close = bar.high = bar.low = 999999
            bar.amount *= 100
    assert multiscale_features(group_bars(bars), day, cutoff) == values
    del grouped[sorted(grouped)[-12]]['10:00']
    assert multiscale_features(grouped, day, cutoff) is None


def test_multiscale_features_match_price_and_same_window_amount():
    from test_overnight import history
    from finscope_market_data.overnight.engine import expected_times
    grouped = group_bars(history(25))
    day = sorted(grouped)[-1]
    for bars in grouped.values():
        for bar in bars.values():
            bar.open = bar.close = bar.high = bar.low = 10
            bar.amount = 100
    values = multiscale_features(grouped, day, '14:30')
    assert values == [0., 0., 0., 0., .5, 0., 0., 0.] * 3
    grouped[day]['14:30'].close = 11
    for stamp in expected_times('14:30'):
        grouped[day][stamp].amount *= 2
    values = multiscale_features(grouped, day, '14:30')
    assert values[1] == pytest.approx(.1)
    assert values[5] == pytest.approx(np.log(2))


def test_history_panel_keeps_train_and_live_feature_order_identical():
    from test_overnight import history
    bars = history(35)
    store = SimpleNamespace(bars=lambda code, through: bars)
    codes = [f'600{i:03}.SH' for i in range(20)]
    through = bars[-1].ended_at + timedelta(days=1)
    rows, audit = build_context_panel(store, codes, '14:30', through, include_multiscale=True)
    legacy, _ = build_context_panel(store, codes, '14:30', through)
    old = {(r['signalDate'], r['instrumentCode']): r for r in legacy}
    assert rows and len(rows[0]['features']) == len(MODEL_FEATURES) == 68
    for row in rows:
        assert row['features'][:44] == old[(row['signalDate'], row['instrumentCode'])]['features']
        assert row['features'][44:] == multiscale_features(group_bars(bars), datetime.fromisoformat(row['signalDate']).date(), '14:30')
    assert audit['missingHistoryRows'] > 0
    assert audit['industryRows'] == audit['indexRows'] == 0


@pytest.fixture(scope='module')
def history_rows():
    rng = np.random.default_rng(61105)
    rows = []
    for i in range(165):
        day = datetime(2026, 1, 1) + timedelta(days=i)
        for stock in range(20):
            x = rng.normal(size=len(MODEL_FEATURES))
            rows.append({'signalDate': day.date().isoformat(), 'exitAt': (day + timedelta(days=1)).replace(hour=15).isoformat(),
                         'instrumentCode': f'600{stock:03}.SH', 'features': x.tolist(),
                         'actualReturn': float(.03 * x[-1] + rng.normal(0, .002))})
    return rows


def test_recent_weights_balance_dates_and_use_trading_sessions():
    rows = [{'signalDate': '2026-01-02'}] * 2 + [{'signalDate': '2026-01-05'}] * 4
    weights = training_weights(rows, half_life=1)
    assert weights.mean() == pytest.approx(1)
    assert weights[2:].sum() / weights[:2].sum() == pytest.approx(2)
    assert training_weights(rows)[2:].sum() == pytest.approx(training_weights(rows)[:2].sum())


def test_new_candidates_learn_history_and_preserve_legacy_predictions(history_rows):
    train, test = history_rows[:2000], history_rows[2000:]
    model = fit_candidates(train, MULTISCALE_RECIPE)
    portable = json.loads(json.dumps(model, allow_nan=False))
    predictions = candidate_probabilities(portable, [r['features'] for r in test])
    labels = np.array([r['actualReturn'] > 0 for r in test])
    assert np.mean((predictions['MULTISCALE_ENSEMBLE'] >= .5) == labels) > .9
    assert np.mean((predictions['RECENT_ENSEMBLE'] >= .5) == labels) > .9
    base = fit_candidates([{**r, 'features': r['features'][:44]} for r in train])
    prior = candidate_probabilities(base, [r['features'][:44] for r in test])
    for name, values in prior.items():
        assert predictions[name] == pytest.approx(values)


def test_selection_and_recent_weighting_cannot_see_future_labels(history_rows):
    cutoff = '2026-06-01T14:30:00'
    original = fit_direction_before(history_rows, cutoff, selection_policy=POLICY, recipe=MULTISCALE_RECIPE)
    assert original['model']['selected'] in ('MULTISCALE_ENSEMBLE', 'RECENT_ENSEMBLE')
    changed = deepcopy(history_rows)
    for row in changed:
        if row['exitAt'] >= cutoff:
            row['actualReturn'] *= -100
            row['features'] = [999.] * len(MODEL_FEATURES)
    assert fit_direction_before(changed, cutoff, selection_policy=POLICY, recipe=MULTISCALE_RECIPE) == original
    audit = original['audit']
    assert audit['trainingThrough'][:10] < audit['selectionStart']
    assert audit['selectionThrough'][:10] < audit['calibrationStart']
    assert audit['calibrationThrough'] < cutoff


def test_top_candidates_use_equal_dates_and_do_not_invent_skill_from_ties():
    rows = [{'signalDate': day, 'instrumentCode': str(i), 'probability': .7, 'actualUp': i % 2 == 0}
            for day, count in [('2026-01-01', 20), ('2026-01-02', 40)] for i in range(count)]
    result = evaluate_candidates(rows)
    assert result['selectionCount'] == 6
    assert result['precision'] == pytest.approx(.5)
    assert result['lift'] == pytest.approx(0)
    assert evaluate_candidates(list(reversed(rows))) == result
    with pytest.raises(ValueError):
        evaluate_candidates(rows + rows[:1])
    assert evaluate_candidates([{**r, 'probability': .4} for r in rows])['precision'] is None


def test_top_candidates_precision_is_separate_from_whole_pool_accuracy():
    rows = [{'signalDate': '2026-01-01', 'instrumentCode': str(i), 'probability': .9 - i / 100,
             'actualUp': i < 3} for i in range(20)]
    result = evaluate_candidates(rows)
    assert result['precision'] == 1
    assert result['poolUpRate'] == .15
    assert result['lift'] == pytest.approx(.85)
