from dataclasses import replace

import numpy as np
import pytest

from test_joint_training import dataset
from finscope_market_data.forecast.conditional_experiment import select_conditional_method


def selection_fixture(base_counts, **challengers):
    data = dataset(days=3, stocks=200)
    rows = tuple(replace(r, sample=replace(r.sample, net_return=.01 if i % 2 else -.01))
                 for i, r in enumerate(data.rows))
    y = np.array([r.sample.positive for r in rows])
    probabilities = {}
    for name, counts in {'BASE': base_counts, **challengers}.items():
        correct = np.concatenate([np.arange(200) < count for count in counts])
        predicted = np.where(correct, y, 1-y)
        probabilities[name] = np.where(predicted, .51, .49)
    return dict(rows=rows, probabilities=probabilities)


def test_two_small_period_wins_cannot_overrule_aggregate_loss():
    result = selection_fixture([112, 112, 112], MARKET_0_1=[114, 114, 102])
    selected = select_conditional_method(result, '2024-01-01', '2024-02-01')
    assert selected['selected'] == 'BASE'
    assert selected['candidates']['BASE']['eligible']
    assert not selected['passed']
    assert 'NO_ACCURACY_GAIN' in selected['candidates']['MARKET_0_1']['rejectionReasons']


def test_complex_correction_must_also_improve_over_intercept():
    result = selection_fixture([112, 112, 112], **{'INTERCEPT_0.1': [120]*3, 'JOINT_0.1': [118]*3})
    selected = select_conditional_method(result, '2024-01-01', '2024-02-01')
    assert selected['selected'] == 'INTERCEPT_0.1'
    assert not selected['candidates']['JOINT_0.1']['eligible']
    assert 'NO_INCREMENT_OVER_INTERCEPT' in selected['candidates']['JOINT_0.1']['rejectionReasons']


def test_incumbent_is_legal_even_when_balanced_accuracy_is_below_half():
    selected = select_conditional_method(selection_fixture([80]*3), '2024-01-01', '2024-02-01')
    assert selected['selected'] == 'BASE'
    assert selected['candidates']['BASE']['eligible']
    assert selected['reason'] == 'NO_QUALIFIED_CHALLENGER'


def test_selection_rejects_too_few_dates_with_clear_error():
    with pytest.raises(ValueError, match='至少.*3'):
        select_conditional_method(selection_fixture([112]*3), '2024-01-01', '2024-01-02')
