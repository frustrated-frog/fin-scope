from datetime import datetime
import json

import pytest

from finscope_market_data.models import MinutePriceBar, StockSymbol
from finscope_market_data.paper.engine import SHANGHAI
from finscope_market_data.paper.lab import PaperLab


@pytest.mark.asyncio
async def test_ex_date_window_survives_next_bar_and_restart(tmp_path, monkeypatch):
    symbol = StockSymbol(code="600000", market="SH")

    def bar(day, minute):
        at = datetime(2026, 11, day, 9, minute, tzinfo=SHANGHAI)
        return MinutePriceBar(
            symbol=symbol,
            observed_at=at,
            frequency_minutes=5,
            open=10,
            high=11,
            low=9,
            close=10,
            volume=100,
        )

    queries = []

    async def actions(router, limit, code, start, end):
        queries.append((start, end))
        return [{"ex_date": "2026-11-24"}]

    monkeypatch.setattr("finscope_market_data.paper.lab.read_actions", actions)
    lab = PaperLab(tmp_path / "paper.db", lambda: None)
    book = {}
    history = [bar(23, 35), bar(24, 35), bar(24, 40)]
    first = await lab.forward_actions(
        {"600000": history}, book, "2026-11-23", history[-1].observed_at
    )
    assert first["600000"] == [history[1], first["600000"][-1]]
    assert first["600000"][-1].actions and first["600000"][-1].windowClipped
    book["lastObserved"] = {"600000": history[-1].observed_at.isoformat()}
    restored = PaperLab(tmp_path / "paper.db", lambda: None)
    history.append(bar(24, 45))
    second = await restored.forward_actions(
        {"600000": history},
        json.loads(json.dumps(book)),
        "2026-11-23",
        history[-1].observed_at,
    )
    assert second["600000"] == history[1:] and len(queries) == 1


def test_research_memory_requires_latest_information_date(tmp_path):
    lab = PaperLab(tmp_path / "lab.db", lambda: None)
    j = lab.jobs.submit({"origin": "test"})["jobId"]
    dates = lab.jobs.information_dates(
        j,
        {"generatedDirection": True, "forwardEvidence": {"lastDay": "2026-11-12"}},
        "2026-10-30T15:00:00+08:00",
    )
    assert (
        dates["knowledgeThrough"]
        == dates["feedbackThrough"]
        == "2026-11-12T15:00:00+08:00"
    )
    with lab.db() as db:
        for i, metrics in enumerate(
            [{"information": dates}, {"developmentThrough": "2026-10-01"}]
        ):
            db.execute(
                "insert into paper_candidate values(?,?,?,?,?,?)",
                (
                    j,
                    i + 1,
                    '{"name":"候选"}',
                    json.dumps(metrics),
                    '{"action":"STOP"}',
                    "test",
                ),
            )
    assert not lab.jobs.memory("2026-11-12T14:55:00+08:00")
    assert len(lab.jobs.memory("2026-11-12T15:00:00+08:00")) == 1


@pytest.mark.asyncio
async def test_dataset_failure_isolated_and_backoff_persisted(tmp_path):
    from finscope_market_data.overnight.store import OvernightStore, compatible_volume
    from finscope_market_data.overnight.models import MinuteBar
    from finscope_market_data.providers.base import ProviderError

    end = datetime(2026, 11, 24, 15)
    original = MinuteBar(
        ended_at=end, open=10, high=10.1, low=9.9, close=10, amount=10000.2
    )
    measured = original.model_copy(update={"volume": 1000, "amount": 10000})
    assert compatible_volume(original, measured)
    assert not compatible_volume(original, measured.model_copy(update={"close": 10.01}))
    store = OvernightStore(tmp_path / "archive.db")
    store.import_history("600001.SH", [original], "old", end)
    calls = []

    class Provider:
        source = "fixture"

        def fetch(self, code, through):
            calls.append(code)
            if code == "600000.SH":
                raise ProviderError("TIMEOUT", "timeout")
            return [measured]

    lab = PaperLab(tmp_path / "paper.db", lambda: None, store, Provider())
    now = datetime(2026, 11, 24, 16, tzinfo=SHANGHAI)
    for _ in range(2):
        await lab.datasets.ensure(["600000", "600001"], now)
    assert sorted(calls) == ["600000.SH", "600001.SH"]
    assert [r["status"] for r in lab.datasets.list()] == ["RETRY", "PARTIAL"]
    updated = store.research_bars("600001.SH", end)[0]
    assert updated.volume == 1000 and updated.amount == original.amount


def test_return_targets_use_timed_entry_and_distinguish_next_open_close(tmp_path):
    from types import SimpleNamespace
    from finscope_market_data.paper.return_targets import trading_targets

    def bar(day, clock, o, h, l, c, v):
        at = datetime.fromisoformat("2026-11-" + day + "T" + clock + ":00+08:00")
        return SimpleNamespace(observed_at=at, open=o, high=h, low=l, close=c, volume=v)

    bars = [
        bar("23", "14:45", 9, 11, 8, 9, 50000),
        bar("23", "14:55", 10, 11, 9, 10, 50000),
        bar("24", "09:35", 11, 11, 11, 11, 50000),
        bar("24", "09:40", 12, 13, 11, 12, 50000),
        bar("24", "15:00", 13, 14, 12, 13, 50000),
    ]
    import numpy as np

    def results():
        return dict(
            trading_targets({"600000": bars}, [b.observed_at for b in bars], ["600000"])
        )

    targets = results()
    assert np.isnan(targets["tail_next_open"][0, 0]) and targets["tail_next_close"][
        0, 0
    ] == pytest.approx(0.3)
    assert targets["intraday_first_sell"][0, 0] == pytest.approx(0.2) and np.isnan(
        targets["next_day_mfe"][0, 0]
    )
    bars[2].estimated_volume = True
    assert np.isnan(results()["intraday_first_sell"][0, 0])


def test_factor_assets_deduplicate_replay_and_isolate_research_evidence(tmp_path):
    from finscope_market_data.paper import factor_library
    from finscope_market_data.paper.python_runtime import PythonStrategy, image_id
    from finscope_market_data.paper.engine import new_book
    from types import SimpleNamespace

    lab = PaperLab(tmp_path / "paper.db", lambda: None)
    source = "def factors(bars):\n    return {'strength':{c:series[-1]['close']*2 for c,series in bars.items()}}\n"
    decide = "def decide(ctx):\n    return {'weights':{},'scores':{},'state':{}}"
    for day, multiplier in [("2026-10-01", 2), ("2026-11-01", 2), ("2026-11-11", 3)]:
        job = lab.jobs.submit({"origin": "test"})["jobId"]
        program = {
            "code": source.replace("*2", "*" + str(multiplier)) + decide + "\n# " + day,
            "name": "候选",
            "factor": "收盘强度",
        }
        metrics = {
            "factors": {"strength": {"horizons": {}, "correlations": {}}},
            "information": {"knowledgeThrough": day + "T15:00:00+08:00"},
        }
        with lab.db() as db:
            db.execute(
                "insert into paper_candidate values(?,?,?,?,?,?)",
                (job, 1, json.dumps(program), json.dumps(metrics), None, "img"),
            )
    assets = lab.jobs.factor_assets()
    assert len(assets) == 2 and sorted(a["studies"] for a in assets) == [1, 2]
    with lab.db() as db:
        allowed = factor_library.assets(db, "2026-10-10T15:00:00+08:00")
    assert len(allowed) == 1
    key, asset = next(iter(allowed.items()))
    assert len(asset["studies"]) == 1 and "def decide" not in asset["source"]
    frozen, refs = factor_library.bind(
        "def factors(bars):\n    return {'linked':library_factor("
        + repr(key)
        + ",bars)}\n"
        + decide,
        allowed,
    )
    assert refs == [key] and factor_library.bind(frozen, allowed)[0] == frozen
    with pytest.raises(ValueError, match="当前开发时点"):
        factor_library.bind(
            "def factors(bars):\n    return library_factor('unknown',bars)", allowed
        )
    try:
        image = image_id()
    except (ValueError, OSError):
        pytest.skip("requires local runtime image")
    at = datetime(2026, 10, 2, 10, tzinfo=SHANGHAI)
    bar = SimpleNamespace(
        observed_at=at, open=10, high=11, low=9, close=10, amount=10000, volume=1000
    )
    with PythonStrategy(frozen, image, collect_factors=True) as strategy:
        strategy.decide({"600000": [bar]}, new_book(10000), at, 10000)
        assert strategy.factor_values == {"linked": {"600000": 20}}
