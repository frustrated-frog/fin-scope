from datetime import datetime, timedelta
import gzip
import json
from types import SimpleNamespace

import pytest

from finscope_market_data.paper import analysis, research
from finscope_market_data.paper.lab import PaperLab

START = datetime(2026, 11, 16, 9, 35, tzinfo=research.SHANGHAI)


def panel(days=6, count=6, minutes=10):
    return {
        str(c): [
            SimpleNamespace(
                observed_at=START + timedelta(days=d, minutes=i * 5),
                close=10 * (1 + c * 0.001) ** (d * 48 + i),
                open=10,
                high=12,
                low=9,
                volume=10000,
                amount=100000,
                estimated_volume=False,
            )
            for d in range(days)
            for i in range(minutes)
        ]
        for c in range(count)
    }


def test_independent_factor_horizons_and_redundancy():
    data = panel(days=5)
    signals = [
        (
            b.observed_at,
            {
                "signal": {c: float(c) for c in data},
                "inverse": {c: -float(c) for c in data},
            },
        )
        for b in data["0"]
    ]
    result = analysis.factor_report(data, signals)
    for key in ["5m", "30m", "1d", "3d"]:
        h = result["signal"]["horizons"][key]
        assert h["observations"] > 0 and h["rankIC"] == pytest.approx(1)
        assert h["spread"] > 0 and result["inverse"]["horizons"][key]["spread"] < 0
    assert result["signal"]["horizons"]["5d"]["rankIC"] is None
    assert result["signal"]["correlations"]["inverse"] == pytest.approx(-1)
    assert analysis.coverage(data)["1"]["completeDays"] == 0


@pytest.mark.asyncio
@pytest.mark.parametrize("conclude", [False, True])
async def test_review_sees_only_development_and_persists_selected_candidate(
    tmp_path, monkeypatch, conclude
):
    lab = PaperLab(tmp_path / "lab.db", lambda: None)
    data = panel(days=5, count=1, minutes=30)
    req = dict(
        codes=["600000"],
        capital=10000,
        pool="WATCHLIST",
        source="test",
        origin="例",
        snapshot="a" * 64,
        autoStart=False,
    )
    job = lab.jobs.submit(req)["jobId"]
    monkeypatch.setattr(research, "image_id", lambda: "sha256:" + "1" * 64)
    monkeypatch.setattr(lab.jobs, "read_panel", lambda _: data)
    evaluations, judgments, holdouts = [], [], []

    def author(prompt, previous, error, ctx):
        with gzip.open(ctx["developmentFile"], "rt") as src:
            dev = json.load(src)["0"]
        assert len(dev) == 120 and dev[-1]["at"][:10] == "2026-11-19"
        return research.Program(
            name="候选",
            hypothesis="假设",
            factor="动量",
            code=f"program{len(evaluations)}",
        )

    def evaluate(dev, code, *_):
        assert dev["0"][-1].observed_at.date().isoformat() == "2026-11-19"
        evaluations.append(code)
        return {"netReturn": -0.02, "factors": {}}

    def assess(evidence):
        assert "validationReturn" not in json.dumps(evidence)
        judgments.append(len(evidence["candidates"]))
        return research.Decision(
            action="REVISE"
            if len(judgments) == 1
            else ("STOP" if conclude else "FREEZE"),
            candidate=1,
            reason="开发证据",
            direction="降低换手",
        )

    def validate(data, program, *_, **kwargs):
        holdouts.append(program.code)
        if len(holdouts) == 1:
            raise ValueError("validation failure")
        return {"curve": [], "fills": 0, "fees": 0}

    for name, fn in dict(
        author=author, evaluate=evaluate, assess=assess, simulate=validate
    ).items():
        monkeypatch.setattr(research, name, fn)
    if not conclude:
        with pytest.raises(ValueError, match="validation failure"):
            await lab.jobs.process(job, req)
    await lab.jobs.process(job, req)
    row = lab.jobs.list()[0]
    assert evaluations == ["program0", "program1"] and judgments == [1, 2]
    if conclude:
        assert row["state"] == "CONCLUDED" and not holdouts and not row["version"]
    else:
        assert holdouts == ["program0", "program0"]
        assert lab.detail(row["version"], "")["history"]["selectedCandidate"] == 1


def test_ex_date_entitlements_cannot_fund_orders_and_skip_crossing_labels():
    from finscope_market_data.paper.engine import advance, new_book
    from finscope_market_data.paper.actions import annotate
    from test_paper_lab import RULE

    data = panel(days=2, minutes=3)
    event = dict(
        ex_date="2026-11-17",
        dividend_per_share=1.0,
        per_share_bonus=0.1,
        allotment_ratio=0,
        event_types=["CASH_DIVIDEND"],
    )
    rows = [[b.observed_at.isoformat()] for b in data["0"]]
    annotate(rows, [event])
    assert [i for i, r in enumerate(rows) if r[-1]] == [3]
    reports = []
    book = new_book(1000)
    book.update(
        cash=0,
        anchors={"0": 10},
        positions={"0": dict(price=10, cost=1000, lots=[["2026-11-15", 100]])},
    )
    for i, b in enumerate(data["0"]):
        b.actions = rows[i][-1]
        b.actionsVerified = True
        if i >= 3:
            b.close = 9 / 1.1
        advance(book, RULE, {"0": [b]}, b.observed_at, b.observed_at, 1000)
        reports.append((b.observed_at, {"x": {c: float(c) for c in data}}))
    assert book["dividendReceivable"] == 100 and book["cash"] == 0
    assert (
        book["positions"]["0"]["lots"][-1] == ["9999-12-31", 10]
        and book["equity"] == 1000
    )
    outcome = analysis.factor_report(data, reports)["x"]["horizons"]["1d"]
    assert outcome["pairs"] == 3 * 5  # 3 same-clock pairs for each unaffected stock


@pytest.mark.asyncio
async def test_forward_holdout_waits_for_new_closing_data(tmp_path, monkeypatch):
    lab = PaperLab(tmp_path / "lab.db", lambda: None)
    data = panel(days=4, count=1, minutes=8)
    req = dict(
        codes=["600000"],
        capital=10000,
        pool="WATCHLIST",
        source="test",
        origin="例",
        forwardDecision="检验近期费用",
        forwardEvidence={"lastDay": "2026-11-18"},
        autoStart=False,
    )
    job = lab.jobs.submit(req)["jobId"]

    async def snapshot(codes, job):
        return "a" * 64

    monkeypatch.setattr(lab.jobs, "snapshot", snapshot)
    monkeypatch.setattr(lab.jobs, "read_panel", lambda digest: data)
    monkeypatch.setattr(research, "image_id", lambda: "sha256:" + "0" * 64)
    await lab.jobs.process(job, req)
    assert lab.jobs.list()[0]["state"] == "WAITING_DATA"
    target = data["0"][-1].observed_at.replace(hour=15, minute=0)
    from finscope_market_data.overnight.engine import expected_times
    from copy import copy

    for clock in expected_times("15:00")[8:]:
        bar = copy(data["0"][-1])
        bar.observed_at = target.replace(hour=int(clock[:2]), minute=int(clock[3:]))
        data["0"].append(bar)

    async def develop(job, request, dev, image, parent, directory):
        assert all(b.observed_at.date().isoformat() < "2026-11-19" for b in dev["0"])
        return None

    monkeypatch.setattr(lab.jobs, "develop", develop)
    await lab.jobs.process(job, req)
    with lab.db() as db:
        assert (
            json.loads(
                db.execute(
                    "select request from paper_job where id=?", (job,)
                ).fetchone()[0]
            )["snapshot"]
            == "a" * 64
        )


def test_forward_factor_observations_match_consecutive_closes_only():
    state = {}
    for day, prices, actions in [
        ("2026-11-16", [10, 10, 10, 10], {}),
        ("2026-11-17", [10, 11, 12, 8], {"3": "2026-11-17"}),
        ("2026-11-19", [10, 13, 16, 9], {}),
    ]:
        analysis.observe_close(
            state,
            {
                "at": day + "T15:00:00+08:00",
                "factorClose": {
                    "prices": dict(zip("0123", prices)),
                    "factors": {"strength": dict(zip("0123", range(4)))},
                    "lastEx": actions,
                },
            },
        )
    assert state["dailyFactorRankIC"]["strength"] == [pytest.approx(1)]


def test_factor_diagnostics_do_not_change_trading_state():
    from finscope_market_data.paper.python_runtime import image_id, PythonStrategy
    from finscope_market_data.paper.engine import new_book

    try:
        image = image_id()
    except (ValueError, OSError):
        pytest.skip("requires local strategy runtime image")
    code = """
seen=False
def factors(bars):
    global seen
    seen=True
    return {"factor":{c:1 for c in bars}}
def decide(ctx):
    return {"weights":{}, "scores":{c:int(seen) for c in ctx["bars"]}, "state":{}}
"""
    data = panel(days=1, count=1, minutes=1)
    with PythonStrategy(code, image, collect_factors=True) as strategy:
        _, scores = strategy.decide(data, new_book(1000), START, 1000)
        assert scores == {"0": 0} and strategy.factor_values == {"factor": {"0": 1}}
