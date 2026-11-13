from copy import deepcopy
from datetime import date, timedelta
import json

import numpy as np
import pytest

from finscope_market_data.overnight.candidate_training import fit_candidate_training, candidate_training_probability
from finscope_market_data.overnight.direction_dataset import FEATURES


def panel(count=12):
    rows, cohorts, days = [], {}, []
    for i in range(count):
        day = date(2026, 10, 1) + timedelta(days=i)
        target = f'{day + timedelta(days=1)}T15:00:00'
        codes = [f'candidate-{j}' for j in range(6)]
        cohorts[(str(day), '14:30')] = {'snapshotAt': f'{day}T14:20:00', 'candidates': dict.fromkeys(codes, {})}
        days.append({'signalDate': str(day), 'targetAt': target, 'complete': True, 'expectedCount': 6})
        for j, code in enumerate(codes):
            x = [0.] * len(FEATURES)
            x[FEATURES.index('INTRADAY_RETURN')] = .02 * (j-2)
            rows.append({'signalDate': str(day), 'instrumentCode': code, 'features': x,
                'actualReturn': .02 if j >= 3 else -.02, 'exitAt': target,
                'candidateOnly': True, 'trainingEligible': True, 'actionsVerified': True})
    return rows, cohorts, {'days': days}


def test_outside_pool_candidates_actually_train_and_nonmembers_cannot_dominate():
    rows, cohorts, readiness = panel()
    model, audit = fit_candidate_training(rows, cohorts, readiness, '2026-11-01', '14:30')
    assert audit['status'] == 'READY' and audit['outsidePoolRows'] == audit['trainingRows'] == 72
    p = candidate_training_probability(json.loads(json.dumps(model)), [r['features'] for r in rows[:6]])
    assert p[0] < .5 < p[-1]
    nonmembers = [{**r, 'instrumentCode': f'pool-{i}', 'candidateOnly': False, 'actualReturn': -r['actualReturn']}
                  for i, r in enumerate(rows*10)]
    assert fit_candidate_training(rows+nonmembers, cohorts, readiness, '2026-11-01', '14:30') == (model, audit)


def test_current_and_previous_signal_day_outcomes_never_train_the_current_decision():
    rows, cohorts, readiness = panel()
    before = deepcopy(rows)
    through = '2026-10-10T14:30:00'
    fitted = fit_candidate_training(rows, cohorts, readiness, through, '14:30')
    assert fitted[1]['trainingThrough'] == '2026-10-09T15:00:00'
    for r in rows:
        if r['exitAt'] >= through:
            r['actualReturn'] = -r['actualReturn']
            r['features'] = [99.] * len(FEATURES)
    assert fit_candidate_training(rows, cohorts, readiness, through, '14:30') == fitted
    assert fitted[1]['trainingRows'] == 48
    assert before[0] == rows[0]


def test_incomplete_and_affected_fit_rows_are_excluded_but_inputs_preserved():
    rows, cohorts, readiness = panel()
    readiness['days'][0]['complete'] = False
    rows[6]['trainingEligible'] = False
    rows[7]['actionsVerified'] = False
    original = deepcopy(rows)
    model, audit = fit_candidate_training(rows, cohorts, readiness, '2026-11-01', '14:30')
    assert model and audit['trainingRows'] == 65 and audit['excludedRows'] == audit['unverifiedActionRows'] == 1
    assert audit['incompleteDays'] == 1 and rows == original


def test_sparse_one_class_and_late_membership_cannot_create_a_valid_model():
    rows, cohorts, readiness = panel(4)
    model, audit = fit_candidate_training(rows, cohorts, readiness, '2026-11-01', '14:30')
    assert model is None and audit['reason'] == 'INSUFFICIENT_CANDIDATE_HISTORY'
    rows, cohorts, readiness = panel()
    for r in rows:
        r['actualReturn'] = .01
    assert fit_candidate_training(rows, cohorts, readiness, '2026-11-01', '14:30')[1]['reason'] == 'INSUFFICIENT_CLASS_DAYS'
    cohorts[('2026-10-01', '14:30')]['snapshotAt'] = '2026-10-01T14:31:00'
    with pytest.raises(ValueError, match='判断前冻结'):
        fit_candidate_training(rows, cohorts, readiness, '2026-11-01', '14:30')


def test_scaling_comes_from_candidates_and_probability_uses_raw_features():
    from finscope_market_data.overnight.direction_candidates import candidate_probabilities, CANDIDATES
    from finscope_market_data.overnight.candidate_training import RECIPE
    rows, cohorts, readiness = panel()
    model, _ = fit_candidate_training(rows, cohorts, readiness, '2026-11-01', '14:30')
    width = len(FEATURES)
    public = {'mean': [0.]*width, 'scale': [1.]*width, 'clipLow': [0.]*width, 'clipHigh': [0.]*width,
              'prior': .4, 'actualCandidate': model}
    predicted = candidate_probabilities(public, [r['features'] for r in rows[:6]])
    assert all(np.all(predicted[key] == .4) for key in CANDIDATES)
    assert predicted[RECIPE][0] < .5 < predicted[RECIPE][-1]


def test_development_metrics_pair_ready_dates_and_do_not_fake_small_sample_intervals():
    from finscope_market_data.overnight.candidate_training_evaluation import evaluate_candidate_training
    checks = [{'signalDate': f'2026-10-{i:02}', 'instrumentCode': str(j), 'actualUp': j == 0,
               'candidateReady': i > 1, 'probability': .7 if j == 0 else .3,
               'poolProbability': .4, 'poolShapeProbability': .4, 'poolPrior': .4,
               'candidatePrior': .5, 'momentum': j} for i in range(1, 7) for j in range(3)]
    readiness = {'days': [{'targetAt': f'2026-10-{i+1:02}T15:00:00'} for i in range(1, 8)]}
    audit = evaluate_candidate_training(checks, readiness, '2026-11-01')
    assert audit['dayCount'] == 5 and audit['sampleCount'] == 15 and audit['warmupDays'] == 1
    assert audit['plannedDays'] == 7 and audit['arms']['CANDIDATE']['accuracy'] == pytest.approx(1.)
    assert not audit['eligible'] and audit['uncertaintyStatus'] == 'INSUFFICIENT_DAYS'
    assert audit['comparisons']['POOL_ROLLING']['accuracyDifferenceLower'] is None
