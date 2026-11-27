from datetime import date, datetime, timedelta
from types import SimpleNamespace

import pytest
from pydantic import ValidationError

from finscope_market_data.paper import classic_study
from finscope_market_data.paper.classic_recipe import ClassicRequest, Recipe
from finscope_market_data.paper.engine import SHANGHAI, new_book


A, B = "600001", "600002"
BASE = dict(at="2026-11-20T16:00:00+08:00", codes=[A,B])
RECIPE = Recipe(family="MOMENTUM", rebalance="WEEKLY")


class Rotate:
    def decide(self, history, book, at, equity):
        book["strategyState"] = dict(selection=dict(at=at.isoformat(), targets={B:.5}, candidates=[A,B]))
        return {}, {B:1.}


def bar(at, price=10., volume=50000):
    return SimpleNamespace(open=price, high=price+.1, low=price-.1, close=price, observed_at=at, volume=volume)


def run_bar(book, at, runtime=None, volume=50000, cfg=RECIPE):
    data = {c:[bar(at, volume=volume)] for c in BASE["codes"]}
    return classic_study.step(book, data, BASE, at, at+timedelta(seconds=3), 10000, runtime, execution=cfg)


def test_frozen_lot_goal_survives_t_plus_one_partial_fills_and_restart():
    import json
    book = new_book(10000)
    start = datetime(2026,11,23,14,45,tzinfo=SHANGHAI)
    book.update(cash=0., positions={A:dict(lots=[[str(start.date()),1000]], cost=10000., price=10.)})
    first=run_bar(book,start,Rotate())
    assert not first["events"]
    assert first["rebalance"]["legs"][A]["reason"] == "T+1 待解冻"
    assert first["rebalance"]["legs"][B]["remaining"] == 500
    book=json.loads(json.dumps(book))
    next_day = start.replace(day=24,hour=9,minute=35)
    run_bar(book,next_day)
    assert len(book["pending"]) == 2
    run_bar(book,next_day+timedelta(minutes=5))
    assert len(book["pending"]) == 2
    partial=run_bar(book,next_day+timedelta(minutes=10),volume=6000)
    assert partial["rebalance"]["legs"][A]["remaining"] == -700
    assert partial["rebalance"]["status"] == "进行中"
    for slot in (15,20,25,30):
        run_bar(book,next_day+timedelta(minutes=slot))
    plan=book["rebalancePlan"]
    assert plan["status"] == "已完成"
    assert not book["pending"] and list(book["positions"]) == [B]
    assert plan["legs"][B]["actual"] == 500
    costs=book["executionCosts"]
    assert costs["notional"] > 0 and costs["slippage"] > 0
    assert costs["commission"] + costs["stampTax"] == pytest.approx(book["fees"])
    assert plan["fees"] == book["fees"]


def test_fee_budget_and_expiry_never_fill_an_old_target():
    cfg=Recipe(family="MOMENTUM", rebalance="WEEKLY", maxFeeBps=1)
    start=datetime(2026,11,23,14,45,tzinfo=SHANGHAI)
    book=new_book(10000)
    run_bar(book,start,Rotate(),cfg=cfg)
    run_bar(book,start+timedelta(minutes=5),cfg=cfg)
    assert not book["fills"]
    assert "费率上限" in str(book["rebalancePlan"]["legs"][B])
    expired=run_bar(book,start+timedelta(days=7),cfg=cfg)
    assert expired["rebalance"]["status"] == "已到期" and not book["pending"]


def test_freeze_validates_custom_interval_and_keeps_comparison_cutoffs(tmp_path, monkeypatch):
    from finscope_market_data.paper.lab import PaperLab
    lab=PaperLab(tmp_path/"paper.db", lambda:None)
    at=datetime(2026,11,27,14,0,tzinfo=SHANGHAI)
    monkeypatch.setattr(classic_study,"schedule", lambda *args:[BASE])
    kwargs=dict(researchMode="CLASSIC",recipe=RECIPE,capital=10000)
    with pytest.raises(ValidationError):
        ClassicRequest(**kwargs,startDate=date(2026,10,1))
    with pytest.raises(ValueError,match="已完成"):
        classic_study.freeze(lab,ClassicRequest(**kwargs,startDate=date(2026,10,1),endDate=at.date()),at)
    frozen=classic_study.freeze(lab,ClassicRequest(**kwargs,startDate=date(2026,10,1),endDate=date(2026,11,20)),at)
    assert frozen["start"][:10] == "2026-10-01" and frozen["cutoff"][:10] == "2026-11-20"
    frozen["classicSnapshots"]=["a"*64]
    job=lab.jobs.submit(frozen)["jobId"]
    with lab.db() as db:
        db.execute("UPDATE paper_job SET version=7 WHERE id=?",(job,))
    compared=classic_study.freeze(lab,ClassicRequest(**kwargs,reference=7),at)
    assert compared["classicSnapshots"] == frozen["classicSnapshots"] and compared["start"] == frozen["start"]
    with pytest.raises(ValueError,match="原实验区间"):
        classic_study.freeze(lab,ClassicRequest(**kwargs,reference=7,startDate=date(2026,10,2),endDate=date(2026,11,20)),at)


def test_common_performance_uses_last_daily_marks_and_does_not_annualize_short_samples():
    from finscope_market_data.paper.performance import report
    points = [dict(at=at, equity=v, benchmark=100) for at,v in [
        ("2026-11-23T10:00", 110), ("2026-11-23T15:00", 105), ("2026-11-24T15:00", 90),
        ("2026-11-26T15:00", 98)]]
    stats = report(points,100,2)
    assert stats["days"] == 3 and stats["returnDays"] == 1
    assert stats["maxDrawdown"] == pytest.approx(1-90/105)
    assert stats["excess"] == pytest.approx(-.02) and stats["feeRate"] == .02
    assert stats["sharpe"] is None


def test_fee_cap_checked_again_on_partial_fill_and_blocks_a_price_gap():
    from finscope_market_data.paper.engine import execute
    book=new_book(10000)
    at=datetime(2026,11,23,14,55,tzinfo=SHANGHAI)
    order=dict(code=A,side="BUY",planAt="selection",maxShares=500,budget=5000,maxFeeBps=15,
               available=(at-timedelta(minutes=9)).isoformat(),reason="测试费用")
    event=execute(book,order,bar(at,volume=2000),at)
    assert event["action"] == "未成交" and "费率上限" in event["reason"]
    assert book["cash"] == 10000


def test_optimization_and_volatility_target_use_aligned_completed_days():
    from finscope_market_data.paper.portfolio_math import optimized_weights, portfolio_risk
    diagonal=[[.01,0.],[0.,.04]]
    assert optimized_weights(diagonal,"MIN_VARIANCE",1.,1.) == pytest.approx([.8,.2],abs=1e-6)
    assert optimized_weights(diagonal,"RISK_PARITY",1.,1.) == pytest.approx([2/3,1/3],abs=1e-6)
    assert optimized_weights(diagonal,"MIN_VARIANCE",1.,.55) == pytest.approx([.55,.45])
    def rows(multiple):
        price=100
        daily=[]
        for i, r in enumerate([0,.01,-.02,.03,-.01,.02,-.03]):
            price *= 1+multiple*r
            daily.append(dict(at=f"2026-11-{i+10}T15:00",close=price))
        return [dict(dailyWindow=daily)]
    cfg=Recipe(family="MULTI",allocation="RISK_PARITY",riskWindow=5,exposure=1,maxWeight=1,targetVol=.05)
    weights,audit=portfolio_risk([A,B],{A:rows(1),B:rows(2)},cfg.model_dump(),{})
    assert audit["days"] == 5 and sum(audit["contributions"].values()) == pytest.approx(1.)
    assert sum(weights.values()) < 1 and audit["annualVol"] == pytest.approx(.05)
    short=rows(2)
    short[0]["dailyWindow"]=short[0]["dailyWindow"][:-3]
    assert portfolio_risk([A,B],{A:rows(1),B:short},cfg.model_dump(),{})[1]["unavailable"]


@pytest.mark.parametrize("setting", ["stopLoss","drawdownStop"])
def test_risk_can_exit_on_a_non_selection_bar_and_wait_for_t_plus_one(setting):
    cfg=Recipe(family="MOMENTUM",rebalance="WEEKLY",**{setting:.05})
    at=datetime(2026,11,24,10,0,tzinfo=SHANGHAI)
    book=new_book(10000)
    book.update(cash=0.,positions={A:dict(lots=[[str(at.date()),1000]],cost=10000.,price=10.)})
    def step(t,price):
        return classic_study.step(book,{A:[bar(t,price)]},BASE,t,t+timedelta(seconds=3),10000,execution=cfg)
    step(at,10)
    point=step(at+timedelta(minutes=5),9.)
    assert point["rebalance"]["source"] == "盘中风控" and not point["events"]
    assert point["rebalance"]["legs"][A]["target"] == 0
    next_day=at+timedelta(days=1)
    step(next_day,9.)
    final=step(next_day+timedelta(minutes=10),9.)
    assert final["rebalance"]["status"]=="已完成" and not book["positions"]
