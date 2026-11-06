from copy import deepcopy
import json

import pytest

from test_overnight_direction import direction_rows
from finscope_market_data.overnight.direction_candidates import candidate_probabilities
from finscope_market_data.overnight.direction_learning import fit_direction_before, direction_prediction
from finscope_market_data.overnight.fresh_direction import fresh_refit, fit_fresh_direction, rolling_calibration


def test_latest_matured_labels_train_parameters_without_changing_legacy(direction_rows):
    through = '2026-07-01T12:00:00'
    legacy = fit_direction_before(direction_rows, through)
    model, audit = fresh_refit(direction_rows, through, legacy['model']['selected'])
    changed = deepcopy(direction_rows)
    for row in changed[-20 * 20:]:
        row['actualReturn'] *= -1
    second, _ = fresh_refit(changed, through, legacy['model']['selected'])
    old_again = fit_direction_before(changed, through)
    assert old_again['model']['shape'] == legacy['model']['shape']
    assert model['shape'] != second['shape']
    assert audit['trainingThrough'] == max(r['exitAt'] for r in direction_rows)
    assert audit['trainingThrough'] > legacy['audit']['selectionThrough']


def test_fresh_refit_does_not_train_pending_or_ineligible_labels(direction_rows):
    through = '2026-05-01T14:30:00'
    first = fresh_refit(direction_rows, through, 'SHAPE_LOGISTIC')
    changed = deepcopy(direction_rows)
    for row in changed:
        if row['exitAt'] >= through:
            row['actualReturn'] *= -10
            row['features'] = [999.] * len(row['features'])
    assert fresh_refit(changed, through, 'SHAPE_LOGISTIC') == first
    blocked = {**changed[0], 'trainingEligible': False, 'instrumentCode': '600999.SH',
               'features': [888.] * len(changed[0]['features'])}
    model, _ = fresh_refit(changed + [blocked], through, 'SHAPE_LOGISTIC')
    assert model == first[0]


@pytest.fixture(scope='module')
def fresh_fit(direction_rows):
    return fit_fresh_direction(direction_rows, '2026-07-01T12:00:00', '14:30', include_checks=True)


def test_real_fresh_pipeline_serializes_and_calibrates_only_issued_past_predictions(fresh_fit, direction_rows):
    fitted = json.loads(json.dumps(fresh_fit))
    assert fitted['audit']['historical']['dayCount'] == 20
    assert not fitted['audit']['historical']['eligible']
    for fold in fitted['audit']['folds']:
        assert fold['trainingThrough'] < fold['predictionAt']
        assert fold['trainingThrough'] > fold['legacyTrainingThrough']
        if fold['calibrationThrough']:
            assert fold['calibrationThrough'] < fold['predictionAt']
            assert fold['calibrationPredictionThrough'] < fold['calibrationThrough']
    assert fitted['audit']['calibrationDays'] == 20
    forecast = direction_prediction(fitted, direction_rows[-1]['features'])
    assert forecast['probabilitySource'] == 'RAW'
    assert 0 <= forecast['upProbability'] <= 1
    assert forecast['upProbability'] == pytest.approx(candidate_probabilities(
        fitted['model'], [direction_rows[-1]['features']])[fitted['model']['selected']][0])


def test_rolling_calibration_rejects_in_sample_or_future_results(fresh_fit):
    checks = fresh_fit['checks']
    through = checks[-20]['predictionAt']
    original = rolling_calibration(checks, through)
    changed = deepcopy(checks)
    for row in changed:
        if row['exitAt'] >= through:
            row.update(raw=.999, actualReturn=100.)
    assert rolling_calibration(changed, through) == original
    invalid = {**checks[0], 'predictionAt': checks[0]['exitAt'], 'raw': .999}
    assert rolling_calibration(checks + [invalid], through) == original


def test_fresh_research_keeps_insufficient_history_explicit(direction_rows):
    assert fit_fresh_direction(direction_rows[:120 * 20], '2026-07-01T12:00:00', '14:30') is None
