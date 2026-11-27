import asyncio
from datetime import date, timedelta
import json
from uuid import uuid4

import pytest

from finscope_market_data.paper import classic_study, walk_forward
from finscope_market_data.paper.classic_recipe import Recipe
from finscope_market_data.paper.lab import PaperLab


def closes():
    start = date(2026, 11, 2)
    return [{"at": (start + timedelta(days=i)).isoformat() + "T15:00:00+08:00"}
            for i in range(19) if (start + timedelta(days=i)).weekday() < 5]


def test_no_window_spans_a_missing_close_or_overlaps_previous_validation():
    curve = closes()
    folds, coverage = walk_forward.windows(curve, 5, 3)
    assert len(folds) == 3 and coverage["tailDays"] == 1
    assert all(train[-1] < test[0] for train, test in folds)
    assert len({day for _, test in folds for day in test}) == 9
    del curve[6]
    curve.insert(6, {"at": "2026-11-10T14:55:00+08:00"})
    folds, coverage = walk_forward.windows(curve, 5, 3)
    assert coverage["spans"] == 2
    assert all(days[0] > "2026-11-10" for days, _ in folds)


def test_out_of_sample_cannot_influence_training_choice():
    folds = [(["1","2"], ["3","4"]), (["3","4"], ["5","6"])]
    scores = {"1": {4:.08,6:.01,8:.08}, "3": {4:-.1,6:.01,8:.2}, "5": {6:.02,8:-.02}}
    calls = []
    def replay(value, days):
        calls.append((value, days[0]))
        return {"net": scores[days[0]][value]}
    rows = walk_forward.compare(folds, {"values":[8,6,4]}, 6, replay, lambda _:None)
    assert [r["selected"] for r in rows] == [4,8]
    assert rows[0]["validation"]["net"] == -.1 and rows[1]["spread"] == -.04
    assert calls[:5] == [(8,"1"),(6,"1"),(4,"1"),(4,"3"),(6,"3")]


def test_replay_uses_frozen_source_and_resets_each_window(tmp_path, monkeypatch):
    lab = PaperLab(tmp_path/"paper.db", lambda:None)
    recipe = Recipe(family="TREND").model_dump()
    history = dict(classicSnapshots=["digest"], universeTimeline=[{"codes":["c"]}], curve=closes())
    rule = dict(recipe=recipe, executionVersion=2, image="frozen-image", code="RECIPE = "+repr(recipe)+"\n# archived")
    with lab.db() as db:
        db.execute("INSERT INTO paper_version VALUES(8,'fixture',?,?,'day')",
                   (json.dumps(dict(rule=rule, capital=10000)), json.dumps(history)))
    plan = walk_forward.WalkRequest(requestId=uuid4(), parameter="window", values=[6,4], trainDays=5, testDays=3)
    job = walk_forward.enqueue(lab,8,plan)["jobId"]
    assert walk_forward.enqueue(lab,8,plan)["jobId"] == job
    with pytest.raises(ValueError,match="另一项实验"):
        walk_forward.enqueue(lab,8,plan.model_copy(update={"testDays":4}))
    with pytest.raises(ValueError,match="重复"):
        walk_forward.enqueue(lab,8,plan.model_copy(update={"values":[6,6]}))
    loaded = []
    def read(digest, *, daily_window):
        loaded.append((digest,daily_window))
        return {}
    monkeypatch.setattr(lab.jobs, "read_panel", read)
    def replay(panel,timeline,code,image,capital,execution,*,first,last):
        assert capital == 10000 and image == "frozen-image" and code.endswith("# archived")
        curve = [dict(p,equity=capital,benchmark=capital) for p in history["curve"] if first<=p["at"][:10]<=last]
        return dict(curve=curve,fees=0,fills=0,costs={},validationReturn=0)
    monkeypatch.setattr(classic_study, "simulate_panel", replay)
    with lab.db() as db:
        request = json.loads(db.execute("SELECT request FROM paper_job WHERE id=?",(job,)).fetchone()[0])
    asyncio.run(lab.jobs.process(job,request))
    report = walk_forward.results(lab,8)[0]
    assert loaded == [("digest",True)] and report["state"] == "DONE"
    assert report["result"]["folds"][0]["selected"] == 6
    with lab.db() as db:
        assert db.execute("SELECT count(*) FROM paper_account").fetchone()[0] == 0
