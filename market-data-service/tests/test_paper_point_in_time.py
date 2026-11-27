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


@pytest.mark.asyncio
async def test_unverified_history_keeps_intraday_features_and_protects_overnight_ledger(tmp_path, monkeypatch):
    lab = PaperLab(tmp_path / "paper.db", lambda: None)
    symbol = StockSymbol(code="600000", market="SH")
    async def unavailable(*args):
        return None
    monkeypatch.setattr(lab, "action_events", unavailable)
    times = [(23, 15, 0), (24, 9, 35), (24, 9, 40), (24, 9, 45)]
    bars = [MinutePriceBar(symbol=symbol, observed_at=datetime(2026, 11, d, h, m, tzinfo=SHANGHAI),
                           frequency_minutes=5, open=10, high=11, low=9, close=10.5, volume=500)
            for d, h, m in times]
    end = bars[-1].observed_at
    history = await lab.forward_actions({"600000": bars}, {}, str(end.date()), end)
    enriched = await lab.features.attach("600000", history["600000"], end)
    assert enriched[-1].shortHistory and set(enriched[-1].completedBars) == {"5m", "15m"}
    assert enriched[0].observed_at == bars[1].observed_at
    book = {"anchors": {"600000": 10}, "positions": {"600000": {"lots": [["2026-11-23", 100]]}}}
    tradable = await lab.forward_actions({"600000": bars, "600001": bars}, book, "2026-11-23", end)
    assert set(tradable) == {"600001"}
    assert "lastObserved" not in book and book["positions"]["600000"]["lots"] == [["2026-11-23", 100]]


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


def test_measured_bars_keep_missing_turnover_and_avoid_conflicting_prices(tmp_path):
    from finscope_market_data.overnight.store import OvernightStore
    from finscope_market_data.overnight.models import MinuteBar
    from datetime import timedelta

    archive = OvernightStore(tmp_path / "archive.db")
    lab = PaperLab(tmp_path / "research.db", lambda: None, archive)
    at = datetime(2026, 11, 24, 15, tzinfo=SHANGHAI)
    first = MinutePriceBar(
        symbol=StockSymbol(market="SH", code="600000"),
        observed_at=at,
        frequency_minutes=5,
        open=10,
        high=11,
        low=9,
        close=10,
        volume=2000,
    )
    second = first.model_copy(update={"observed_at": at + timedelta(days=1)})
    archive.import_history(
        "600000.SH",
        [
            MinuteBar(
                ended_at=at.replace(tzinfo=None),
                open=9.99,
                high=11,
                low=9,
                close=10,
                amount=18000,
            )
        ],
        "old",
        at.replace(tzinfo=None),
    )
    lab.datasets.save_quotes("600000", [first, second], second.observed_at)
    bars = lab.datasets.bars("600000", second.observed_at.replace(tzinfo=None))
    assert bars[0].volume is None and bars[0].open == 9.99
    assert bars[1].volume == 2000 and bars[1].amount is None


@pytest.mark.asyncio
async def test_cold_reader_reuses_concurrent_pool_refresh(tmp_path):
    import asyncio
    import threading
    from finscope_market_data.overnight.store import OvernightStore

    entered = threading.Event()
    finish = threading.Event()
    calls = []

    class Provider:
        source = "fixture"

        def fetch(self, *args):
            calls.append(args)
            entered.set()
            assert finish.wait(5)
            return []

    lab = PaperLab(
        tmp_path / "paper.db",
        lambda: None,
        OvernightStore(tmp_path / "archive.db"),
        Provider(),
    )
    now = datetime(2026, 11, 24, 16, tzinfo=SHANGHAI)
    background = asyncio.create_task(lab.datasets.ensure(["600000"], now))
    try:
        assert await asyncio.to_thread(entered.wait, 5)
        reader = asyncio.create_task(lab.datasets.ensure(["600000"], now, wait=True))
        await asyncio.sleep(0.1)
        assert not reader.done()
    finally:
        finish.set()
        await background
    await reader
    assert len(calls) == 1 and lab.datasets.list()[0]["status"] == "RETRY"


def test_archive_counts_complete_clocks_and_validates_volume(tmp_path):
    from datetime import timedelta
    from types import SimpleNamespace
    from finscope_market_data.paper.datasets import coverage_counts
    from finscope_market_data.overnight.engine import expected_times
    from finscope_market_data.overnight.store import OvernightStore
    from finscope_market_data.overnight.models import MinuteBar

    day = datetime(2026, 11, 24)
    rows = [
        SimpleNamespace(
            ended_at=datetime.combine(
                day.date() - timedelta(days=i), datetime.strptime(clock, "%H:%M").time()
            ),
            volume=100,
        )
        for i in range(123)
        for clock in expected_times("15:00")[1:]
    ]
    assert len(rows) > 120 * 48 and coverage_counts(rows) == (0, len(rows))
    archive = OvernightStore(tmp_path / "archive.db")
    lab = PaperLab(tmp_path / "paper.db", lambda: None, archive)
    at = day.replace(hour=15)
    archive.import_history(
        "600000.SH",
        [MinuteBar(ended_at=at, open=10, high=11, low=9, close=10, amount=20000)],
        "old",
        at,
    )
    quote = MinutePriceBar(
        symbol=StockSymbol(code="600000", market="SH"),
        observed_at=at.replace(tzinfo=SHANGHAI),
        frequency_minutes=5,
        open=10,
        high=11,
        low=9,
        close=10,
        volume=20,
    )
    lab.datasets.save_quotes("600000", [quote], quote.observed_at)
    assert lab.datasets.bars("600000", at)[0].volume is None


def test_requested_return_targets_are_validated_and_filter_reports():
    from types import SimpleNamespace
    from finscope_market_data.paper.analysis import factor_report
    from finscope_market_data.paper.return_targets import selected_targets

    assert len(selected_targets(None)) == 10
    with pytest.raises(ValueError, match="收益目标"):
        selected_targets(["tomorrow"])
    at = datetime(2026, 11, 24, 14, 45, tzinfo=SHANGHAI)
    panel = {"600000": [SimpleNamespace(observed_at=at, close=10)]}
    report = factor_report(panel, [(at, {"momentum": {"600000": 1}})], ["30m"])
    assert list(report["momentum"]["horizons"]) == ["30m"]


@pytest.mark.asyncio
async def test_factor_holdout_waits_for_close_and_retries_same_day(
    tmp_path, monkeypatch
):
    from types import SimpleNamespace
    from finscope_market_data.paper import research

    lab = PaperLab(tmp_path / "paper.db", lambda: None)
    key = "a" * 64
    request = {"snapshot": "frozen", "requiredFactor": key}
    job = lab.jobs.submit(request)["jobId"]
    now = datetime(2026, 11, 25, 10, tzinfo=SHANGHAI)
    observations = [
        SimpleNamespace(observed_at=now.replace(day=d), volume=100)
        for d in (23, 24, 25)
    ]
    monkeypatch.setattr(research, "image_id", lambda: "test-image")
    monkeypatch.setattr(
        research,
        "datetime",
        type("Clock", (datetime,), {"now": classmethod(lambda cls, tz: now)}),
    )
    monkeypatch.setattr(lab.jobs, "read_panel", lambda digest: {"600000": observations})
    monkeypatch.setattr(
        lab.jobs,
        "factor_assets",
        lambda asset: {"studies": [{"knowledgeThrough": "2026-11-23T18:00:00+08:00"}]},
    )
    await lab.jobs.process(job, request)
    with lab.db() as db:
        row = db.execute(
            "SELECT state,request FROM paper_job WHERE id=?", (job,)
        ).fetchone()
        assert row["state"] == "WAITING_DATA"
        saved = json.loads(row["request"])
        assert saved["checkedDay"] == "2026-11-24" and "snapshot" not in saved
