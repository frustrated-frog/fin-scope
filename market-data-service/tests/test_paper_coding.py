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
    monkeypatch.setattr(
        "finscope_market_data.paper.lab.stamp", lambda: START + timedelta(days=5)
    )
    calls = []

    def write(prompt, previous, error, context):
        calls.append(error)
        code = "def factors(bars):\n    return {'price':{c:v[-1]['close'] for c,v in bars.items()}}\ndef decide(ctx):\n    return {'weights':{'600000':.5},'scores':{'600000':1},'state':{}}"
        return research.Program(
            name="策略",
            hypothesis="试验",
            factor="因子",
            code=code if error else "def decide(:",
        )

    monkeypatch.setattr(research, "author", write)
    monkeypatch.setattr(
        research,
        "assess",
        lambda evidence: research.Decision(
            action="FREEZE", candidate=1, reason="检验运行", direction=""
        ),
    )
    req = research.JobRequest(
        codes=["600000"],
        capital=10000,
        pool="WATCHLIST",
        source="test",
        origin="test",
        autoIterate=True,
        requestId="a783f4cc-aa4b-49df-8d12-6912ea054c74",
    )
    job = lab.jobs.enqueue(req)["jobId"]
    assert lab.jobs.enqueue(req)["jobId"] == job
    await lab.jobs.process(job, req.model_dump(mode="json"))
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
    assert book["fills"] == 1 and lab.detail(v, "")["book"]["equity"] == 10000
    verify = lab.jobs.replay(v)["jobId"]
    await lab.jobs.process(verify, {"replay": v})
    assert lab.jobs.list()[0]["state"] == "DONE"
    for day in range(5):
        t = (START + timedelta(days=day)).replace(hour=15).isoformat()
        with lab.db() as db:
            db.execute(
                "INSERT INTO paper_point VALUES(?,?,?)",
                (v, t, json.dumps({"at": t, "equity": 10000, "benchmark": 9990, "events":[]})),
            )
        review = Review(at=t, text="样本待观察", model="test")
        lab.save_review(v, review)
        lab.save_review(v, review)
    with lab.db() as db:
        jobs = db.execute(
            "SELECT request FROM paper_job WHERE state='QUEUED'"
        ).fetchall()
    assert not jobs and lab.detail(v, "")["forwardEvidence"]["status"] == "继续观察"
    for day in [5, 6]:
        t = (START + timedelta(days=day)).replace(hour=15).isoformat()
        point = dict(
            at=t,
            equity=9970,
            benchmark=10000,
            events=[
                dict(
                    action="成交",
                    fee=5,
                    quantity=100,
                    code="600000",
                    side="BUY",
                    reason="test",
                )
            ]
            * 2,
        )
        with lab.db() as db:
            db.execute(
                "INSERT INTO paper_point VALUES(?,?,?)", (v, t, json.dumps(point))
            )
            lab.jobs.after_review(db, v, Review(at=t, text="反馈", model="test"))
    queued = [x for x in lab.jobs.list() if x["state"] == "QUEUED"]
    assert len(queued) == 1
    with lab.db() as db:
        auto = json.loads(
            db.execute(
                "SELECT request FROM paper_job WHERE id=?", (queued[0]["id"],)
            ).fetchone()[0]
        )
    assert (
        auto["parent"] == v
        and auto["forwardEvidence"]["fills"] == 4
        and "snapshot" not in auto
    )
    monkeypatch.setattr(
        research,
        "review_forward",
        lambda evidence: research.ForwardDecision(
            action="OBSERVE", reason="等待更多独立交易"
        ),
    )
    await lab.jobs.process(queued[0]["id"], auto)
    assert lab.jobs.list()[0]["state"] == "CONCLUDED"


@pytest.mark.asyncio
async def test_job_can_refresh_an_insufficient_snapshot_on_retry(tmp_path, monkeypatch):
    lab = PaperLab(tmp_path / "lab.db", lambda: None)
    req = research.JobRequest(
        codes=["600000"], capital=10000, pool="WATCHLIST", source="test", origin="test"
    )
    job = lab.jobs.enqueue(req)["jobId"]
    monkeypatch.setattr(research, "image_id", lambda: "sha256:" + "0" * 64)
    monkeypatch.setattr(
        lab.jobs,
        "read_panel",
        lambda digest: {"600000": [SimpleNamespace(observed_at=START)]},
    )
    with lab.db() as db:
        db.execute(
            "UPDATE paper_job SET request=json_set(request,'$.snapshot','old') WHERE id=?",
            (job,),
        )
    with pytest.raises(ValueError, match="不足三个交易日"):
        await lab.jobs.process(job, {**req.model_dump(), "snapshot": "old"})
    with lab.db() as db:
        row = db.execute("SELECT request FROM paper_job WHERE id=?", (job,)).fetchone()
    assert "snapshot" not in json.loads(row[0])


def test_rebalance_uses_only_actual_sale_proceeds():
    from finscope_market_data.paper.engine import INTERVAL

    prices = bars([10, 10, 10])["600000"]
    history = {"600000": prices, "000001": prices}
    for bought, expected in [("2026-11-20", 2), (str(START.date()), 0)]:
        book = new_book(0)
        book["positions"]["600000"] = {
            "price": 10,
            "cost": 10000,
            "lots": [[bought, 1000]],
        }
        for code, side in [("000001", "BUY"), ("600000", "SELL")]:
            book["pending"].append(
                dict(
                    code=code,
                    side=side,
                    budget=9000,
                    available=(START - INTERVAL).isoformat(),
                )
            )
        point = advance(book, RULE, history, START, START, 10000)
        assert [event["side"] for event in point["events"][:2]] == ["SELL", "BUY"]
        assert book["fills"] == expected and book["cash"] >= 0
