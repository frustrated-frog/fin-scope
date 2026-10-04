from datetime import date

import pytest

from finscope_market_data.overnight.engine import group_bars, features, expected_times
from finscope_market_data.overnight.session_features import session_features, FEATURE_CODES
from test_overnight import history


def test_session_features_distinguish_gap_and_intraday_without_using_post_cutoff_bars():
    grouped = group_bars(history(12))
    days = sorted(grouped)
    day, previous, before = days[-2], grouped[days[-3]], grouped[days[-4]]
    result = session_features(grouped, day, '14:30')
    assert len(result) == 8 + len(FEATURE_CODES)
    assert result[:8] == features(grouped[day], '14:30')
    assert result[8] == pytest.approx(grouped[day]['09:35'].open / previous['15:00'].close - 1)
    assert result[9] == pytest.approx(previous['09:35'].open / before['15:00'].close - 1)
    assert result[10] == pytest.approx(previous['15:00'].close / previous['09:35'].open - 1)
    changed = {key: dict(value) for key, value in grouped.items()}
    for value, bars in changed.items():
        for stamp, bar in list(bars.items()):
            if value > day or value == day and stamp > '14:30':
                bars[stamp] = bar.model_copy(update={'open': 1000, 'close': 2000, 'amount': 1e10})
    assert session_features(changed, day, '14:30') == result
    # Relative activity compares the same clock window on all five earlier days.
    for stamp in expected_times('15:00'):
        if stamp > '14:30':
            bar = changed[days[-3]][stamp]
            changed[days[-3]][stamp] = bar.model_copy(update={'amount': 1e10})
    assert session_features(changed, day, '14:30')[-1] == result[-1]


def test_missing_sessions_are_not_compressed_into_previous_trading_days():
    grouped = group_bars(history(12))
    days = sorted(grouped)
    assert session_features(grouped, days[4], '14:30') is None
    assert session_features(grouped, date(2027, 1, 5), '14:30') is None
    del grouped[days[-3]]
    assert session_features(grouped, days[-1], '14:30') is None


def test_partial_previous_session_does_not_become_a_complete_history_feature():
    grouped = group_bars(history(12))
    days = sorted(grouped)
    del grouped[days[-2]]['13:30']
    assert session_features(grouped, days[-1], '14:30') is None
