import pytest
from finscope_market_data.forecast.market_state_features import market_state_features, MARKET_STATE_FEATURE_CODES


def row(ret5, ret20, gap=0., body=0., activity=0.):
    return (ret5, ret20, 0., 0., 0., .02, activity, gap, body)


def test_state_uses_observed_cross_section_not_labels_or_membership():
    values = {'a':row(.02,.1,.01,.01), 'b':row(-.02,-.1,-.01,-.01)}
    result = market_state_features(values)
    assert len(result['a']) == len(MARKET_STATE_FEATURE_CODES)
    assert result['a'][0] == .5
    assert result['a'][1] == .5
    assert result['a'][2] == .5
    assert result['a'][3] == pytest.approx(.0001)
    assert result == market_state_features(dict(reversed(list(values.items()))))
    assert result['a'][-1] != result['b'][-1]
