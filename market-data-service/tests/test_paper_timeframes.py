from datetime import datetime
from types import SimpleNamespace

from finscope_market_data.forecast.trading_calendar import next_session
from finscope_market_data.overnight.engine import expected_times
from finscope_market_data.paper.timeframes import Timeframes


def test_completed_groups_missing_bars_daily_warmup_and_ex_date():
    window = Timeframes()
    day = datetime(2026, 5, 6).date()
    clocks = expected_times("15:00")
    for i in range(62):
        for j, clock in enumerate(clocks):
            bar = SimpleNamespace(open=100+i, close=101+i)
            f = window.update(datetime.fromisoformat(f"{day}T{clock}"), bar)
            if i == j == 0:
                assert set(f) == {"return5m"}
            if i == 0 and j == 2:
                assert "return15m" in f and "return60m" not in f
            if i == 1 and j == 0:
                assert f["return1d"] == 101/100-1
        day = next_session(day)
    assert f["trend60d"] == 162/102-1
    for clock in clocks:
        if clock == "09:40":
            continue
        bar = SimpleNamespace(open=160, close=161, actions=[{}] if clock == clocks[0] else [])
        f = window.update(datetime.fromisoformat(f"{day}T{clock}"), bar)
        if clock == "09:45":
            assert set(f) == {"return5m"}
    assert "return1d" not in f and "trend60d" not in f
