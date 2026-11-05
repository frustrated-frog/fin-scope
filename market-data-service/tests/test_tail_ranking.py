from copy import deepcopy
import json

import numpy as np
import pytest

from test_overnight_multiscale import history_rows
from finscope_market_data.overnight.candidate_evaluation import evaluate_ranking
from finscope_market_data.overnight.tail_ranking import fit_pair_before, fit_tail_ranking, predict_pair, validate_panel


def test_rank_scores_are_unbounded_and_ties_are_fractional():
    rows = [{'signalDate': '2026-11-05', 'instrumentCode': str(i), 'score': -9., 'actualUp': i < 2} for i in range(5)]
    result = evaluate_ranking(rows)
    assert result['precision'] == pytest.approx(.4)
    assert result['lift'] == pytest.approx(0)
    assert result['probabilityThreshold'] is None
    assert result['selectionCount'] == 3
    with pytest.raises(ValueError):
        evaluate_ranking(rows + [rows[0]])
    rows[0]['score'] = float('nan')
    with pytest.raises(ValueError):
        evaluate_ranking(rows)


def test_future_and_reserved_calibration_labels_cannot_change_pair(history_rows):
    cutoff = '2026-06-01T14:30:00'
    before = fit_pair_before(history_rows, cutoff)
    assert before
    from finscope_market_data.overnight.direction_learning import temporal_parts
    calibration_start = min(r['signalDate'] for r in temporal_parts(history_rows, cutoff)[2])
    mutated = [{**r, 'actualReturn': -r['actualReturn'] * 10} if r['signalDate'] >= calibration_start else r for r in history_rows]
    after = fit_pair_before(mutated, cutoff)
    assert before['model'] == after['model']
    assert before['audit']['trainingThrough'][:10] < calibration_start


def test_binary_objective_does_not_learn_return_magnitudes_and_serializes(history_rows):
    fitted = fit_pair_before(history_rows, '2026-07-01T12:00:00')
    mutated = [{**r, 'actualReturn': 100 if r['actualReturn'] > 0 else -100} for r in history_rows]
    same = fit_pair_before(mutated, '2026-07-01T12:00:00')
    x = [r['features'] for r in history_rows[:20]]
    for name, values in predict_pair(json.loads(json.dumps(fitted)), x).items():
        np.testing.assert_array_equal(values, predict_pair(same, x)[name])
    assert predict_pair(fitted, x)['RANKER'].std() > 0
    assert fit_pair_before([{**r, 'actualReturn': 1.} for r in history_rows], '2026-07-01T12:00:00') is None


def test_panel_rejects_duplicates_bad_features_and_same_day_labels(history_rows):
    with pytest.raises(ValueError):
        validate_panel(history_rows + [history_rows[0]])
    row = deepcopy(history_rows[0])
    row['features'][0] = float('inf')
    with pytest.raises(ValueError):
        validate_panel([row])
    row = {**history_rows[0], 'exitAt': history_rows[0]['signalDate'] + 'T15:00:00'}
    with pytest.raises(ValueError):
        validate_panel([row])


def test_walk_forward_pairs_every_stock_and_never_promotes(history_rows):
    fitted = fit_tail_ranking(history_rows, '2026-07-01T12:00:00', '14:30', include_checks=True)
    assert fitted and not fitted['eligible']
    audit = fitted['audit']
    assert audit['historical']['pairedDays'] == 20
    assert all(v['dayCount'] == 20 for v in audit['historical']['results'].values())
    assert len(fitted['checks']) == audit['historical']['pairedRows']
    assert all(f['trainingThrough'] < f['signalDate'] for f in audit['folds'])
    assert audit['historical']['results']['UNIFORM']['lift'] == pytest.approx(0)
    with pytest.raises(ValueError):
        fit_tail_ranking(history_rows, '2026-07-01T12:00:00', '15:00')
