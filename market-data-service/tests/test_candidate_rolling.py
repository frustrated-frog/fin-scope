from copy import deepcopy
from datetime import date, datetime, timedelta
from types import SimpleNamespace
import json

import pytest

from finscope_market_data.overnight.candidate_readiness import build_actual_candidate_panel
from finscope_market_data.overnight.candidate_rolling import fit_rolling_candidates, select_candidate_recipe
from finscope_market_data.overnight.direction_candidates import CANDIDATES
from finscope_market_data.overnight.direction_learning import direction_prediction
from test_overnight_direction import direction_rows


def candidates(rows, days=24):
    selected = sorted({r['signalDate'] for r in rows})[-days:]
    cohorts, readiness = {}, {'days': []}
    for day in selected:
        batch = [r for r in rows if r['signalDate'] == day]
        cohorts[(day, '14:30')] = {'snapshotAt': f'{day}T14:20:00',
                                  'candidates': {r['instrumentCode']: {} for r in batch}}
        readiness['days'].append({'signalDate': day, 'targetAt': batch[0]['exitAt'],
            'complete': True, 'expectedCount': len(batch), 'availableCount': len(batch)})
    return cohorts, readiness


def test_candidate_readiness_keeps_missing_and_pending_in_plan_without_optional_context():
    from test_overnight import history
    from finscope_market_data.forecast.trading_calendar import next_session
    bars = history(12)
    dates = sorted({str(b.ended_at.date()) for b in bars})
    day, pending = dates[-2], dates[-1]
    cohorts = {(d, '14:30'): {'snapshotAt': f'{d}T14:20:00',
        'candidates': {'600000.SH': {}, '600001.SH': {}}} for d in (day, pending)}
    now = datetime.fromisoformat(f'{pending}T18:00:00')
    store = SimpleNamespace(bars=lambda code, through: bars if code == '600000.SH' else [])
    rows, audit = build_actual_candidate_panel([], store, cohorts, '14:30', now)
    assert len(rows) == 1 and rows[0]['candidateOnly']
    assert rows[0]['exitAt'] == f'{next_session(date.fromisoformat(day))}T15:00:00'
    assert audit['reasonCounts'] == {'AVAILABLE': 1, 'MISSING_DECISION_BARS': 1, 'NOT_DUE': 2}
    assert audit['completeDays'] == 0
    assert audit['days'][0]['expectedCount'] == 2


def strong_checks():
    checks, days = [], []
    for i in range(20):
        day = date(2026, 10, 1) + timedelta(days=i)
        exit_at = f'{day + timedelta(days=1)}T15:00:00'
        days.append({'signalDate': str(day), 'targetAt': exit_at, 'complete': True, 'expectedCount': 10})
        for j in range(10):
            values = {name: .3 for name in CANDIDATES}
            values['SHAPE_LOGISTIC'] = .8 if j < 3 else .2
            checks.append({'signalDate': str(day), 'instrumentCode': str(j), 'actualUp': j < 3,
                'probabilities': values, 'momentum': -int(j < 3), 'exitAt': exit_at})
    return checks, {'days': days}


def test_recent_complete_candidates_select_discrimination_and_preserve_missing_days():
    checks, readiness = strong_checks()
    selected, audit = select_candidate_recipe(checks, readiness, '2026-11-01')
    assert selected == 'SHAPE_LOGISTIC'
    assert audit['selectionDays'] == 20 and audit['candidateCoverage'] == 1
    assert audit['candidates'][selected]['direction']['upRecall'] == 1
    # Incomplete hard days cannot be silently removed from the denominator.
    partial = checks[:-90]
    selected, audit = select_candidate_recipe(partial, readiness, '2026-11-01')
    assert selected == 'PRIOR' and audit['candidateCoverage'] == pytest.approx(.55)
    assert audit['reason'] == 'INSUFFICIENT_COMPLETE_CANDIDATES'


def test_selector_uses_only_matured_results_and_has_no_symbol_tie_break_advantage():
    checks, readiness = strong_checks()
    through = '2026-10-15T14:30:00'
    first = select_candidate_recipe(checks, readiness, through)
    changed = deepcopy(checks)
    for row in changed:
        if row['exitAt'] >= through:
            row['actualUp'] = not row['actualUp']
            row['probabilities'] = {name: .99 for name in CANDIDATES}
    assert select_candidate_recipe(changed, readiness, through) == first
    for row in checks:
        row['probabilities'] = {name: .6 for name in CANDIDATES}
    selected, audit = select_candidate_recipe(checks, readiness, '2026-11-01')
    assert selected == 'PRIOR'
    assert audit['candidates']['SHAPE_LOGISTIC']['topK']['lift'] == pytest.approx(0)


def test_nested_refit_uses_latest_labels_and_never_selects_on_its_test_day(direction_rows):
    cohorts, readiness = candidates(direction_rows)
    fitted = fit_rolling_candidates(direction_rows, cohorts, readiness, '2026-07-01', '14:30', include_checks=True)
    assert fitted is not None
    restored = json.loads(json.dumps(fitted, allow_nan=False))
    assert restored['audit']['modelFitThrough'] == max(r['exitAt'] for r in direction_rows)
    assert restored['audit']['selection']['selectionDays'] == 20
    assert restored['audit']['historical']['dayCount'] == 24
    for fold in restored['audit']['folds']:
        assert fold['modelFitThrough'] < fold['predictionAt']
        assert fold['selection']['selectionThrough'] is None or fold['selection']['selectionThrough'] < fold['predictionAt']
    assert restored['audit']['folds'][0]['selected'] == 'PRIOR'
    assert direction_prediction(restored, direction_rows[-1]['features'])['modelFitThrough'] == restored['audit']['modelFitThrough']


def test_invalid_complete_candidate_day_fails_explicitly(direction_rows):
    cohorts, readiness = candidates(direction_rows, days=1)
    cohorts[next(iter(cohorts))]['candidates']['UNKNOWN'] = {}
    with pytest.raises(ValueError, match='完整候选日'):
        fit_rolling_candidates(direction_rows, cohorts, readiness, '2026-07-01', '14:30')
