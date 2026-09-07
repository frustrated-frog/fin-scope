import pytest
from finscope_market_data.forecast.industry_features import IndustryMembership, industry_features


def test_membership_is_point_in_time_and_peer_average_excludes_self():
    features = {'a': (.1, .2), 'b': (-.1, -.2), 'c': (.1, .4)}
    record = IndustryMembership('industry', '2025-01-02', ('a', 'b', 'c'))
    assert industry_features(features, [record], '2025-01-01')['a'] == (0., 0., 0., 0.)
    result = industry_features(features, [record], '2025-01-02')
    assert result['a'] == (.1, .5, .1, 1.)
    assert result['b'][0] == pytest.approx(.3)


def test_future_membership_update_cannot_change_past_features():
    features = {'a': (.1, .2), 'b': (-.1, -.2), 'c': (.1, .4)}
    before = IndustryMembership('industry', '2025-01-02', ('a', 'b', 'c'))
    after = IndustryMembership('industry', '2025-02-02', ('b', 'c'))
    assert industry_features(features, [before], '2025-01-03') == industry_features(features, [before, after], '2025-01-03')


def test_reobserving_unchanged_members_does_not_erase_first_available_date(tmp_path):
    import json
    from finscope_market_data.forecast.industry_features import load_industry_memberships
    source, archive = tmp_path / 'current.json', tmp_path / 'history.json'
    item = {'quality_status': 'COMPLETE', 'retrieved_at': '2025-01-01T18:00:00',
            'snapshot_at': '2025-01-01T18:00:00', 'values': [['a', 'SZ', 'A'], ['b', 'SZ', 'B']]}
    source.write_text(json.dumps({'sectors': {'881001': item}}))
    original = load_industry_memberships(source, archive)
    item.update(retrieved_at='2025-02-01T18:00:00', snapshot_at='2025-02-01T18:00:00')
    source.write_text(json.dumps({'sectors': {'881001': item}}))
    assert load_industry_memberships(source, archive) == original
    item['values'].append(['c', 'SZ', 'C'])
    source.write_text(json.dumps({'sectors': {'881001': item}}))
    updated = load_industry_memberships(source, archive)
    assert len(updated) == 2 and updated[0] == original[0]
