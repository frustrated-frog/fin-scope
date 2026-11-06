from copy import deepcopy
from datetime import datetime
from types import SimpleNamespace

import pytest

from test_fresh_research import fresh_reports
from test_overnight_direction import direction_rows
from test_overnight_error_audit import scan
from finscope_market_data.overnight.direction_candidates import INDEX_ONLY_RECIPE
from finscope_market_data.overnight.fresh_direction import fresh_refit
from finscope_market_data.overnight.selection_evaluation import PROTOCOL
from finscope_market_data.overnight.selection_validation import summarize_selection_forward
from finscope_market_data.overnight.selection_research import train_index_only


def values():
    records = fresh_reports()
    for r in records:
        r['closeDirection']['stockSelectionModels'] = [{**r['closeDirection']['freshModels'][2],
            'protocol': PROTOCOL, 'indexOnlyProbability': .4, 'indexOnlyTrainingThrough': '2026-09-18T15:00:00'}]
    return records


def test_unequal_comparison_sizes_fail_before_training():
    with pytest.raises(ValueError, match='数量不一致'):
        train_index_only([], '2026-11-06', '14:30', [{}], [])


def audit(records):
    meta = SimpleNamespace(iter_jobs=lambda phase: iter([scan()] if phase == 'DISCOVER' else []))
    return summarize_selection_forward(SimpleNamespace(iter_history=lambda: iter(records)), meta, datetime(2026, 9, 23))['groups'][0]


def test_forward_comparison_keeps_low_probability_dates_and_original_predictions():
    records = values()
    result = audit(records)
    assert result['pairedCount'] == result['scheduledCount'] == 3
    assert result['metrics']['accuracy'] == 1
    selection = result['sameDaySelection']
    assert selection['dayCount'] == selection['noCandidateDays'] == 1
    assert selection['forcedTopK']['precision']['INDEX'] == 0
    assert selection['matchedCandidates']['precision']['INDEX'] is None
    later = deepcopy(records[0])
    later.update(id='later', generatedAt='2026-09-21T14:32:00')
    later['closeDirection']['stockSelectionModels'][0]['upProbability'] = .9
    assert audit([later, *records]) == result


@pytest.mark.parametrize('change,reason', [
    ({'indexOnlyProbability': None}, 'MISSING_INDEX_ONLY'),
    ({'indexOnlyTrainingThrough': '2026-09-21T15:00:00'}, 'INVALID_EVIDENCE'),
    ({'protocol': 'older'}, 'MISSING_MODEL'),
])
def test_missing_evidence_excludes_whole_day_without_shrinking_denominator(change, reason):
    records = values()
    records[0]['closeDirection']['stockSelectionModels'][0].update(change)
    result = audit(records)
    assert result['scheduledCount'] == 3 and result['pairedCount'] == 2
    assert result['missing'] == {reason: 1}
    assert result['sameDaySelection']['dayCount'] == 0


@pytest.mark.parametrize('change', [{'indexOnlyProbability': .41}, {'artifactId': 'other'}, {'indexHistoryFingerprint': 'other'}])
def test_mixed_day_models_are_not_silently_averaged(change):
    records = values()
    records[0]['closeDirection']['stockSelectionModels'][0].update(change)
    result = audit(records)
    assert result['pairedCount'] == 0 and result['scheduledCount'] == 3
    assert result['missing'] == {'INCONSISTENT_PAIRED_MODEL': 3}


def test_index_only_recipe_has_exact_schema_and_ignores_future_outcomes(direction_rows):
    rows = [{**r, 'features': r['features'][:9]} for r in direction_rows]
    through = '2026-05-01T14:30:00'
    result = fresh_refit(rows, through, 'SHAPE_ENSEMBLE', recipe=INDEX_ONLY_RECIPE)
    changed = [{**r, 'actualReturn': -r['actualReturn']} if r['exitAt'] >= through else r for r in rows]
    assert fresh_refit(changed, through, 'SHAPE_ENSEMBLE', recipe=INDEX_ONLY_RECIPE) == result
    with pytest.raises(ValueError, match='特征宽度'):
        fresh_refit(rows, through, 'SHAPE_ENSEMBLE')
    with pytest.raises(ValueError, match='特征宽度'):
        fresh_refit(direction_rows, through, 'SHAPE_ENSEMBLE', recipe=INDEX_ONLY_RECIPE)
