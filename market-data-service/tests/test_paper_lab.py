from datetime import datetime, timedelta
from types import SimpleNamespace

import pytest
from finscope_market_data.paper.engine import SHANGHAI, Rule, advance, new_book
from finscope_market_data.paper.lab import PaperLab, Research


RULE = Rule(name="动量", hypothesis="短窗上涨延续", terms=[dict(feature="momentum",window=2,weight=1)],
            entry=0.01, exit=-0.01, slots=1)
START = datetime(2026, 11, 23, 10, 0, tzinfo=SHANGHAI)


def bars(prices):
    return {"600000":[SimpleNamespace(close=p, open=p, high=p+.1, low=p-.1, volume=100000)
                      for p in prices]}


def test_signal_waits_for_observable_fill_and_sell_obeys_t_plus_one():
    book = new_book(10000)
    first = advance(book,RULE,bars([9,9.5,10]),START,START+timedelta(seconds=5),10000)
    assert first["events"][0]["side"] == "BUY" and book["fills"] == 0
    for minutes in [5,10]:
        end = START + timedelta(minutes=minutes)
        advance(book,RULE,bars([9.9,10,10]),end,end,10000)
    assert book["fills"] == 1 and book["cash"] >= 0 and book["fees"] == 5
    for minutes in [15,20]:
        end = START + timedelta(minutes=minutes)
        point = advance(book,RULE,bars([10,9.9,9.7]),end,end,10000)
    assert point["events"] == [] and book["pending"] == [] and book["fills"] == 1
    end = START + timedelta(days=1)
    advance(book,RULE,bars([10,9.9,9.7]),end,end,10000)
    advance(book,RULE,bars([10,9.9,9.7]),end+timedelta(minutes=5),end+timedelta(minutes=5),10000)
    assert book["fills"] == 2 and not book["positions"]
    assert book["cash"] == round(10000 + book["realized"],2)


def test_deployment_is_idempotent_and_starts_in_cash(tmp_path):
    lab = PaperLab(tmp_path/"lab.db", lambda:None)
    spec = Research(rule=RULE,pool="WATCHLIST",source="snapshot",capital=10000,codes=["600000"],origin="test")
    with lab.db() as db:
        db.execute("INSERT INTO paper_version VALUES(1,'x',?,'{}','now')",(spec.model_dump_json(),))
    lab.deploy(1)
    started = lab.catalog()[0]["started"]
    lab.deploy(1)
    assert lab.catalog()[0]["started"] == started
    assert lab.catalog()[0]["book"]["cash"] == 10000
    assert lab.detail(1,"")["points"] == []


def test_invalid_thresholds():
    with pytest.raises(ValueError):
        Rule.model_validate({**RULE.model_dump(),"exit":2})



@pytest.mark.asyncio
async def test_replay_manual_deployment_and_paired_forward_evidence(tmp_path, monkeypatch):
    from finscope_market_data.models import StockSymbol, MinutePriceBar
    from finscope_market_data.paper import lab as module

    now = START + timedelta(seconds=10)
    symbol = StockSymbol(code="600000", market="SH")
    samples = []
    def add(t, price):
        samples.append(MinutePriceBar(symbol=symbol, observed_at=t, frequency_minutes=5,
                                      open=price, close=price, high=price+.02, low=price-.02, volume=200000))
    for i in range(6):
        add(START-timedelta(minutes=25-i*5),10+i*.1)

    async def fetch(*args, **kwargs):
        return SimpleNamespace(data=samples)
    monkeypatch.setattr(module, "stamp", lambda:now)
    lab = PaperLab(tmp_path/"paper.db", lambda:SimpleNamespace(fetch=fetch))
    base = Research(rule=RULE, capital=10000,pool="DISCOVERY",codes=["600000"],source="run 1",origin="test")
    parent = (await lab.research(base))["id"]
    assert await lab.research(base) == {"id":parent}
    assert not lab.catalog()[0]["book"]
    child = base.model_copy(deep=True)
    child.parent = parent
    child.rule.terms[0].weight = -1
    version = (await lab.research(child, auto_start=True))["id"]
    started = lab.catalog()[0]["started"]
    assert started and await lab.research(child, auto_start=True) == {"id": version}
    assert lab.catalog()[0]["started"] == started
    for i in range(1,5):
        end = START+timedelta(minutes=i*5)
        now = end+timedelta(seconds=10)
        add(end,10.5+i*.15)
        await lab.tick()
        await lab.tick()  # same observed bar after restart or retry
    detail = lab.detail(version,"")
    assert len(detail["points"]) == 4 and len(detail["daily"]) == 1
    assert detail["book"]["fills"] == 0 and detail["book"]["control"]["fills"] == 1
    assert detail["daily"][0]["control"] != detail["daily"][0]["equity"]
    assert lab.detail(parent,"")["book"] is None
    review = module.Review(at=detail["points"][-1]["at"],model="test",text="样本不足")
    lab.save_review(version, review)
    assert lab.detail(version,"")["review"]["at"] == review.at
    now += timedelta(minutes=10)
    await lab.tick()
    assert lab.catalog()[0]["status"] == "行情缺失或延迟，等待同步"


def test_reviews_use_observed_day_and_retry_missing_closes(tmp_path, monkeypatch):
    from finscope_market_data.paper import lab as module

    lab = PaperLab(tmp_path/"review.db", lambda:None)
    spec = Research(rule=RULE,pool="WATCHLIST",source="snapshot",capital=10000,codes=["600000"],origin="test")
    day1 = START.replace(hour=15)
    day2 = day1 + timedelta(days=1)
    with lab.db() as db:
        db.execute("INSERT INTO paper_version VALUES(1,'v',?,'{}','now')",(spec.model_dump_json(),))
        for t, equity in [(day1, 10100), (day2, 10000)]:
            point = dict(at=t.isoformat(),equity=equity,benchmark=10005,cash=100,holdings={"600000":100},
                         events=[dict(code="600000",side="SELL",action="未成交",reason="T+1"),
                                 dict(code="600000",side="BUY",action="成交",reason="入场",quantity=100,fee=5)])
            db.execute("INSERT INTO paper_point VALUES(1,?,?)",(point["at"],module.dumps(point)))
    monkeypatch.setattr(module, "stamp", lambda:day2)
    assert lab.pending_reviews() == [{"id":1,"day":day1.date().isoformat()}]
    monkeypatch.setattr(module, "stamp", lambda:day2+timedelta(minutes=10))
    assert len(lab.pending_reviews()) == 2
    summary = lab.detail(1,day2.date().isoformat())["daySummary"]
    assert (summary["pnl"],summary["fills"],summary["fees"]) == (-100,1,5)
    lab.save_review(1,module.Review(at=day2.isoformat(),text="收盘复盘",model="test"))
    assert lab.detail(1,day1.date().isoformat())["review"] is None
    assert lab.pending_reviews() == [{"id":1,"day":day1.date().isoformat()}]


def test_weekly_health_tracks_whole_missing_days_pause_and_lunch(tmp_path):
    lab = PaperLab(tmp_path / "lab.db", lambda: None)
    now = START + timedelta(days=4, minutes=7)
    account = dict(started=START.isoformat(), lifecycle="RUNNING",
                   book='{"resumedAt":"2026-11-27T09:40:00+08:00"}')
    with lab.db() as db:
        db.execute("INSERT INTO paper_run_interval VALUES(?,?,?)",
                   (1, START.isoformat(), "2026-11-24T15:06:00+08:00"))
        db.execute("INSERT INTO paper_point VALUES(1,?,'{}')", ("2026-11-27T10:00:00+08:00",))
        health = lab.execution_health(db, 1, account, now)
    assert health["executed"] == 1 and health["due"] == 4
    assert "10:05" not in health["missingTimes"]
    assert [d["date"] for d in health["days"]] == ["2026-11-27", "2026-11-24", "2026-11-23"]
    assert health["days"][1]["missed"] == 48


def test_cached_comparison_uses_close_only_risk_without_changing_frozen_history(tmp_path):
    import json
    lab = PaperLab(tmp_path/"lab.db", lambda:None)
    spec = Research(rule=RULE,pool="WATCHLIST",source="snapshot",capital=10000,codes=["600000"],origin="test")
    curve = [dict(at="2026-11-23T15:00",equity=10000,benchmark=10000),
             dict(at="2026-11-24T11:00",equity=9000,benchmark=10000)]
    old = dict(curve=curve,fees=0,comparison=dict(version=1,performance=dict(maxDrawdown=.1)))
    with lab.db() as db:
        db.execute("INSERT INTO paper_version VALUES(1,'base',?,?,'now')",(spec.model_dump_json(),json.dumps(dict(curve=curve,fees=0))))
        db.execute("INSERT INTO paper_version VALUES(2,'change',?,?,'now')",(spec.model_dump_json(),json.dumps(old)))
    refreshed = lab.detail(2,"")["history"]["comparison"]["performance"]
    assert refreshed["closeDays"] == 1 and refreshed["maxDrawdown"] == 0
    assert refreshed["netReturn"] == pytest.approx(-.1)
    with lab.db() as db:
        assert json.loads(db.execute("SELECT history FROM paper_version WHERE id=2").fetchone()[0]) == old
