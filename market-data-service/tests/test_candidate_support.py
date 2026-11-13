from copy import deepcopy

import pytest

from finscope_market_data.overnight.candidate_support import candidate_support
from finscope_market_data.overnight.direction_dataset import FEATURES


def row(day, code, value, **flags):
    values = [0.] * len(FEATURES)
    values[0] = value
    return {'signalDate': day, 'instrumentCode': code, 'exitAt': f'{day}T15:00:00', 'features': values, **flags}


def test_support_excludes_future_incomplete_and_action_affected_pool_rows_without_mutating_inputs():
    rows = [row('2026-11-10', 'base', 0.), row('2026-11-10', 'action', 999., trainingEligible=False),
            row('2026-11-10', 'candidate', .1, candidateOnly=True), row('2026-11-11', 'future', 1000.),
            row('2026-11-09', 'incomplete', 1000.)]
    original = deepcopy(rows)
    days = [{'signalDate': day, 'targetAt': f'{day}T15:00:00', 'complete': day != '2026-11-09'}
            for day in ['2026-11-09', '2026-11-10', '2026-11-11']]
    cohorts = {('2026-11-10', '14:30'): {'candidates': {'candidate': {}}}}
    result = candidate_support(rows, cohorts, days, '14:30', '2026-11-11T14:30:00')
    assert result['status'] == 'SHIFT_DETECTED'
    assert result['poolRows'] == result['candidateRows'] == result['outsidePoolRows'] == 1
    assert result['features'][0]['poolMedian'] == 0 and result['features'][0]['candidateMedian'] == .1
    assert rows == original


def test_support_weights_dates_equally_and_does_not_turn_unknown_into_zero():
    rows, days, cohorts = [], [], {}
    for day, size, value in [('2026-11-09', 10, 0.), ('2026-11-10', 1, .2)]:
        rows.append(row(day, 'base', 0.))
        rows.extend(row(day, str(i), value, candidateOnly=True) for i in range(size))
        days.append({'signalDate': day, 'targetAt': f'{day}T15:00:00', 'complete': True})
        cohorts[(day, '14:30')] = {'candidates': {str(i): {} for i in range(size)}}
    result = candidate_support(rows, cohorts, days, '14:30', '2026-11-11')
    assert result['features'][0]['outsideCentralRangeRate'] == pytest.approx(.5)
    empty = candidate_support([], cohorts, days, '14:30', '2026-11-11')
    assert empty['status'] == 'INSUFFICIENT_DATA' and not empty['features']
    assert empty['unpairedDays'] == 2
