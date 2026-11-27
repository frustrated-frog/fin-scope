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


def test_completed_candles_exclude_partial_sessions_and_unknown_volume():
    state = Timeframes(candles=True)
    stamp = "2026-11-26T"
    for i, t in enumerate(expected_times("15:00")):
        row = SimpleNamespace(open=10+i, high=12+i, low=9+i, close=11+i,
                              volume=100, amount=1000, estimated_volume=i == 1)
        state.update(datetime.fromisoformat(stamp+t), row)
        if t == "09:45":
            assert state.candles["15m"] == dict(at=stamp+"09:45:00+08:00", open=10, high=14, low=9,
                                                close=13, volume=None, amount=3000)
        if t == "14:55":
            assert "1d" not in state.candles and state.candles["60m"]["at"][11:16] == "14:00"
    state.update(datetime.fromisoformat("2026-11-27T09:35"), row)
    assert state.candles["1d"]["at"] == stamp+"15:00:00+08:00" and "15m" not in state.candles
