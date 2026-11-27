from datetime import datetime, timedelta
import json
from types import SimpleNamespace

import pytest
from pydantic import ValidationError
from finscope_market_data.paper import classic_components as components
from finscope_market_data.paper.classic_recipe import Recipe, program
from finscope_market_data.paper.classic_universe import as_of, schedule, active_codes
from finscope_market_data.paper.engine import SHANGHAI, new_book
from finscope_market_data.paper.classic_study import step


def sample_rows(up=True):
    prices = [10, 10.2, 10.5, 10.4, 10.6, 10.7, 11.] if up else [11, 10.9, 11., 10.8, 10.6, 10.5, 10.]
    rows = []
    for i, price in enumerate(prices):
        at = (datetime(2026, 11, 26, 14, 15, tzinfo=SHANGHAI) + timedelta(minutes=i*5)).isoformat()
        candle = dict(at=at, close=price, volume=10000, high=price+.1, low=price-.1)
        rows.append(dict(**candle, completedBars={"5m": candle}))
    return rows


@pytest.mark.parametrize("family,preferred", [("TREND","600000"),("MOMENTUM","600000"),
                                             ("REVERSION","600001"),("MULTI","600000")])
def test_compiled_strategies_choose_from_dated_pool(family, preferred):
    cfg = Recipe(family=family, period="5m", holdings=1)
    env = {}
    exec(program(cfg).code, env)
    rows = {"600000": sample_rows(), "600001": sample_rows(False), "600002": sample_rows()}
    ctx = dict(bars=rows, selectionBase=["600000","600001"], at=rows["600000"][-1]["at"],
               positions={}, positionDetails={}, state={})
    decision = env["decide"](ctx)
    assert decision["weights"] == {preferred: .4}
    assert set(decision["state"]["selection"]["candidates"]) == {"600000", "600001"}
    assert env["decide"]({**ctx, "state": decision["state"]})["weights"] == {}
    assert components.indicators(rows["600000"][:-1],cfg.model_dump()) is None


def test_allocation_caps_and_weekly_rebalance():
    cfg = Recipe(family="MOMENTUM", allocation="INVERSE_VOL", maxWeight=.5, exposure=.9)
    data = {"a":{"volatility":.1},"b":{"volatility":.2}}
    assert components.portfolio(["a","b"],data,{},cfg.model_dump()) == pytest.approx({"a":.5,"b":.3})
    assert components.percentiles({"a":1,"b":1,"c":3}) == {"a":.5,"b":.5,"c":1.}
    with pytest.raises(ValidationError):
        Recipe(family="TREND", holdings=5, poolSize=4)
    cfg = Recipe(family="TREND", period="5m", rebalance="WEEKLY")
    env = {}
    exec(program(cfg).code, env)
    ctx = dict(bars={"a":sample_rows()}, selectionBase=["a"], at="2026-11-26T14:45:00+08:00",
               positions={},positionDetails={},state={})
    decision = env["decide"](ctx)
    assert decision["weights"]
    assert not env["decide"]({**ctx,"at":"2026-11-27T14:45:00+08:00","state":decision["state"]})["weights"]


def test_late_discovery_report_and_departed_position(tmp_path):
    directory = tmp_path / "discovery-research"
    directory.mkdir()
    for day, published, code in [("23","23T16:00","600001"),("24","26T16:00","600002"),("25","25T16:00","600003")]:
        (directory / f"2026-11-{day}.json").write_text(json.dumps(dict(as_of_date="2026-11-"+day,
            retrieved_at="2026-11-"+published, candidates=[dict(code=code,name="示例")])))
    at = datetime(2026, 11, 26, 14, 45, tzinfo=SHANGHAI)
    timeline = schedule(SimpleNamespace(path=tmp_path/"paper.db"), "DISCOVERY", at+timedelta(days=1), at-timedelta(days=4))
    assert as_of(timeline,at-timedelta(days=1))["codes"] == ["600001"]
    assert as_of(timeline,at+timedelta(days=1))["codes"] == ["600003"]
    assert as_of(timeline,at-timedelta(days=4)) is None
    book = new_book(10000)
    book["positions"]["600001"] = {}
    assert active_codes(as_of(timeline,at),book) == ["600003","600001"]
