import pytest
from finscope_market_data.forecast.direction_evaluation import evaluate_direction


def test_date_weighting_is_invariant_to_repeating_an_entire_cross_section():
    first = evaluate_direction([.8,.2,.8,.2], [1,0,0,1], ['a','a','b','b'], {'PRIOR':[.5]*4})
    repeated = evaluate_direction([.8,.2,.8,.2,.8,.2], [1,0,1,0,0,1], ['a','a','a','a','b','b'], {'PRIOR':[.5]*6})
    assert first['accuracy'] == repeated['accuracy'] == .5
    assert first['brierScore'] == pytest.approx(repeated['brierScore'])


def test_always_up_does_not_look_balanced_or_beat_a_better_prior():
    result = evaluate_direction([.9]*100, [1]*80+[0]*20, [str(i) for i in range(100)], {'PRIOR':[.8]*100})
    assert result['accuracy'] == pytest.approx(.8)
    assert result['balancedAccuracy'] == .5
    assert result['eligible'] is False
    assert result['highConfidence']['coverage'] == pytest.approx(1)


def test_strong_signal_is_measured_against_every_baseline_with_date_blocks():
    y = [i % 2 for i in range(120)]
    result = evaluate_direction([.9 if v else .1 for v in y], y, [str(i).zfill(3) for i in range(120)],
                                {'PRIOR':[.5]*120, 'MOMENTUM':[.4 if v else .6 for v in y]})
    assert result['eligible']
    assert result['dayCount'] == 120
    assert all(v['brierDifferenceUpper'] < 0 for v in result['comparisons'].values())
    assert result['highConfidence']['accuracy'] == 1
    with pytest.raises(ValueError):
        evaluate_direction([float('nan')], [1], ['a'], {'PRIOR':[.5]})
