"""Read-only learning evidence and an immutable hypothesis / verdict journal."""
from collections import Counter
from datetime import date, datetime, time, timedelta
import json
from math import sqrt
from statistics import mean, stdev
from typing import Annotated, Literal
from uuid import UUID

from fastapi import HTTPException
from pydantic import BaseModel, Field, StringConstraints

from finscope_market_data.forecast.trading_calendar import consecutive_observed_sessions
from finscope_market_data.models import DataCapability, StockSymbol
from .engine import SHANGHAI

Cadence = Literal["daily", "weekly", "monthly", "quarterly"]
Text = Annotated[str, StringConstraints(strip_whitespace=True, min_length=1, max_length=1200)]


class Entry(BaseModel):
    token: UUID
    period: Cadence
    day: date
    hypothesis: Text
    criterion: Text
    source: str = Field(default="", max_length=500)


class Verdict(BaseModel):
    outcome: Literal["支持", "推翻", "证据不足"]
    reason: Text
    experiment: int | None = Field(default=None, gt=0)


def schema(db):
    db.execute("""CREATE TABLE IF NOT EXISTS paper_learning_entry (
        id INTEGER PRIMARY KEY, token TEXT UNIQUE NOT NULL, version INTEGER NOT NULL,
        created TEXT NOT NULL, payload TEXT NOT NULL, evidence TEXT NOT NULL,
        closed TEXT, verdict TEXT, result_evidence TEXT)""")


    db.execute("CREATE INDEX IF NOT EXISTS paper_learning_version ON paper_learning_entry(version,created)")


def closes(points):
    return list({p["at"][:10]: p for p in points}.values())


def daily_returns(points):
    return [b["equity"]/a["equity"]-1 for a, b in zip(points, points[1:])
            if a["equity"] > 0 and consecutive_observed_sessions(
                date.fromisoformat(a["at"][:10]), date.fromisoformat(b["at"][:10]))]


def period_stats(points, start, end, index):
    before = [p for p in points if p["at"][:10] < str(start)]
    sample = [p for p in points if str(start) <= p["at"][:10] <= str(end)]
    result = dict(start=str(start), end=str(end), days=len(sample), net=None, market=None, excess=None)
    if not sample:
        return result
    # Use the prior session only if it exists; a new account starts at its first observed close.
    baseline = sample[0]
    if before and consecutive_observed_sessions(date.fromisoformat(before[-1]["at"][:10]),
                                                date.fromisoformat(sample[0]["at"][:10])):
        baseline = before[-1]
    last = sample[-1]
    first_day, last_day = baseline["at"][:10], last["at"][:10]
    result.update(first=first_day, last=last_day)
    if first_day == last_day or baseline["equity"] <= 0:
        return result
    result["net"] = last["equity"]/baseline["equity"]-1
    if (first_day in index and last_day in index and baseline["at"][11:16] == "15:00"
            and last["at"][11:16] == "15:00"):
        result["market"] = index[last_day]/index[first_day]-1
        result["excess"] = result["net"]-result["market"]
    return result


def risk_at(points, capital):
    returns = daily_returns(points[-21:])
    sharpe = mean(returns)*sqrt(252)/stdev(returns) if len(returns)==20 and stdev(returns)>0 else None
    peak = max([capital] + [p["equity"] for p in points])
    return dict(at=points[-1]["at"] if points else None, sharpe=sharpe, returnDays=len(returns),
                drawdown=1-points[-1]["equity"]/peak if points and peak>0 else None)


def scan_week(db, version, points, start, end, index, capital):
    stats = period_stats(points,start,end,index)
    window_points = [json.loads(r[0]) for r in db.execute(
        "SELECT point FROM paper_point WHERE id=? AND at>=? AND at<? ORDER BY at",
        (version,str(start),str(end+timedelta(days=1))))]
    closes_to_end = [p for p in points if p["at"][:10]<=str(end)]
    risk = risk_at(closes_to_end if stats["days"] else [],capital)
    return {**stats, "risk":risk, "observations":len(window_points), "execution":reconciliation(window_points)}


def parameter_folds(db, version, day):
    """Expanding-window model selection over existing, matched, frozen backtest ledgers."""
    rows=db.execute("""SELECT id,json_extract(history,'$.curve') AS curve FROM paper_version v
        WHERE substr(v.created,1,10)<=? AND EXISTS (
            SELECT 1 FROM paper_version base WHERE base.id=? AND
            json_extract(base.history,'$.classicSnapshots')=json_extract(v.history,'$.classicSnapshots') AND
            json_array_length(base.history,'$.classicSnapshots')>0 AND
            json_extract(base.history,'$.universeTimeline')=json_extract(v.history,'$.universeTimeline') AND
            json_extract(base.history,'$.engine')=json_extract(v.history,'$.engine') AND
            json_extract(base.spec,'$.capital')=json_extract(v.spec,'$.capital') AND
            json_extract(base.history,'$.requestedStart')=json_extract(v.history,'$.requestedStart') AND
            json_extract(base.history,'$.requestedEnd')=json_extract(v.history,'$.requestedEnd'))
        ORDER BY id""",(str(day),version)).fetchall()
    curves={r["id"]:{p["at"][:10]:p["equity"] for p in json.loads(r["curve"])
                     if p["at"][11:16]=="15:00" and p["at"][:10]<=str(day) and p["equity"]>0} for r in rows}
    return evaluate_folds(curves,version)


def evaluate_folds(curves, version):
    result=dict(versions=list(curves), folds=[], commonDays=0)
    if version not in curves or len(curves)<2:
        return result
    dates=sorted(set.intersection(*(set(c) for c in curves.values())))
    result["commonDays"]=len(dates)
    # At least ten close-to-close training observations and five subsequent observations per fold.
    for end in range(10,len(dates)-5,5):
        first, split, last=dates[0],dates[end],dates[end+5]
        train={v:c[split]/c[first]-1 for v,c in curves.items()}
        winner=max(train,key=lambda v:(train[v],-v))
        result["folds"].append(dict(trainFrom=first, trainTo=split, testFrom=dates[end+1], testTo=last,
            selected=winner, trainReturn=train[winner], testReturn=curves[winner][last]/curves[winner][split]-1,
            referenceReturn=curves[version][last]/curves[version][split]-1))
    return result


def reconciliation(points):
    outcomes = Counter()
    reasons = Counter()
    fees = notional = slippage = 0.
    priced = 0
    signals, resolved = set(), set()
    for point in points:
        for e in point.get("events", []):
            key = (e.get("available"), e["code"], e["side"])
            outcomes[e["action"]] += 1
            if e["action"] == "信号":
                signals.add(key)
            elif e["action"] in ("未成交", "成交"):
                resolved.add(key)
                if e["action"] == "未成交":
                    reasons[e["reason"]] += 1
                else:
                    if e.get("executionOpen") is not None:
                        side = 1 if e["side"]=="BUY" else -1
                        slippage += side*(e["price"]-e["executionOpen"])*e["quantity"]
                        priced += 1
                    fees += e["fee"]
                    notional += e["quantity"]*e["price"]
    return dict(signals=outcomes["信号"], fills=outcomes["成交"], rejected=outcomes["未成交"],
                unresolved=len(signals-resolved), carried=len(resolved-signals),
                slippage=round(slippage,2) if priced and priced==outcomes["成交"] else None, priced=priced,
                fees=round(fees,2), feeBps=fees/notional*10000 if notional else None,
                reasons=[dict(reason=k,count=v) for k,v in reasons.most_common(5)])


def rows_for_day(db, version, day):
    return [json.loads(r[0]) for r in db.execute(
        "SELECT point FROM paper_point WHERE id=? AND at>=? AND at<? ORDER BY at",
        (version,str(day),str(day+timedelta(days=1))))]


def account_check(lab, db, account, day, cutoff):
    health = lab.execution_health(db, account["id"], account, cutoff)
    health = next((r for r in (health or {}).get("days",[]) if r["date"]==str(day)),None)
    points = rows_for_day(db, account["id"], day)
    plan = next((p["rebalance"] for p in reversed(points) if p.get("rebalance")), {})
    blocked = sum(1 for leg in plan.get("legs",{}).values() if leg.get("remaining"))
    return dict(version=account["id"], name=account["name"], health=health, points=len(points),
                remaining=blocked, **reconciliation(points))


def read_entries(db, version, day):
    rows = db.execute("SELECT * FROM paper_learning_entry WHERE version=? AND substr(created,1,10)<=? ORDER BY id DESC",
                      (version,str(day)))
    entries = []
    for r in rows:
        payload = json.loads(r["payload"])
        closed = r["closed"] if r["closed"] and r["closed"][:10]<=str(day) else None
        entries.append(dict(id=r["id"],created=r["created"],closed=closed,**payload,
                            verdict=json.loads(r["verdict"]) if closed else None))
    return entries


def snapshot(lab, db, version, day, now):
    if day > now.date():
        raise HTTPException(422, "观察日期不能在未来")
    cutoff=min(now,datetime.combine(day,time.max,SHANGHAI))
    row=db.execute("""SELECT created,json_extract(spec,'$.rule.name') AS name,
            json_extract(spec,'$.capital') AS capital,json_extract(spec,'$.rule.recipe') AS recipe,
            json_extract(history,'$.curve') AS curve,json_extract(history,'$.comparison.version') AS parent,
            json_extract(history,'$.validationStart') AS holdout, json_extract(history,'$.fees') AS fees,
            json_extract(history,'$.validationReturn') AS net, json_extract(history,'$.validationBenchmark') AS benchmark,
            json_extract(history,'$.comparison') AS pairing FROM paper_version WHERE id=?""",(version,)).fetchone()
    if not row:
        raise HTTPException(404,"实验不存在")
    if day.isoformat()<row["created"][:10]:
        raise HTTPException(422,"观察日期早于策略版本创建日")
    accounts=db.execute("""SELECT a.*,json_extract(v.spec,'$.rule.name') AS name FROM paper_account a
                           JOIN paper_version v ON v.id=a.id WHERE substr(a.started,1,10)<=?
                           AND (a.lifecycle='RUNNING' OR a.id=? OR EXISTS (
                               SELECT 1 FROM paper_point p WHERE p.id=a.id AND p.at>=? AND p.at<?)
                               OR EXISTS (SELECT 1 FROM paper_run_interval i WHERE i.id=a.id AND
                               substr(i.started,1,10)<=? AND substr(i.ended,1,10)>=?)) ORDER BY a.id""",(str(day),version,str(day),str(day+timedelta(days=1)),str(day),str(day))).fetchall()
    checks=[account_check(lab,db,a,day,cutoff) for a in accounts]
    points=[dict(r) for r in db.execute("""SELECT max(at) AS at,json_extract(point,'$.equity') AS equity
        FROM paper_point WHERE id=? AND at<? AND substr(at,12,5)='15:00' GROUP BY substr(at,1,10) ORDER BY at""",
        (version,str(day+timedelta(days=1))))]
    risk=risk_at(points,row["capital"])
    prior=risk_at([p for p in points if p["at"][:10]<str(day)],row["capital"])
    current=rows_for_day(db,version,day)
    latest=current[-1] if current else None
    peak=max([row["capital"]]+[p["equity"] for p in points]+([latest["equity"]] if latest else []))
    drawdown=1-latest["equity"]/peak if latest else None
    index={}
    if lab.index_store:
        cached=lab.index_store.load(DataCapability.DAILY_BARS,StockSymbol(market="SH",code="000300"))
        index={str(b.trade_date):b.close for b in (cached.data or [] if cached else [])
               if b.is_complete and b.close and b.close>0}
    if prior["at"]:
        prior_day=date.fromisoformat(prior["at"][:10])
        prior["comparison"]=period_stats(points,prior_day,prior_day,index)
    monday=day-timedelta(days=day.weekday())
    qstart=day.replace(month=1+3*((day.month-1)//3),day=1)
    history=closes(json.loads(row["curve"] or "[]"))
    historical=[p for p in history if p["at"][:10]<=str(day)] if row["created"][:10]<=str(day) else []
    hist_returns=daily_returns([p for p in historical if p["at"][11:16]=="15:00"])
    distribution=[dict(label=label,days=sum(lower<=v<upper for v in hist_returns))
                  for label,lower,upper in (("低于 −2%",float("-inf"),-.02),("−2% 至 −1%",-.02,-.01),
                      ("−1% 至 0%",-.01,0),("0% 至 1%",0,.01),("1% 至 2%",.01,.02),("至少 2%",.02,float("inf")))]
    recipe=json.loads(row["recipe"] or "{}")
    entries=read_entries(db,version,day)
    month=str(day)[:7]
    resolved=[e for e in entries if (e["closed"] or "").startswith(month)]
    status=Counter(e["verdict"]["outcome"] for e in resolved)
    return dict(version=version,name=row["name"],day=str(day),accounts=checks,
        daily=dict(at=latest["at"] if latest else None,sharpe=risk["sharpe"],returnDays=risk["returnDays"],
                   preopen=prior,feeBudget=recipe.get("maxFeeBps") or None,
                   drawdown=drawdown,limit=recipe.get("drawdownStop") or None,
                   comparison=period_stats(points,day,day,index)),
        weekly=dict(current=scan_week(db,version,points,monday,day,index,row["capital"]),
                    previous=scan_week(db,version,points,monday-timedelta(days=7),monday-timedelta(days=1),index,row["capital"])),
        monthly=dict(month=month,opened=sum(e["created"].startswith(month) for e in entries),
                     supported=status["支持"],refuted=status["推翻"],insufficient=status["证据不足"],
                     pending=sum(not e["closed"] for e in entries),
                     refutations=[dict(hypothesis=e["hypothesis"],reason=e["verdict"]["reason"]) for e in resolved
                                  if e["verdict"]["outcome"]=="推翻"],
                     closes=sum(p["at"].startswith(month) for p in points),
                     marketCloses=sum(d.startswith(month) and d<=str(day) for d in index)),
        quarterly=dict(forward=period_stats(points,qstart,day,index),historyDays=len(historical),
                       years=[period_stats(historical,date(y,1,1),date(y,12,31),index)
                              for y in sorted({int(p["at"][:4]) for p in historical})],
                       positive=sum(v>0 for v in hist_returns),negative=sum(v<0 for v in hist_returns),
                       flat=sum(v==0 for v in hist_returns),holdout=row["holdout"],comparison=row["parent"],
                       settings=recipe, validation=parameter_folds(db,version,day), distribution=distribution, backtest=dict(net=row["net"],benchmark=row["benchmark"],fees=row["fees"],
                       first=historical[0]["at"][:10] if historical else None,last=historical[-1]["at"][:10] if historical else None),
                       pairing=json.loads(row["pairing"] or "null")),entries=entries)


def evidence(report):
    return {k:v for k,v in report.items() if k!="entries"}


def register(lab, version, value, now):
    payload=value.model_dump_json(exclude={"token"})
    with lab.db() as db:
        schema(db)
        db.execute("BEGIN IMMEDIATE")
        existing=db.execute("SELECT id,version,payload FROM paper_learning_entry WHERE token=?",(str(value.token),)).fetchone()
        if existing:
            if (existing["version"],existing["payload"])!=(version,payload):
                raise HTTPException(422,"记录编号已被其他请求使用")
            return dict(id=existing["id"])
        report=snapshot(lab,db,version,value.day,now)
        cursor=db.execute("INSERT INTO paper_learning_entry(token,version,created,payload,evidence) VALUES(?,?,?,?,?)",
                          (str(value.token),version,now.isoformat(),payload,json.dumps(evidence(report),ensure_ascii=False)))
        return dict(id=cursor.lastrowid)


def conclude(lab, version, entry, value, now):
    with lab.db() as db:
        schema(db)
        db.execute("BEGIN IMMEDIATE")
        row=db.execute("SELECT * FROM paper_learning_entry WHERE id=? AND version=?",(entry,version)).fetchone()
        if not row:
            raise HTTPException(404,"学习记录不存在")
        verdict=value.model_dump_json()
        if row["closed"]:
            if row["verdict"]!=verdict:
                raise HTTPException(422,"结论已保存")
            return dict(id=entry)
        target=value.experiment or version
        v=db.execute("SELECT created, json_extract(history,'$.comparison.version') AS base, json_extract(spec,'$.parent') AS parent FROM paper_version WHERE id=?",(target,)).fetchone()
        if not v or target!=version and (version not in (v["base"],v["parent"]) or v["created"]<=row["created"]):
            raise HTTPException(422,"请选择登记之后从该策略衍生的对照实验")
        if target==version and value.outcome!="证据不足":
            fresh=db.execute("SELECT 1 FROM paper_point WHERE id=? AND at>? LIMIT 1",(version,row["created"])).fetchone()
            if not fresh:
                raise HTTPException(422,"登记之后还没有新增模拟观测")
        result=evidence(snapshot(lab,db,target,now.date(),now))
        db.execute("UPDATE paper_learning_entry SET closed=?,verdict=?,result_evidence=? WHERE id=?",
                   (now.isoformat(),verdict,json.dumps(result,ensure_ascii=False),entry))
        return dict(id=entry)
