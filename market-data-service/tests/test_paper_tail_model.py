from datetime import datetime
from types import SimpleNamespace

import pytest

from finscope_market_data.forecast.trading_calendar import next_session
from finscope_market_data.overnight.engine import expected_times
from finscope_market_data.paper import factor_proposal, tail_model
from finscope_market_data.paper.engine import SHANGHAI


def panel():
    result = {}
    for stock in range(5):
        code = f"60000{stock}"
        day = datetime(2026, 5, 6).date()
        rows = []
        for i in range(20):
            for j, clock in enumerate(expected_times("15:00")):
                price = 9 + stock * .3 + i*.04 + j*.002
                rows.append(SimpleNamespace(observed_at=datetime.fromisoformat(f"{day}T{clock}").replace(tzinfo=SHANGHAI),
                                            open=price, close=price+.01, high=price+.02, low=price-.01,
                                            volume=50000, amount=50000*price, actionsVerified=True))
            day = next_session(day)
        result[code] = rows
    return result


def test_model_freezes_mature_labels_and_uses_next_executable_open():
    data = panel()
    code, bars = next(iter(data.items()))
    joined = bars[48*2].observed_at.isoformat()
    rows = tail_model.samples(code, bars, joined)
    assert rows[0]["signalDate"] == joined[:10]
    assert rows[0]["target"] == bars[48*3+2].open / bars[48*2+46].open - 1
    observations = [r for c, series in data.items() for r in tail_model.samples(c, series)]
    split = bars[-48*4].observed_at.date().isoformat()
    fit = tail_model.train(observations, split)
    assert fit["samples"] >= 50 and fit["lastLabel"] < split
    for row in observations:
        if row["exitDate"] >= split:
            row["target"] = 999
    assert fit == tail_model.train(observations, split)
    with pytest.raises(ValueError, match="覆盖不足"):
        tail_model.train(rows[:2], split)


def test_incomplete_next_open_and_unknown_corporate_actions_exclude_label():
    code, bars = next(iter(panel().items()))
    first = bars[48].observed_at.date().isoformat()
    bars[2*48+2].volume = None
    assert first not in {r["signalDate"] for r in tail_model.samples(code, bars)}
    bars[2*48+2].volume = 50000
    bars[2*48].actionsVerified = False
    assert first not in {r["signalDate"] for r in tail_model.samples(code, bars)}


def test_dated_snapshot_warms_daily_trends_without_exposing_pre_membership_bars(tmp_path):
    import gzip
    import hashlib
    import json
    from finscope_market_data.paper.research import ResearchJobs

    day = datetime(2026, 5, 6).date()
    rows = []
    for session in range(63):
        for clock in expected_times("15:00"):
            at = datetime.fromisoformat(f"{day}T{clock}").replace(tzinfo=SHANGHAI)
            price = 10 + session
            rows.append([at.isoformat(), price, price, price, price, 100, price * 100, False, True, []])
        day = next_session(day)
    start = rows[-4][0]
    jobs = ResearchJobs(SimpleNamespace(path=tmp_path / "paper.db"), None)
    jobs.root.mkdir()
    raw = json.dumps({"eligibleFrom": start, "bars": {"600000": rows}}).encode()
    digest = hashlib.sha256(raw).hexdigest()
    jobs.snapshot_path(digest).write_bytes(gzip.compress(raw))
    eligible = jobs.read_panel(digest)["600000"]
    assert [b.observed_at.isoformat() for b in eligible] == [row[0] for row in rows[-4:]]
    assert eligible[0].completedBars["1d"]["at"] == rows[-49][0]
    assert eligible[0].multiTimeframe["trend60d"] == pytest.approx(71 / 11 - 1)
    assert eligible[-1].completedBars["1d"]["at"] == rows[-1][0]


@pytest.mark.asyncio
async def test_frozen_pair_deploys_and_fills_together_in_forward_engine(tmp_path, monkeypatch):
    from datetime import timedelta
    import shutil
    from finscope_market_data.models import MinutePriceBar, StockSymbol
    from finscope_market_data.paper.lab import PaperLab
    from finscope_market_data.paper.research import Program
    from finscope_market_data.paper.python_runtime import image_id

    if not shutil.which("docker"):
        pytest.skip("Docker CLI unavailable")
    try:
        image = image_id()
    except ValueError:
        pytest.skip("strategy image unavailable")
    data = panel()
    observations = [r for code, bars in data.items() for r in tail_model.samples(code, bars)]
    proposal = factor_proposal.FactorProposal(hypothesis="量价与时段反转", factors=[
        "日内加速 | return5m - return15m | 比较两种趋势", "振幅 | -abs(range15m) | 偏好低波动"])
    formulas = factor_proposal.definitions(proposal, factor_proposal.INPUTS)
    fit = tail_model.train(observations, data["600000"][-48*4].observed_at.date().isoformat(), formulas=formulas)
    lab = PaperLab(tmp_path / "paper.db", lambda: None)
    request = dict(capital=10000, autoStart=True, pool="COHORT", source="dated", codes=list(data),
                   origin=tail_model.HYPOTHESIS)
    job = lab.jobs.submit(request)["jobId"]
    program = Program(name=tail_model.NAME, hypothesis=tail_model.HYPOTHESIS, factor="test", code=tail_model.source(fit))
    baseline = dict(**program.model_dump(), kind="python", image=image, snapshot="a"*64, sdkVersion=3, exDateAccounting=True)
    baseline["code"] = tail_model.source(fit, equal=True)
    clock = data["600000"][-96].observed_at
    monkeypatch.setattr("finscope_market_data.paper.lab.stamp", lambda: clock)
    lab.jobs.save_version(job, request, program, image, "a"*64, {"baselineRule": baseline})
    async def no_actions(*args):
        return []
    monkeypatch.setattr(lab, "action_events", no_actions)
    live = {code: [MinutePriceBar(symbol=StockSymbol(code=code, market="SH"), frequency_minutes=5,
                                  observed_at=b.observed_at, open=b.open, close=b.close, low=b.low, high=b.high,
                                  volume=b.volume, amount=b.amount) for b in bars[-240:]] for code, bars in data.items()}
    for i in (-52, -50, -48, -46):
        clock = data["600000"][i].observed_at + timedelta(seconds=5)
        with lab.db() as db:
            row = db.execute("SELECT a.*,v.spec FROM paper_account a JOIN paper_version v USING(id)").fetchone()
        await lab.update_account(row, {c: [b for b in series if b.observed_at <= clock]
                                       for c, series in live.items()}, clock)
    detail = lab.detail(1, "")
    for book in (detail["book"], detail["book"]["control"]):
        assert book["fills"] == 6 and not book["positions"] and book["fees"] > 0
    events = [e for point in detail["points"] for e in point["events"]]
    assert any(e["action"] == "成交" and "可卖" in e["signalReason"] for e in events)
    assert any(e["action"] == "成交" for p in detail["points"] for e in p["controlEvents"])


def test_formula_interpreter_rejects_missing_inputs_and_unavailable_operations():
    def define(text):
        return factor_proposal.definitions(factor_proposal.FactorProposal(hypothesis="测算", factors=[
            "第一项 | return5m | 观察趋势", "第二项 | " + text + " | 观察风险"]), ["return5m", "return15m"])

    for expr in ["return1d", "return5m ** 2", "return5m()", "2", "return5m"]:
        with pytest.raises(ValueError):
            define(expr)
    specs = define("abs(return5m) / return15m")
    assert factor_proposal.factor_values(specs, {"return5m": -.2}) == {"第一项": -.2}
    assert factor_proposal.factor_values(specs, {"return5m": -.2, "return15m": 0}) == {"第一项": -.2}
    assert factor_proposal.factor_values(specs, {"return5m": -.2, "return15m": .1})["第二项"] == 2
