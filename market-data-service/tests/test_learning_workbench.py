from datetime import date, datetime, timedelta
from uuid import uuid4
import json
import pytest
from fastapi import HTTPException
from finscope_market_data.paper import learning
from finscope_market_data.paper.engine import SHANGHAI
from finscope_market_data.paper.lab import PaperLab


def test_aligned_daily_comparison_requires_both_closed_sessions():
    p=[dict(at="2026-11-25T15:00:00",equity=100),dict(at="2026-11-26T15:00:00",equity=110)]
    day=date(2026,11,26)
    result=learning.period_stats(p,day,day,{"2026-11-25":200,"2026-11-26":204})
    assert result["excess"]==pytest.approx(.08)
    assert learning.period_stats(p,day,day,{})["market"] is None
    assert learning.period_stats(p[1:],day,day,{})["net"] is None


def test_execution_matches_signal_timestamp_and_does_not_invent_old_slippage():
    order=dict(code="000001",side="BUY",available="09:35",reason="入场")
    events=[dict(**order,action="信号"),dict(**order,action="成交",quantity=100,price=5.02,fee=5),
            dict(code="000002",side="SELL",available="昨日",action="未成交",reason="跨日失效")]
    r=learning.reconciliation([dict(events=events)])
    assert (r["signals"],r["fills"],r["carried"],r["unresolved"],r["slippage"])==(1,1,1,0,None)
    events[1]["executionOpen"]=5
    assert learning.reconciliation([dict(events=events)])["slippage"]==2


def test_locked_study_requires_new_evidence_and_retries_are_idempotent(tmp_path):
    lab=PaperLab(tmp_path/"paper.db",lambda:None)
    now=datetime(2026,11,26,15,1,tzinfo=SHANGHAI)
    with lab.db() as db:
        db.execute("INSERT INTO paper_version VALUES(1,'v',?,?,?)",(json.dumps(dict(capital=100,rule=dict(name="测试"))),'{}',now.isoformat()))
    note=learning.Entry(token=uuid4(),period="weekly",day=now.date(),hypothesis="费率下降",criterion="至少五日后比较")
    assert learning.register(lab,1,note,now)==learning.register(lab,1,note,now)
    with pytest.raises(HTTPException):
        learning.register(lab,1,note.model_copy(update={"criterion":"改写"}),now)
    verdict=learning.Verdict(outcome="支持",reason="学员判断")
    with pytest.raises(HTTPException):
        learning.conclude(lab,1,1,verdict,now)
    later=now+timedelta(days=1)
    with lab.db() as db:
        pt=dict(at=later.replace(minute=0).isoformat(),equity=102,benchmark=101,events=[])
        db.execute("INSERT INTO paper_point VALUES(1,?,?)",(pt["at"],json.dumps(pt)))
    assert learning.conclude(lab,1,1,verdict,later)==learning.conclude(lab,1,1,verdict,later)
    with pytest.raises(HTTPException):
        learning.conclude(lab,1,1,verdict.model_copy(update={"outcome":"推翻"}),later)
    with lab.db() as db:
        old=learning.snapshot(lab,db,1,now.date(),later)
        new=learning.snapshot(lab,db,1,later.date(),later)
        saved=db.execute("SELECT evidence,result_evidence FROM paper_learning_entry").fetchone()
        assert old["monthly"]["pending"]==1 and new["monthly"]["supported"]==1
        assert json.loads(saved[0])["day"]!=json.loads(saved[1])["day"]


def test_forward_folds_select_before_each_unseen_block():
    start=date(2026,10,8)
    days=[str(start+timedelta(days=i)) for i in range(21)]
    a=[100+i for i in range(11)]+[90]*10
    b=[100]*11+[120]*10
    curves={4:dict(zip(days,a)),5:dict(zip(days,b))}
    result=learning.evaluate_folds(curves,4)
    first,second=result["folds"]
    assert first["selected"]==4 and first["testReturn"]<0 and first["trainTo"]<first["testFrom"]
    assert second["selected"]==5 and second["testFrom"]>first["testTo"]
    assert learning.evaluate_folds({4:curves[4]},4)["folds"]==[]


def test_archived_session_distinguishes_no_ledger_and_observed_zero(tmp_path):
    lab=PaperLab(tmp_path/"paper.db",lambda:None)
    day=date(2026,11,26)
    at=str(day)+"T15:00:00+08:00"
    fill=dict(at=at,action="成交",code="000001",side="BUY",quantity=100,fee=5)
    with lab.db() as db:
        db.execute("INSERT INTO paper_version VALUES(1,'v','{}',?,?)",
                   (json.dumps(dict(curve=[dict(at=at,equity=100)],trades=[fill])),at))
        assert learning.archived_fills(db,1,day,[])==dict(fills=1,fees=5,matched=None)
        assert learning.archived_fills(db,1,day,[dict(at=at,events=[fill])])["matched"]==1
        assert learning.archived_fills(db,1,day,[dict(at=at,events=[])])["matched"]==0
        assert learning.archived_fills(db,1,day+timedelta(days=1),[]) is None
