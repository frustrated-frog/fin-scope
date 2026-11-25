from datetime import timedelta
import json
from types import SimpleNamespace

import pytest
from fastapi import HTTPException

from finscope_market_data.paper import research
from finscope_market_data.paper.engine import advance, new_book
from finscope_market_data.paper.lab import PaperLab, Research, Review
from finscope_market_data.paper.python_runtime import image_id
from test_paper_lab import RULE, START, bars


def test_partial_prices_keep_stock_value_and_block_only_missing_symbol():
    book = new_book(10000)
    book.update(
        cash=8000,
        positions={
            "600000": {"price": 10, "cost": 2000, "lots": [["2026-11-20", 200]]}
        },
    )
    other = {"000001": bars([10, 10, 10])["600000"]}
    point = advance(
        book, RULE, other, START, START, 10000, universe=["600000", "000001"]
    )
    assert point["equity"] == 10000 and point["stale"] == ["600000"]


def test_account_lifecycle_preserves_balance_and_cancels_orders(tmp_path):
    lab = PaperLab(tmp_path / "lab.db", lambda: None)
    spec = Research(
        rule=RULE,
        pool="WATCHLIST",
        source="s",
        capital=10000,
        codes=["600000"],
        origin="test",
    )
    with lab.db() as db:
        db.execute(
            "INSERT INTO paper_version VALUES(1,'v',?,'{}','now')",
            (spec.model_dump_json(),),
        )
    lab.deploy(1)
    with lab.db() as db:
        db.execute(
            """UPDATE paper_account SET book=json_set(book,'$.pending',json('[{"side":"BUY"}]'))"""
        )
    for state in ["PAUSED", "RUNNING", "STOPPED", "ARCHIVED"]:
        assert lab.transition(1, state) == {"id": 1}
    with pytest.raises(HTTPException):
        lab.transition(1, "RUNNING")
    assert (
        lab.catalog()[0]["book"]["pending"] == []
        and lab.catalog()[0]["book"]["equity"] == 10000
    )


@pytest.mark.asyncio
async def test_agent_repairs_failure_replays_and_reuses_strategy_in_forward(
    tmp_path, monkeypatch
):
    try:
        image_id()
    except (ValueError, OSError):
        pytest.skip("requires local strategy runtime image")
    lab = PaperLab(tmp_path / "lab.db", lambda: None)
    data = [
        SimpleNamespace(
            observed_at=START + timedelta(days=day, minutes=5 * i),
            open=10,
            high=10.2,
            low=9.8,
            close=10,
            amount=1e6,
            volume=100000,
        )
        for day in range(4)
        for i in range(6)
    ]

    async def prices(_):
        return data

    monkeypatch.setattr(lab, "bars", prices)
    monkeypatch.setattr(
        research,
        "datetime",
        type(
            "Clock",
            (research.datetime,),
            {"now": classmethod(lambda cls, tz: START + timedelta(days=5))},
        ),
    )
    calls = []

    def write(prompt, previous, error, context):
        calls.append(error)
        code = "def decide(ctx):\n    return {'weights':{'600000':.5},'scores':{'600000':1},'state':{}}"
        return research.Program(
            name="策略",
            hypothesis="试验",
            factor="因子",
            code=code if error else "def decide(:",
        )

    monkeypatch.setattr(research, "author", write)
    req = research.JobRequest(
        codes=["600000"],
        capital=10000,
        pool="WATCHLIST",
        source="test",
        origin="test",
        autoIterate=True,
    )
    job = lab.jobs.enqueue(req)["jobId"]
    await lab.jobs.process(job, req.model_dump())
    assert calls[0] is None and "SyntaxError" in calls[1]
    v = lab.jobs.list()[0]["version"]
    detail = lab.detail(v, "")
    assert detail["history"]["days"] == 4 and detail["history"]["fills"] == 1
    with lab.db() as db:
        spec = lab.spec(db, v)
    book = detail["book"]
    for b in data[:4]:
        await lab.forward(
            book,
            spec.rule,
            spec,
            {"600000": [b]},
            b.observed_at,
            b.observed_at + timedelta(seconds=3),
        )
    assert book["fills"] == 1 and lab.detail(v,"")["book"]["equity"] == 10000
    verify = lab.jobs.replay(v)["jobId"]
    await lab.jobs.process(verify, {"replay": v})
    assert lab.jobs.list()[0]["state"] == "DONE"
    for day in range(5):
        t = (START + timedelta(days=day)).replace(hour=15).isoformat()
        with lab.db() as db:
            db.execute(
                "INSERT INTO paper_point VALUES(?,?,?)",
                (v, t, json.dumps({"at": t, "equity": 10000, "benchmark": 9990})),
            )
        review = Review(at=t, text="样本待观察", model="test")
        lab.save_review(v, review)
        lab.save_review(v, review)
    with lab.db() as db:
        jobs = db.execute(
            "SELECT request FROM paper_job WHERE state='QUEUED'"
        ).fetchall()
    assert len(jobs) == 1 and json.loads(jobs[0][0])["parent"] == v
