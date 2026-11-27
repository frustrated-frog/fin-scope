from datetime import datetime
from types import SimpleNamespace
import json

import pytest

from finscope_market_data.paper import autonomous_quant as aq
from finscope_market_data.paper.classic_recipe import Recipe
from finscope_market_data.paper.engine import ENGINE,new_book,SHANGHAI
from finscope_market_data.paper.lab import PaperLab


def test_sources_deduplicate_copied_versions_and_never_use_fills(tmp_path):
    lab = PaperLab(tmp_path/"paper.db",lambda:None)
    recipe = Recipe(family="TREND").model_dump()
    spec = dict(rule=dict(name="研究",recipe=recipe),capital=10000)
    history = dict(engine=ENGINE+"-classic-v2",classicSnapshots=["a"],universeTimeline=[dict(at="old",codes=["a","b"])],
                   firstDay="2026-11-02",lastDay="2026-11-27",days=20,decisions=[dict(at="decision",targets={"a":.4,"b":.4})])
    with lab.db() as db:
        for i in range(2):
            db.execute("INSERT INTO paper_version VALUES(?,?,?,?,?)",(i+1,str(i),json.dumps(spec),
                       json.dumps(dict(history,trades=[{"code":"never-selected","quantity":i+1}])),"today"))
    sources = aq.catalog(lab)
    assert [s["tool"] for s in sources] == ["ALLOCATION","CORRELATION"]
    assert aq.nominations(history["decisions"]) == {"decision":["a","b"]}
    with pytest.raises(ValueError,match="不可用"):
        aq.execute(lab,aq.StudyInput(source="0"*64,tool="EXECUTION"))
    with lab.db() as db:
        assert db.execute("SELECT count(*) FROM paper_account").fetchone()[0] == 0


def test_configurations_share_candidates_and_apply_caps_and_cash():
    cov=[[.01,0],[0,.04]]
    for method in aq.METHODS:
        w=aq.risk_weights(method,cov,.95,.4)
        assert sum(w)==pytest.approx(.8)
        assert max(w)<=.4000001
    assert aq.risk_weights("INVERSE_VOL",cov,1,1)==pytest.approx([2/3,1/3])
    assert aq.risk_weights("RISK_PARITY",cov,1,1)==pytest.approx([2/3,1/3])


def test_missing_prior_covariance_skips_every_arm_even_when_future_returns_exist():
    at=datetime(2026,11,11,14,45,tzinfo=SHANGHAI)
    cfg=Recipe(family="TREND",riskWindow=5)
    bars=[dict(at=f"2026-11-{d:02}T15:00",close=p) for d,p in enumerate([1,2,3,2,5,4,7],8)]
    for method in aq.METHODS:
        strategy=aq.FixedSelection({at.isoformat():["a","b"]},cfg,method)
        book=new_book(10000)
        strategy.decide({c:[SimpleNamespace(dailyWindow=bars)] for c in ("a","b")},book,at,10000)
        assert strategy.missing==[at.isoformat()] and not strategy.samples
        assert not book.get("strategyState")


def test_regime_requires_both_market_directions():
    at = datetime(2026,11,2,15,tzinfo=SHANGHAI)
    panel = {c:[SimpleNamespace(observed_at=at,dailyWindow=[])] for c in "abcdefgh"}
    hist = dict(firstDay="2026-11-02",lastDay="2026-11-02",universeTimeline=[dict(at=at.isoformat(),codes=list(panel))])
    with pytest.raises(ValueError,match="至少五个"):
        aq.correlation_study(panel,hist)


def test_a_frozen_move_to_cash_exits_quoted_holdings_and_keeps_unquoted_positions():
    at = datetime(2026, 11, 11, 14, 45, tzinfo=SHANGHAI)
    for method in aq.METHODS:
        strategy = aq.FixedSelection({at.isoformat(): []}, Recipe(family="TREND"), method)
        book = new_book(10000)
        book["positions"] = {"quoted": {}, "suspended": {}}
        targets, scores = strategy.decide({"quoted": [object()]}, book, at, 10000)
        assert targets == {"quoted": 0.}
        assert set(scores) == set(targets)
        assert book["strategyState"]["selection"]["targets"] == {}
