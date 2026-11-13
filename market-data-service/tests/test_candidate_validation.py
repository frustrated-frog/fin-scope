from copy import deepcopy
from datetime import datetime
from types import SimpleNamespace

import pytest

from finscope_market_data.overnight.candidate_validation import paired_ranks, summarize_candidate_forward
from finscope_market_data.overnight.up_selection import PROTOCOL
from test_overnight_market_research import forecast, reports
from test_overnight_error_audit import scan


def values():
    rows = reports()
    for row in rows:
        row['closeDirection']['rollingSelectionModels'] = [forecast('ROLLING', protocol=PROTOCOL,
            featureFingerprint='features', modelFitThrough='2026-09-18T15:00:00', momentumScore=.01,
            selection={'reason': 'NO_CANDIDATE_ADVANTAGE', 'selectionThrough': '2026-09-18T15:00:00'})]
    return rows


def audit(rows, scans=None):
    meta = SimpleNamespace(iter_jobs=lambda phase: ([scan()] if scans is None else scans) if phase == 'DISCOVER' else [])
    return summarize_candidate_forward(SimpleNamespace(iter_history=lambda: iter(rows)), meta, datetime(2026, 9, 23))


def test_new_protocol_starts_when_issued_and_never_treats_legacy_absence_as_failure():
    assert audit(reports())['groups'] == []
    group = audit(values(), scans=[scan('2026-09-18'), scan()])['groups'][0]
    assert group['scheduledCount'] == group['pairedCount'] == 3 and group['scheduledDays'] == 1
    assert group['ranking']['matched']['selectedDays'] == 0
    assert group['direction']['ROLLING']['upPrecision'] is None


@pytest.mark.parametrize('change', [
    {'modelFitThrough': '2026-09-21T15:00:00'}, {'momentumScore': float('nan')},
    {'referencePrice': 11}, {'protocol': 'legacy'}, {'upProbability': float('nan')},
    {'selection': {'reason': 'CANDIDATE_ADVANTAGE', 'selectionThrough': '2026-09-21T15:00:00'}},
])
def test_invalid_candidate_retains_denominator_but_cannot_improve_complete_day(change):
    rows = values()
    rows[0]['closeDirection']['rollingSelectionModels'][0].update(change)
    group = audit(rows)['groups'][0]
    assert group['scheduledCount'] == 3 and group['pairedCount'] == 2
    assert group['dayCount'] == 0 and group['ranking']['forced']['precision']['ROLLING'] is None


def test_a_complete_cohort_cannot_mix_models_trained_at_different_times():
    rows = values()
    rows[0]['closeDirection']['rollingSelectionModels'][0]['artifactId'] = 'another-model'
    group = audit(rows)['groups'][0]
    assert group['scheduledCount'] == 3 and group['pairedCount'] == 0
    assert group['dayCount'] == 0 and group['missing'] == {'MIXED_MODEL_ARTIFACTS': 3}


def test_candidate_only_forecast_requires_its_own_past_training_audit():
    from finscope_market_data.overnight.candidate_training import PROTOCOL as TRAINING_PROTOCOL, RECIPE
    rows = values()
    for row in rows:
        row['closeDirection']['rollingSelectionModels'][0].update(selectedModel=RECIPE, candidateTraining={
            'protocol': TRAINING_PROTOCOL, 'status': 'READY', 'trainingDays': 5, 'trainingRows': 30,
            'trainingThrough': '2026-09-18T15:00:00'})
    assert audit(rows)['groups'][0]['dayCount'] == 1
    rows[0]['closeDirection']['rollingSelectionModels'][0]['candidateTraining']['trainingThrough'] = '2099-01-01'
    assert audit(rows)['groups'][0]['dayCount'] == 0


def test_original_is_preserved_and_equal_count_ties_do_not_create_stock_skill():
    rows = values()
    late = deepcopy(rows[0])
    late.update(id='later', generatedAt='2026-09-21T14:32:00')
    late['closeDirection']['rollingSelectionModels'][0]['upProbability'] = .99
    assert audit([late, *rows]) == audit(rows)
    ranked = [{'signalDate': '2026-09-21', 'instrumentCode': str(i), 'probability': .8 if i < 2 else .4,
               'live': .5, 'momentumScore': -i, 'actualUp': i < 2} for i in range(6)]
    result = paired_ranks(ranked)
    assert result['matched']['precision']['ROLLING'] == 1
    assert result['matched']['precision']['INCUMBENT'] == pytest.approx(1/3)
    assert result['matched']['precision']['UNIFORM'] == pytest.approx(1/3)
    assert result['forced']['precision']['ROLLING'] == pytest.approx(2/3)
