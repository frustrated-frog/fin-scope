"""Run a frozen classic strategy against dated universes using the shared paper ledger."""
import asyncio
from datetime import datetime, time, timedelta
import json

from finscope_market_data.forecast.trading_calendar import previous_session
from .analysis import frames
from . import performance, rebalance
from .classic_recipe import FAMILIES, Recipe, program
from .classic_universe import active_codes, as_of, schedule
from .engine import ENGINE, SHANGHAI, advance, new_book
from .python_runtime import PythonStrategy, image_id


def freeze(lab, value, now):
    if value.reference:
        with lab.db() as db:
            row = db.execute("SELECT request FROM paper_job WHERE version=? AND json_extract(request,'$.researchMode')='CLASSIC' ORDER BY id LIMIT 1",
                             (value.reference,)).fetchone()
        if not row:
            raise ValueError("原经典实验不存在")
        original = json.loads(row[0])
        if original["recipe"]["base"] != value.recipe.base or original["capital"] != value.capital:
            raise ValueError("同数据对照须使用相同的股票池来源与初始本金")
        if value.startDate and (str(value.startDate) != original["start"][:10] or str(value.endDate) != original["cutoff"][:10]):
            raise ValueError("同数据对照须沿用原实验区间")
        return {**original, **value.model_dump(mode="json"), "executionVersion": 2, "origin": FAMILIES[value.recipe.family][0]}
    date = previous_session(now.date() + timedelta(days=int(now.time() >= time(15, 10))))
    if not date:
        raise ValueError("交易日历缺少最近收盘日")
    if value.endDate and value.endDate > date:
        raise ValueError(f"回测结束日不能超过最近已完成的交易日 {date}")
    cutoff = datetime.combine(value.endDate or date, time(15), SHANGHAI)
    start = datetime.combine(value.startDate or date-timedelta(days=30), time(), SHANGHAI)
    timeline = schedule(lab, value.recipe.base, cutoff, start)
    if not timeline or not any(entry["codes"] for entry in timeline):
        raise ValueError("所选股票池缺少带首次可用时间的历史名单")
    sources = {"COHORT": "登记研究池", "DISCOVERY": "股票发现历史候选池", "GEM": "登记研究池 · 创业板",
               "SH_MAIN": "登记研究池 · 沪市主板"}
    return dict(**value.model_dump(mode="json"), origin=FAMILIES[value.recipe.family][0],
                pool="COHORT", source=sources[value.recipe.base], codes=next(r["codes"] for r in reversed(timeline) if r["codes"]),
                timeline=timeline, start=start.isoformat(), cutoff=cutoff.isoformat(), executionVersion=2)


def noop(histories, book, end, equity):
    return {}, {}


def step(book, history, entry, at, available, capital, runtime=None, decision_clock=None, *, execution=None):
    codes = active_codes(entry, book)
    fresh = {c: history[c] for c in codes if c in history}
    if entry and entry["codes"] and not book.get("classicBenchmark"):
        book["classicBenchmark"] = list(entry["codes"])
    if execution is not None:
        rebalance.expire(book, at, fresh, before_fill=True)
        point = advance(book, None, fresh, at, available, capital, universe=codes, decide=noop)
        return rebalance.advance_plan(book, point, fresh, entry, at, available, runtime, execution, capital, decision_clock)
    decide = noop
    if runtime and entry and at.strftime("%H:%M") == "14:45":
        book["selectionBase"] = entry["codes"]
        decide = runtime.decide
    point = advance(book, None, fresh, at, available, capital, universe=codes or list(history),
                    decide=decide, decision_clock=decision_clock)
    selection = book.get("strategyState", {}).get("selection", {})
    if selection.get("at") == at.isoformat():
        point["selection"] = dict(**selection, base=entry["codes"], knownAt=entry["at"])
        point["reason"] = "按本期候选排名与仓位规则调仓"
    elif not point["events"]:
        point["reason"] = "等待下一调仓时点"
    return point


def simulate(jobs, snapshots, timeline, frozen, image, capital, execution=None):
    panel = {}
    for digest in snapshots:
        panel.update(jobs.read_panel(digest, daily_window=True))
    curve, decisions, trades = [], [], []
    book = new_book(capital)
    plans = {}
    with PythonStrategy(frozen.code, image, sdk_version=4) as runtime:
        for at, history in frames(panel):
            entry = as_of(timeline, at)
            if not entry or not active_codes(entry, book):
                continue
            relevant = {c: history[c] for c in active_codes(entry, book) if c in history}
            if not relevant:
                continue
            point = step(book, relevant, entry, at, at + timedelta(seconds=3), capital, runtime, execution=execution)
            for key in ("previousPlan", "rebalance"):
                if key in point:
                    plan = point[key]
                    plans[plan["at"]] = plan
            if "selection" in point:
                decisions.append(point["selection"])
            trades.extend(dict(at=point["at"], **e) for e in point["events"])
            curve.append({k: point[k] for k in ("at", "equity", "benchmark")})
    if not curve or len({p["at"][:10] for p in curve}) < 3:
        raise ValueError("按实际入池时间过滤后，至少需要三个有行情的交易日")
    if not any(d["candidates"] for d in decisions):
        raise ValueError("所选周期和流动性条件没有合格样本，请调整窗口或股票池")
    if execution and all(d.get("skipped") for d in decisions):
        raise ValueError("整个区间内均缺少足够的有效样本生成目标组合，请检查行情周期与风险窗口")
    extra = dict(plans=list(plans.values()), costs=book["executionCosts"]) if execution else {}
    return dict(curve=curve, decisions=decisions, trades=trades, fills=book["fills"], fees=book["fees"], **extra,
                firstDay=curve[0]["at"][:10], lastDay=curve[-1]["at"][:10],
                days=len({p["at"][:10] for p in curve}), validationReturn=book["equity"] / capital - 1,
                validationBenchmark=curve[-1]["benchmark"] / capital - 1, engine=ENGINE+("-classic-v2" if execution else "-classic-v1"))


async def run(jobs, job, request):
    recipe = Recipe.model_validate(request["recipe"])
    frozen = program(recipe)
    image = await asyncio.to_thread(image_id)
    codes = list(dict.fromkeys(c for entry in request["timeline"] for c in entry["codes"]))
    snapshots = request.get("classicSnapshots", [])
    for index in range(0, len(codes), 16):
        if index // 16 < len(snapshots):
            continue
        jobs.update(job, "DATA", f"冻结历史名单行情 · {index + 1}–{min(index+16,len(codes))}/{len(codes)}")
        digest = await jobs.snapshot(codes[index:index+16], job, eligible_from=datetime.fromisoformat(request["start"]),
                                     cutoff=datetime.fromisoformat(request["cutoff"]), full_interval=bool(request.get("startDate")))
        snapshots.append(digest)
        with jobs.lab.db() as db:
            db.execute("UPDATE paper_job SET request=json_set(request,'$.classicSnapshots',json(?)) WHERE id=?",
                       (json.dumps(snapshots), job))
    jobs.update(job, "BACKTEST", "按历史名单逐次选股、计算目标仓位并撮合")
    execution = recipe if request.get("executionVersion", 1) == 2 else None
    result = await asyncio.to_thread(simulate, jobs, snapshots, request["timeline"], frozen, image, request["capital"], execution)
    if request.get("reference"):
        with jobs.lab.db() as db:
            original_row = db.execute("SELECT history,spec FROM paper_version WHERE id=?", (request["reference"],)).fetchone()
            original = json.loads(original_row[0])
            rule = json.loads(original_row[1])["rule"]
        result["comparison"] = dict(version=request["reference"], netReturn=original["validationReturn"],
                                    fills=original["fills"], fees=original["fees"], executionVersion=rule.get("executionVersion",1),
                                    frequency=rule["recipe"]["rebalance"],
                                    performance=performance.report(original["curve"],request["capital"],original["fees"],original.get("costs")))
    result.update(classicSnapshots=snapshots, universeTimeline=request["timeline"], validationMode="CLASSIC",
                  requestedStart=request["start"][:10], requestedEnd=request["cutoff"][:10])
    jobs.save_version(job, request, frozen, image, snapshots[0], result)
