from datetime import datetime
from types import SimpleNamespace

import pytest

from finscope_market_data.forecast.trading_calendar import next_session
from finscope_market_data.overnight.engine import expected_times
from finscope_market_data.paper import tail_model
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
    fit = tail_model.train(observations, data["600000"][-48*4].observed_at.date().isoformat())
    lab = PaperLab(tmp_path / "paper.db", lambda: None)
    request = dict(capital=10000, autoStart=True, pool="COHORT", source="dated", codes=list(data),
                   origin=tail_model.HYPOTHESIS)
    job = lab.jobs.submit(request)["jobId"]
    program = Program(name=tail_model.NAME, hypothesis=tail_model.HYPOTHESIS, factor="test", code=tail_model.source(fit))
    baseline = dict(**program.model_dump(), kind="python", image=image, snapshot="a"*64, exDateAccounting=True)
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
