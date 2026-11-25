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
    assert point["events"][0]["action"] == "未成交" and book["fills"] == 1
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
