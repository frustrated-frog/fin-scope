"""Exposure of an observed paper portfolio at its recorded valuation, in account-equity units."""

from datetime import date, datetime, time, timedelta
import json
from math import ceil, isfinite, sqrt

from fastapi import HTTPException
import numpy as np

from finscope_market_data.financial_statistics import BENCHMARK, TRADING_YEAR, daily_sample
from finscope_market_data.forecast.trading_calendar import previous_session
from .engine import SHANGHAI, money


def drawdowns(curve, capital, started):
    peak, peak_at = capital, started
    deepest = current = 0.0
    trough = high = recovered = None
    for at, equity in curve:
        if equity >= peak:
            if deepest and recovered is None:
                recovered = at
            peak, peak_at = equity, at
        current = max(0.0, 1 - equity / peak)
        if current > deepest:
            deepest, high, trough, recovered = current, peak_at, at, None
    age = (date.fromisoformat(curve[-1][0][:10]) - date.fromisoformat(peak_at[:10])).days if current else 0
    return dict(
        current=current,
        maximum=deepest,
        peakAt=high,
        troughAt=trough,
        recoveredAt=recovered,
        underwaterDays=age,
        observations=len(curve),
    )


def valuation(db, point, book):
    holdings = point.get("holdings")
    if holdings is None:
        return None, None, None
    source = "SNAPSHOT"
    rights = point.get("receivable")
    values = point.get("positionValues")
    if values is None:
        if book.get("last") == point["at"]:
            positions = book["positions"]
            if {c: sum(q for _, q in p["lots"]) for c, p in positions.items()} != holdings:
                return None, None, None
            values = {c: money(p["price"] * holdings[c]) for c, p in positions.items()}
            source, rights = "BOOK", book.get("dividendReceivable", 0)
        else:
            values = {}
            source = "MATCHED_QUOTES"
            has_quotes = db.execute(
                "SELECT 1 FROM sqlite_master WHERE type='table' AND name='paper_measured_bar'"
            ).fetchone()
            local_at = (
                datetime.fromisoformat(point["at"]).astimezone(SHANGHAI).replace(tzinfo=None).isoformat()
            )
            for code, quantity in holdings.items():
                if not has_quotes or code in point.get("stale", []):
                    return None, None, None
                row = db.execute(
                    "SELECT close FROM paper_measured_bar WHERE code=? AND at=?", (code, local_at)
                ).fetchone()
                if not row or row[0] is None:
                    return None, None, None
                values[code] = money(row[0] * quantity)
            # Legacy records have no separate receivable field. A zero residual is required.
            rights = rights or 0
    if set(values) != set(holdings) or any(not isfinite(v) or v <= 0 for v in values.values()):
        return None, None, None
    residual = point["equity"] - point["cash"] - (rights or 0) - sum(values.values())
    if abs(residual) > 0.011 + 0.005 * len(values):
        return None, None, None
    return values, source, rights


def sample_start(end, window):
    day = end + timedelta(days=1)
    for _ in range(window):
        previous = previous_session(day)
        if previous is None:
            previous = day - timedelta(days=1)
            while previous.weekday() >= 5:
                previous -= timedelta(days=1)
        day = previous
    return day


def history(store, symbol, start, end, window):
    if not store:
        return {}
    bars = store.daily_history_as_of(symbol, str(end), window + 3)
    bars = [bar for bar in bars if bar.volume > 0]
    return daily_sample(bars, start, end)[0]


def factor_risk(rows, samples, benchmark):
    common = sorted(set.intersection(*(set(s) for s in samples)))
    result = dict(
        days=len(common),
        first=str(common[0]) if common else None,
        last=str(common[-1]) if common else None,
        marketDays=sum(d in benchmark for d in common),
        annualVol=None,
        beta=None,
        systematicShare=None,
        residualShare=None,
        var95=None,
        es95=None,
        worst=None,
        strongestPair=None,
    )
    for row, series in zip(rows, samples):
        row["days"] = len(series)
    if len(common) < 20:
        return result
    matrix = np.asarray([[s[d][1] for s in samples] for d in common])
    weights = np.array([r["weight"] for r in rows])
    portfolio = matrix @ weights
    covariance = np.atleast_2d(np.cov(matrix, rowvar=False, ddof=1))
    variance = float(np.var(portfolio, ddof=1))
    result["annualVol"] = sqrt(variance * TRADING_YEAR)
    for i, row in enumerate(rows):
        row["annualVol"] = sqrt(max(0.0, covariance[i, i]) * TRADING_YEAR)
        if variance > 1e-18:
            covariance_to_portfolio = float(covariance[i] @ weights)
            row["marginalVol"] = covariance_to_portfolio * sqrt(TRADING_YEAR / variance)
            row["riskShare"] = weights[i] * covariance_to_portfolio / variance
    best = None
    for i, left in enumerate(rows):
        for j in range(i):
            denominator = sqrt(max(0.0, covariance[i, i] * covariance[j, j]))
            if denominator > 1e-16:
                rho = float(np.clip(covariance[i, j] / denominator, -1, 1))
                if best is None or abs(rho) > abs(best["rho"]):
                    best = dict(left=left["code"], right=rows[j]["code"], rho=rho)
    result["strongestPair"] = best
    worst = int(np.argmin(portfolio))
    result["worst"] = dict(day=str(common[worst]), change=float(portfolio[worst]))
    if len(common) >= 60:
        # Empirical inverse CDF and mean of exactly the worst 5% probability mass.
        losses = np.sort(-portfolio)[::-1]
        mass = len(losses) / 20
        full = int(mass)
        result["var95"] = max(0.0, float(losses[len(losses) - ceil(0.95 * len(losses))]))
        result["es95"] = max(0.0, float((sum(losses[:full]) + (mass - full) * losses[full]) / mass))
    ids = [i for i, d in enumerate(common) if d in benchmark]
    result["marketDays"] = len(ids)
    if len(ids) >= 20:
        x = np.array([benchmark[common[i]][1] for i in ids])
        y = portfolio[ids]
        if np.var(x) > 1e-18:
            beta = float(np.cov(x, y)[0, 1] / np.var(x, ddof=1))
            result["beta"] = beta
            if np.var(y) > 1e-18:
                share = float(np.clip(beta**2 * np.var(x) / np.var(y), 0, 1))
                result.update(systematicShare=share, residualShare=1 - share)
    return result


def analyze(lab, version, day, window):
    with lab.db() as db:
        db.execute("BEGIN")
        account = db.execute(
            """SELECT a.book,a.started,json_extract(v.spec,'$.capital') AS capital
            FROM paper_account a JOIN paper_version v ON v.id=a.id WHERE a.id=?""",
            (version,),
        ).fetchone()
        if not account:
            raise HTTPException(404, "模拟账户不存在")
        where = "id=?" + (" AND at>=? AND at<?" if day else "")
        args = (version, str(day), day.isoformat() + "Z") if day else (version,)
        tick = db.execute(
            "SELECT point FROM paper_point WHERE " + where + " ORDER BY at DESC LIMIT 1", args
        ).fetchone()
        if not tick:
            return dict(state="NO_OBSERVATION", version=version, window=window, day=str(day) if day else None)
        point = json.loads(tick[0])
        values, source, rights = valuation(db, point, json.loads(account["book"]))
        curve = db.execute(
            "SELECT at,json_extract(point,'$.equity') FROM paper_point WHERE id=? AND at<=? ORDER BY at",
            (version, point["at"]),
        ).fetchall()
    equity = point["equity"]
    at = datetime.fromisoformat(point["at"]).astimezone(SHANGHAI)
    end = at.date() if at.time() >= time(15) else at.date() - timedelta(days=1)
    start = sample_start(end, window)
    rows = [
        dict(
            code=c,
            quantity=q,
            value=values[c] if values is not None else None,
            weight=values[c] / equity if values is not None and equity > 0 else None,
            stale=c in point.get("stale", []),
            riskShare=None,
            marginalVol=None,
            annualVol=None,
            days=None,
        )
        for c, q in sorted((point.get("holdings") or {}).items())
    ]
    result = dict(
        version=version,
        state="VALUED" if values is not None and equity > 0 else "UNPRICED",
        at=point["at"],
        window=window,
        fromDay=str(start),
        throughDay=str(end),
        source=source,
        equity=equity,
        cash=point["cash"],
        receivable=rights,
        holdings=rows,
        exposure=None,
        risk=None,
        shocks=[],
        drawdown=drawdowns(curve, account["capital"], account["started"]),
    )
    if result["state"] == "UNPRICED":
        return result
    exposure = sum(r["weight"] for r in rows)
    result["exposure"] = exposure
    result["shocks"].append(dict(kind="EQUITY", shock=-0.1, change=-0.1 * exposure))
    if not rows:
        result["state"] = "CASH"
        return result
    samples = [
        history(
            lab.index_store, ("SH:" if r["code"].startswith("6") else "SZ:") + r["code"], start, end, window
        )
        for r in rows
    ]
    benchmark = history(lab.index_store, BENCHMARK, start, end, window)
    risk = result["risk"] = factor_risk(rows, samples, benchmark)
    if risk["beta"] is not None:
        result["shocks"].append(dict(kind="MARKET", shock=-0.05, change=-0.05 * risk["beta"]))
    if risk["worst"]:
        result["shocks"].append(
            dict(kind="HISTORY", day=risk["worst"]["day"], change=risk["worst"]["change"])
        )
    return result
