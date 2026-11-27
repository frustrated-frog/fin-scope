from datetime import date
import json

import numpy as np
import pytest

from finscope_market_data.paper import account_risk
from finscope_market_data.paper.lab import PaperLab


def sample(values):
    return {i: (i - 1, v) for i, v in enumerate(values)}


def test_cash_weighted_euler_and_market_decomposition_with_a_hedge():
    market = np.linspace(-0.03, 0.03, 60)
    rows = [{"code": "A", "weight": 0.5}, {"code": "B", "weight": 0.25}]
    risk = account_risk.factor_risk(rows, [sample(2 * market), sample(-market)], sample(market))
    pnl = 0.75 * market
    assert risk["beta"] == pytest.approx(0.75)
    assert risk["annualVol"] == pytest.approx(np.std(pnl, ddof=1) * np.sqrt(252))
    assert risk["systematicShare"] == pytest.approx(1)
    assert [r["riskShare"] for r in rows] == pytest.approx([4 / 3, -1 / 3])
    assert sum(r["marginalVol"] * r["weight"] for r in rows) == pytest.approx(risk["annualVol"])
    assert risk["strongestPair"]["rho"] == pytest.approx(-1)
    assert risk["var95"] == pytest.approx(-pnl[3])
    assert risk["es95"] == pytest.approx(-np.mean(pnl[:3]))


def test_sparse_samples_are_explicit_and_constant_series_have_no_risk_share():
    rows = [{"code": "a", "weight": 1, "riskShare": None}]
    short = sample([0] * 11)
    result = account_risk.factor_risk(rows, [short], short)
    assert result["marketDays"] == result["days"] == 11
    assert result["annualVol"] is None
    flat = sample([0] * 21)
    result = account_risk.factor_risk(rows, [flat], flat)
    assert result["annualVol"] == 0 and rows[0]["riskShare"] is None
    assert result["beta"] is None and result["var95"] is None


def test_archived_account_marks_and_exact_matched_quotes_at_selected_cutoff(tmp_path):
    lab = PaperLab(tmp_path / "paper.db", None)
    first = "2026-11-24T15:00:00+08:00"
    second = "2026-11-25T11:25:00+08:00"
    last = "2026-11-26T15:00:00+08:00"
    code = "600000"
    book = {"last": last, "positions": {}}
    with lab.db() as db:
        db.execute("INSERT INTO paper_version VALUES(1,'v','{\"capital\":1000}','{}',?)", (first,))
        db.execute(
            "INSERT INTO paper_account(id,started,book,status) VALUES(1,?,?,?)",
            (first, json.dumps(book), "RUNNING"),
        )
        db.execute("CREATE TABLE paper_measured_bar(code TEXT, at TEXT, close REAL)")
        db.execute("INSERT INTO paper_measured_bar VALUES(?,?,?)", (code, second[:19], 6))
        for at, cash, equity, holdings, extra in [
            (first, 100, 1100, {code: 100}, {"positionValues": {code: 1000}}),
            (second, 200, 800, {code: 100}, {}),
            (last, 1200, 1200, {}, {}),
        ]:
            point = dict(at=at, cash=cash, equity=equity, holdings=holdings, **extra)
            db.execute("INSERT INTO paper_point VALUES(1,?,?)", (at, json.dumps(point)))

    def report(day):
        return account_risk.analyze(lab, 1, date.fromisoformat(day), 60)

    assert report(first[:10])["source"] == "SNAPSHOT"
    mid = report(second[:10])
    assert mid["source"] == "MATCHED_QUOTES" and mid["throughDay"] == first[:10]
    assert mid["exposure"] == 0.75 and mid["drawdown"]["recoveredAt"] is None
    assert mid["drawdown"]["maximum"] == pytest.approx(1 - 800 / 1100)
    end = report(last[:10])
    assert end["state"] == "CASH" and end["drawdown"]["recoveredAt"] == last
    assert report("2026-11-27")["state"] == "NO_OBSERVATION"
    with lab.db() as db:
        db.execute("UPDATE paper_measured_bar SET close=8")
    assert report(second[:10])["state"] == "UNPRICED"
