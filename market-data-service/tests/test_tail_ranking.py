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


def test_live_attachment_rejects_future_artifact_and_freezes_scores(history_rows):
    from types import SimpleNamespace
    from test_overnight import history, request
    from test_overnight_context import snapshot
    from finscope_market_data.overnight.engine import group_bars
    from finscope_market_data.overnight.tail_ranking_research import attach_tail_ranking
    from finscope_market_data.overnight.tail_ranking import PROTOCOL
    from finscope_market_data.overnight.direction_dataset import TARGET
    from finscope_market_data.overnight.context_research import MODEL_FEATURES
    fitted = {**fit_pair_before(history_rows, '2026-07-01T12:00:00'),
              'protocol': PROTOCOL, 'target': TARGET, 'features': list(MODEL_FEATURES)}
    bars = history(35)
    day = bars[-1].ended_at.date()
    decision = f'{day}T14:30:00'
    artifact = {'id': 'paired', 'universeFingerprint': 'pool', 'tailRanking': fitted,
                'createdAt': decision, 'labelsThrough': '2025-12-31T15:00:00'}
    contexts = SimpleNamespace(at=lambda *args: {**snapshot(str(day)), 'universeFingerprint': 'pool'})
    report = {'dataThrough': decision, 'closeDirection': {'upProbability': .61}}
    attach_tail_ranking(report, group_bars(bars), request(day), artifact, contexts)
    assert report['closeDirection']['tailRanking']['status'] == 'WAITING_MODEL'
    artifact['createdAt'] = '2026-01-01T10:00:00'
    attach_tail_ranking(report, group_bars(bars), request(day), artifact, contexts)
    value = report['closeDirection']['tailRanking']
    assert value['status'] == 'SHADOW' and value['validated'] is False
    assert np.isfinite(value['rankScore']) and 'upProbability' not in value
    assert report['closeDirection']['upProbability'] == .61


def test_actual_cohort_rejects_partial_future_mismatched_reference_and_model():
    from test_overnight_error_audit import audit, report
    from finscope_market_data.overnight.tail_ranking import PROTOCOL
    from finscope_market_data.overnight.direction_dataset import TARGET
    reports = [report(f'60000{i}.SH', .6) for i in range(3)]
    for i, value in enumerate(reports):
        value['dataThrough'] = '2026-09-21T14:30:00'
        value['closeDirection']['referencePrice'] = 10.
        value['closeDirection']['tailRanking'] = {'protocol': PROTOCOL, 'target': TARGET, 'status': 'SHADOW',
            'artifactId': 'paired', 'trainedAt': '2026-09-21T10:00:00', 'referenceAt': value['dataThrough'],
            'referencePrice': 10., 'rankScore': i - 9., 'classifierProbability': .6, 'momentumScore': .01}
    result = audit(reports)['groups'][0]['rankingComparison']
    assert result['pairedDays'] == 1 and result['results']['RANKER']['precision'] == 0
    for field, bad in [('rankScore', None), ('trainedAt', '2026-09-21T15:00:00'),
                       ('referencePrice', 11.), ('artifactId', 'other'), ('protocol', 'old')]:
        changed = deepcopy(reports)
        changed[0]['closeDirection']['tailRanking'][field] = bad
        comparison = audit(changed)['groups'][0]['rankingComparison']
        assert comparison['pairedDays'] == 0 and comparison['missingPairedDays'] == 1
        assert comparison['results']['RANKER']['precision'] is None
    assert audit(reports[:2])['groups'][0]['rankingComparison']['pairedDays'] == 0


def test_artifact_checks_ranking_label_cutoffs_and_hides_model_in_status(tmp_path):
    from datetime import datetime
    from finscope_market_data.overnight.store import OvernightStore
    from finscope_market_data.overnight.joint_store import OvernightJointStore
    from finscope_market_data.overnight.joint_dataset import JointProfile, PROTOCOL as JOINT_PROTOCOL
    from finscope_market_data.overnight.tail_ranking import PROTOCOL
    models = OvernightJointStore(OvernightStore(tmp_path / 'ranking.db'))
    ranking = {'protocol': PROTOCOL, 'model': {'ranker': 'private coefficients'}, 'audit': {
        'trainingThrough': '2026-11-01T15:00:00', 'calibrationThrough': '2026-11-03T15:00:00',
        'testThrough': '2026-11-04T15:00:00'}}
    artifact = {'protocol': JOINT_PROTOCOL, 'profile': JointProfile('TAIL_ENTRY', '14:30', 20).dump(),
        'createdAt': '2026-11-05T10:00:00', 'labelsThrough': '2026-11-04T15:00:00',
        'targets': {}, 'tailRanking': ranking}
    published = models.publish(artifact, [])
    assert models.latest(artifact['profile']['key'], datetime(2026, 11, 5, 9)) is None
    assert models.latest(artifact['profile']['key'], datetime(2026, 11, 5, 11)) == published
    assert 'model' not in models.summaries(datetime(2026, 11, 5, 11))[0]['tailRanking']
    ranking['audit']['testThrough'] = '2026-11-06T15:00:00'
    with pytest.raises(ValueError, match='标签时点'):
        models.publish(artifact, [])
