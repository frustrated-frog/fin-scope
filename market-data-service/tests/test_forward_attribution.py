from finscope_market_data.paper import forward_attribution
from finscope_market_data.paper.lab import PaperLab


def marked(at, equity, values):
    point = dict(at=at, equity=equity, benchmark=1000, events=[])
    forward_attribution.record_marks(dict(positions={c:dict(price=p, lots=[["day",10]]) for c,p in values.items()}),point)
    return point


def test_account_change_reconciles_purchases_exits_fees_and_receivables():
    prior = marked("2026-11-26T15:00",1000,{"sold":10})
    end = marked("2026-11-27T15:00",1017,{"bought":11})
    end["receivable"] = 2
    end["events"] = [dict(code=c,side=s,action="成交",reason="调仓",quantity=10,price=p,fee=2.5)
                     for c,s,p in [("sold","SELL",11),("bought","BUY",10)]]
    report = PaperLab.day_summary([end],prior,1000)["attribution"]
    assert report["net"] == 17 and report["beforeFees"] == 22 and report["fees"] == 5
    assert report["residual"] == 0 and report["receivableChange"] == 2
    assert [r["net"] for r in report["stocks"]] == [7.5,7.5]


def test_old_marks_and_empty_cash_accounts_are_distinguished():
    point = marked("2026-11-27T15:00",1000,{})
    assert PaperLab.day_summary([point],None,1000)["attribution"]["stocks"] == []
    old = dict(at="2026-11-26T15:00",equity=993,benchmark=1000,events=[])
    result = PaperLab.day_summary([point],old,1000)["attribution"]
    assert result["stocks"] is None and result["residual"] is None and result["beforeFees"] == 7
