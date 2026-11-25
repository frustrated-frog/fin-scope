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

    def validate(data, program, *_):
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
