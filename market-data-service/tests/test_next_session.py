from datetime import date, datetime, timedelta
import math
from dataclasses import replace

import pytest

from finscope_market_data.models import DailyBar, StockSymbol
from finscope_market_data.forecast.next_session import build_next_session_forecast, build_close_samples, _fit_at
from finscope_market_data.forecast.trading_calendar import next_session


def bars(count=800):
    dates = []
    day = date(2026, 9, 4)
    while len(dates) < count:
        if day.weekday() < 5:
            dates.append(day.isoformat())
        day -= timedelta(days=1)
    return [DailyBar(symbol=StockSymbol(code="000001", market="SZ"), trade_date=day,
                    open=100 + i * .01, close=100 + i * .01 + math.sin(i / 7),
                    high=103 + i * .01, low=97 + i * .01, volume=1_000_000,
                    amount=100_000_000 + i * 100, adjustment="QFQ")
            for i, day in enumerate(reversed(dates))]


def test_next_session_uses_exchange_holidays_not_makeup_workdays():
    assert next_session(date(2026, 9, 4)) == date(2026, 9, 7)
    assert next_session(date(2026, 9, 24)) == date(2026, 9, 28)
    assert next_session(date(2026, 9, 30)) == date(2026, 10, 8)
    assert next_session(date(2026, 2, 13)) == date(2026, 2, 24)
    assert next_session(date(2026, 12, 31)) is None


def test_labels_are_next_close_not_next_open_to_following_open():
    data = bars(100)
    samples = build_close_samples(data)
    assert samples[-1].exit_date == data[-1].trade_date
    assert samples[-1].net_return == pytest.approx(data[-1].close / data[-2].close - 1)


def test_current_model_uses_recent_mature_labels_and_only_pretest_calibration():
    samples = build_close_samples(bars())
    fitted = _fit_at(samples[:-60], samples[-60].signal_date)
    assert fitted.training_through < fitted.calibration_start
    assert fitted.calibration_through < samples[-60].signal_date
    current = _fit_at(samples, "2026-09-05")
    assert current.training_through > fitted.training_through
    assert current.calibration_through == "2026-09-04"


def test_real_next_day_report_has_finite_intervals_and_rolling_evidence():
    result = build_next_session_forecast(bars(), now=datetime(2026, 9, 5, 11))
    assert result.target_date == "2026-09-07"
    assert result.label == "NEXT_CLOSE_RETURN"
    assert result.status in {"READY", "WATCH"}
    assert 0 < result.up_probability < 1
    assert result.lower_return <= result.expected_return <= result.upper_return
    assert result.validation_sample_count == 60
    assert 0 <= result.interval_coverage <= 1
    assert result.calibration_through == "2026-09-04"
    assert len(result.data_fingerprint) == 64


@pytest.mark.parametrize("now,status", [(datetime(2026,9,8,16), "STALE_DATA"),
                                        (datetime(2026,9,4,14), "BEFORE_CLOSE")])
def test_does_not_present_stale_or_intraday_bars_as_tomorrow_forecast(now, status):
    result = build_next_session_forecast(bars(), now=now)
    assert result.status == status
    assert result.up_probability is None


@pytest.mark.parametrize("count", [1, 10, 60, 100])
def test_short_history_does_not_invent_probability(count):
    result = build_next_session_forecast(bars(count), now=datetime(2026,9,5,11))
    assert result.status == "INSUFFICIENT_DATA"
    assert result.up_probability is None


def test_future_labels_cannot_change_rolling_fit():
    samples = build_close_samples(bars(500))
    cutoff = samples[-30].signal_date
    original = _fit_at(samples, cutoff)
    changed = _fit_at([replace(s, net_return=-s.net_return + .9) if s.exit_date >= cutoff else s
                       for s in samples], cutoff)
    assert original.predict(samples[-30].features) == pytest.approx(changed.predict(samples[-30].features))


def test_existing_single_stock_report_includes_next_close_prediction_even_when_20d_history_insufficient():
    from finscope_market_data.forecast.service import build_forecast
    report = build_forecast(bars(500), instrument_code="000001.SZ", source_code="TEST", source_family="TEST",
                            quality_status="FRESH_PRIMARY", warnings=[], panel_now=datetime(2026,9,5,11))
    assert report.next_session.target_date == "2026-09-07"
    assert report.next_session.up_probability is not None


@pytest.mark.parametrize('hour,minute,status', [(0, 0, 'INSUFFICIENT_DATA'), (9, 29, 'INSUFFICIENT_DATA'), (9, 30, 'STALE_DATA')])
def test_previous_close_remains_usable_until_target_session_open(hour, minute, status):
    # Friday's close remains usable on Monday before the open, including across midnight/weekend.
    result = build_next_session_forecast(bars(100), now=datetime(2026, 9, 7, hour, minute))
    assert result.target_date == '2026-09-07'
    assert result.status == status


def test_next_session_reports_absolute_direction_benchmarks_and_coverage():
    result = build_next_session_forecast(bars(), now=datetime(2026, 9, 5, 11))
    audit = result.direction_evaluation
    assert audit['task'] == 'NEXT_CLOSE_DIRECTION'
    assert audit['dayCount'] == 60
    assert set(audit['comparisons']) == {'PRIOR','MOMENTUM','LEGACY'}
    assert audit['accuracy'] == pytest.approx(result.accuracy)
    assert (result.status == 'READY') == audit['eligible']
    assert result.model_version == 'local-prediction-v3-consecutive-session-v2'


@pytest.mark.parametrize('signal,outcome,accepted', [
    ('2026-09-07', '2026-09-09', False),  # missing Tuesday / suspension
    ('2026-09-24', '2026-09-28', True),   # exchange holiday
    ('2026-09-24', '2026-09-29', False),
    ('2026-09-25', '2026-09-28', False),  # invalid holiday endpoint
    ('2025-09-05', '2025-09-08', True),   # no intervening weekday
    ('2025-09-05', '2025-09-09', False),
    ('2025-09-30', '2025-10-08', False),  # unknown long holiday fails closed
])
def test_next_close_samples_never_bridge_unverified_missing_sessions(signal, outcome, accepted):
    data = bars(62)
    data[-2] = data[-2].model_copy(update={'trade_date': signal})
    data[-1] = data[-1].model_copy(update={'trade_date': outcome})
    # Earlier feature history must precede both endpoints.
    start = date.fromisoformat(signal) - timedelta(days=150)
    for index in range(60):
        data[index] = data[index].model_copy(update={'trade_date': (start + timedelta(days=index)).isoformat()})
    result = build_close_samples(data)
    assert bool(result) is accepted
    if accepted:
        assert result[0].exit_date == outcome
        assert result[0].net_return == pytest.approx(data[-1].close / data[-2].close - 1)
