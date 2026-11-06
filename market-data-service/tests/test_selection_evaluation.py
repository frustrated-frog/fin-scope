from copy import deepcopy

import pytest

from finscope_market_data.overnight.selection_evaluation import ARMS, paired_selection


def rows():
    return [{'signalDate': f'2026-11-0{day}', 'instrumentCode': f'60000{i}.SH', 'actualUp': i >= 2,
             'scores': {'INDEX': (.3 if day == 2 else .9) - i * .01,
                        'STOCK_ONLY': .2 + i * .1, 'INDEX_ONLY': .6 if day == 1 else .4}}
            for day in (1, 2) for i in range(5)]


def test_equal_counts_ties_and_abstention_days_cannot_disappear():
    result = paired_selection(rows())
    forced, matched = result['forcedTopK'], result['matchedCandidates']
    assert result['dayCount'] == 2 and result['noCandidateDays'] == 1
    assert forced['selectedDays'] == 2 and forced['selectionCountPerArm'] == 6
    assert matched['selectedDays'] == 1 and matched['dayCoverage'] == .5
    assert matched['selectionCountPerArm'] == 3
    assert forced['precision'] == pytest.approx({'INDEX': 1/3, 'STOCK_ONLY': 1., 'INDEX_ONLY': .6})
    assert result['days'][1]['matched'] == {arm: None for arm in ARMS}
    assert result['days'][1]['forced']['INDEX_ONLY'] == pytest.approx(.6)
    assert not result['eligible']


def test_order_and_equal_score_codes_cannot_change_selection():
    values = rows()
    result = paired_selection(values)
    assert paired_selection(list(reversed(values))) == result
    for row in values:
        row['scores']['INDEX'] = .7
    result = paired_selection(values)
    assert result['forcedTopK']['precision']['INDEX'] == pytest.approx(.6)
    assert all(row['forced']['INDEX'] == pytest.approx(.6) for row in result['days'])


@pytest.mark.parametrize('change', ['duplicate', 'missing', 'nan', 'label', 'index_identity'])
def test_rejects_nonpaired_or_invalid_inputs(change):
    values = deepcopy(rows())
    if change == 'duplicate':
        values.append(values[0])
    elif change == 'missing':
        del values[0]['scores']['STOCK_ONLY']
    elif change == 'nan':
        values[0]['scores']['INDEX'] = float('nan')
    elif change == 'label':
        values[0]['actualUp'] = 1
    else:
        values[0]['scores']['INDEX_ONLY'] = .9
    with pytest.raises((ValueError, KeyError)):
        paired_selection(values)


def test_small_pool_and_no_candidates_keep_explicit_denominators():
    values = [r for r in rows() if r['signalDate'].endswith('2')][:2]
    result = paired_selection(values)
    assert result['forcedTopK']['selectionCountPerArm'] == 2
    assert result['matchedCandidates']['selectionCountPerArm'] == 0
    assert result['matchedCandidates']['precision'] == {arm: None for arm in ARMS}
    assert result['noCandidateDays'] == result['dayCount'] == 1
    assert paired_selection([])['dayCount'] == 0
