from datetime import date

import pytest
from fastapi.testclient import TestClient

from finscope_market_data.app import create_app
from finscope_market_data.settings import Settings
from finscope_market_data.forecast.trading_calendar import previous_session


@pytest.mark.parametrize("target,expected", [
    ("2026-09-21", "2026-09-18"),
    ("2026-09-22", "2026-09-21"),
    ("2026-10-08", "2026-09-30"),
    ("2026-02-24", "2026-02-13"),
])
def test_previous_session_includes_weekend_and_exchange_holidays(target, expected):
    assert previous_session(date.fromisoformat(target)) == date.fromisoformat(expected)


def test_unknown_calendar_year_is_not_guessed():
    assert previous_session(date(2028, 10, 8)) is None


def test_calendar_api_returns_verified_date_and_rejects_unavailable_calendar(tmp_path):
    client = TestClient(create_app(settings=Settings(data_dir=tmp_path)))
    response = client.get("/v1/calendar/previous-session?before=2026-10-08")
    assert response.status_code == 200
    assert response.json() == {"previous_session": "2026-09-30"}
    assert client.get("/v1/calendar/previous-session?before=2028-10-08").status_code == 503
    assert client.get("/v1/calendar/previous-session?before=not-a-date").status_code == 422


def test_event_window_aligns_baseline_and_five_sessions_across_holiday(tmp_path):
    client = TestClient(create_app(settings=Settings(data_dir=tmp_path)))
    response = client.get('/v1/calendar/event-window?on_or_after=2026-10-01')
    assert response.status_code == 200
    sessions = response.json()['sessions']
    assert len(sessions) == 11
    assert sessions[5] == '2026-09-30'
    assert sessions[6:] == ['2026-10-08', '2026-10-09', '2026-10-12', '2026-10-13', '2026-10-14']
    assert client.get('/v1/calendar/event-window?on_or_after=2028-10-01').status_code == 503
    assert client.get('/v1/calendar/event-window?on_or_after=2026-01-01').status_code == 503
    assert client.get('/v1/calendar/event-window?on_or_after=invalid').status_code == 422
