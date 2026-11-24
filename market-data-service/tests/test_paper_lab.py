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

