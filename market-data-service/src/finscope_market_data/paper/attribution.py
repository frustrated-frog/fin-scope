"""Explain frozen backtest P&L. No live quotes or new strategy decisions enter this calculation."""
import json
from collections import defaultdict

from fastapi import HTTPException

from .actions import apply_entitlements
from .engine import money, new_book, settle_fill


def leg(version, spec, history):
    capital = spec["capital"]
    curve = history.get("curve", [])
    if capital <= 0 or not curve:
        raise HTTPException(422, "没有可归因的初始本金或历史净值")
    last = curve[-1]
    costs = history.get("costs") or {}
    pnl = money(last["equity"] - capital)
    fees = history.get("fees")
    slip = costs.get("slippage")
    before_fees = money(pnl + fees) if fees is not None else None
    return dict(id=version, name=spec["rule"]["name"], capital=capital, net=pnl,
                beforeFees=before_fees, beforeCosts=money(before_fees + slip) if before_fees is not None and slip is not None else None,
                benchmark=money(last["benchmark"] - capital), fees=fees, slippage=slip,
                commission=costs.get("commission"), tax=costs.get("stampTax"), fills=history.get("fills"),
                frequency=(spec["rule"].get("recipe") or {}).get("rebalance"),
                first=curve[0]["at"], last=last["at"])


def compatible(current, base, facts, reference):
    required = ("classicSnapshots", "universeTimeline", "engine")
    return (all(current.get(k) and current[k] == base.get(k) for k in required)
            and all(facts[k] == reference[k] for k in ("capital", "first", "last", "benchmark"))
            and [p["at"] for p in current["curve"]] == [p["at"] for p in base["curve"]])


def bridge(current, base):
    net = money(current["net"] - base["net"])
    if current["fees"] is None or base["fees"] is None:
        return None
    fees = money(base["fees"] - current["fees"])
    slip = None
    if base["slippage"] is not None and current["slippage"] is not None:
        slip = money(base["slippage"] - current["slippage"])
    return dict(net=net, fees=fees, slippage=slip, holdings=money(net-fees-(slip or 0)),
                points=100*net/current["capital"])


def day_changes(history, capital):
    ends = {point["at"][:10]: point for point in history["curve"]}
    previous = capital
    result = {}
    for day, point in sorted(ends.items()):
        result[day] = dict(at=point["at"], net=money(point["equity"]-previous))
        previous = point["equity"]
    return result


def stock_ledger(history, prices, start):
    """Revalue only executed trades, applying dated entitlements before the fills on the same bar."""
    trades = [t for t in history["trades"] if t["action"] == "成交"]
    if len(trades) != history.get("fills"):
        raise ValueError("逐笔成交数量与回测汇总不符")
    by_code = defaultdict(lambda: defaultdict(list))
    for trade in trades:
        by_code[trade["code"]][trade["at"]].append(trade)
    times = {p["at"] for p in history["curve"]}
    result = {}
    for code, orders in by_code.items():
        book = new_book(0.)
        mark = None
        for bar in prices.get(code, []):
            at = bar[0]
            if at not in times:
                continue
            apply_entitlements(book, code, bar[9] if len(bar)>9 else [])
            book["anchors"].setdefault(code, bar[4])
            for trade in orders.pop(at, []):
                gross = money(trade["quantity"] * trade["price"])
                settle_fill(book, code, trade["side"], at[:10], trade["quantity"], gross, trade["fee"])
            mark = bar[4]
        if orders or mark is None:
            raise ValueError("快照缺少成交时点")
        shares = sum(q for _,q in book["positions"].get(code, {}).get("lots", []))
        value = money(shares*mark)
        income = book.get("dividendReceivable", 0.)
        result[code] = dict(net=money(book["cash"]+value+income), fees=book["fees"], dividends=income,
                            fills=book["fills"], shares=shares, value=value)
    residual = money(history["curve"][-1]["equity"] - start - sum(v["net"] for v in result.values()))
    if abs(residual) > .02:
        raise ValueError("逐股账本与冻结净值无法核平")
    return result, residual


def read_version(db, version):
    row = db.execute("SELECT spec,history FROM paper_version WHERE id=?", (version,)).fetchone()
    if not row:
        raise HTTPException(404, "回测版本不存在")
    return json.loads(row[0]), json.loads(row[1])


def report(lab, version):
    with lab.db() as db:
        spec, history = read_version(db, version)
        parent = (history.get("comparison") or {}).get("version")
        original = read_version(db, parent) if parent else None
    current = leg(version, spec, history)
    base = leg(parent, *original) if original else None
    paired = bool(base and compatible(history, original[1], current, base))
    summary = dict(current=current, base=base, paired=paired, bridge=bridge(current, base) if paired else None)
    dates = day_changes(history, current["capital"])
    reference = day_changes(original[1], base["capital"]) if paired else {}
    summary["days"] = [dict(day=d, at=p["at"], net=p["net"], base=reference[d]["net"] if paired else None,
                             gap=money(p["net"]-reference[d]["net"]) if paired else None) for d,p in dates.items()]
    summary.update(stocks=[], residual=None, stockStatus="")
    ledgers = [history, original[1]] if paired else [history]
    if any("trades" not in h or not h.get("classicSnapshots") for h in ledgers):
        summary["stockStatus"] = "该版本没有归档逐笔成交和可核对的行情快照"
        return summary
    try:
        codes = {e["code"] for h in ledgers for e in h["trades"] if e["action"] == "成交"}
        prices = {}
        for digest in history["classicSnapshots"]:
            payload = lab.jobs.snapshot_payload(digest)
            prices.update({c:bars for c,bars in payload["bars"].items() if c in codes})
        holdings, residual = stock_ledger(history, prices, current["capital"])
        baseline, base_residual = stock_ledger(original[1], prices, base["capital"]) if paired else ({}, 0.)
    except (ValueError, KeyError, OSError, IndexError):
        summary["stockStatus"] = "逐笔记录或冻结行情不完整，无法核对逐股贡献"
        return summary
    names = lab.name_lookup(list(codes)) if lab.name_lookup else {}
    rows = []
    for code in sorted(holdings.keys() | baseline.keys()):
        a, b = holdings.get(code), baseline.get(code)
        difference = money((a or {}).get("net", 0.) - (b or {}).get("net", 0.))
        rows.append(dict(code=code, name=names.get(code), current=a, base=b, gap=difference if paired else None))
    summary.update(stocks=sorted(rows, key=lambda r: -abs(r["gap"] if paired else r["current"]["net"])),
                   residual=money(residual-base_residual))
    return summary
