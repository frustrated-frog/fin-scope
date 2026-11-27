"""Rolling selection and paired out-of-sample replay on immutable classic snapshots."""
from datetime import date, datetime
import json
from typing import Literal
from uuid import UUID

from pydantic import BaseModel, ConfigDict, Field

from finscope_market_data.forecast.trading_calendar import consecutive_observed_sessions
from . import classic_study, performance
from .classic_recipe import Recipe, archived_body, variant_code
from .engine import SHANGHAI


class WalkRequest(BaseModel):
    model_config = ConfigDict(extra="forbid", allow_inf_nan=False)
    requestId: UUID
    parameter: Literal["window", "holdings", "exposure", "maxWeight", "stopLoss"]
    values: list[float] = Field(min_length=2, max_length=8)
    trainDays: int = Field(ge=5, le=120)
    testDays: int = Field(ge=3, le=40)


def windows(curve, train, test):
    days = sorted({p["at"][:10] for p in curve if p["at"][11:16] == "15:00"})
    spans = []
    for day in days:
        if not spans or not consecutive_observed_sessions(date.fromisoformat(spans[-1][-1]), date.fromisoformat(day)):
            spans.append([])
        spans[-1].append(day)
    folds = []
    tail = 0
    for span in spans:
        start = train
        while start + test <= len(span):
            folds.append((span[start-train:start], span[start:start+test]))
            start += test
        tail += max(0, len(span) - start)
    return folds, dict(closeDays=len(days), spans=len(spans), tailDays=tail)


def source(db, version):
    row = db.execute("SELECT spec,history FROM paper_version WHERE id=?", (version,)).fetchone()
    if not row:
        raise ValueError("找不到该历史回测")
    spec, history = map(json.loads, row)
    rule = spec["rule"]
    if (rule.get("executionVersion") != 2 or not rule.get("image") or not history.get("classicSnapshots")
            or not history.get("universeTimeline") or not rule.get("code", "").startswith("RECIPE = ")):
        raise ValueError("请选择保存了完整行情与执行版本的经典回测")
    return spec, history, archived_body(rule)


def enqueue(lab, version, value):
    plan = value.model_dump(exclude={"requestId"})
    plan["values"].sort()
    if len(set(plan["values"])) != len(plan["values"]):
        raise ValueError("候选参数不能重复")
    key = "walk:" + str(value.requestId)
    with lab.db() as db:
        lab.jobs.schema(db)
        db.execute("BEGIN IMMEDIATE")
        saved = db.execute("SELECT id,request FROM paper_job WHERE auto_key=?", (key,)).fetchone()
        if saved:
            request = json.loads(saved[1])
            if request["reference"] != version or request["plan"] != plan:
                raise ValueError("该提交编号已用于另一项实验")
            return {"jobId": saved[0]}
        spec, history, _ = source(db, version)
        for candidate in value.values:
            Recipe.model_validate({**spec["rule"]["recipe"], value.parameter: candidate})
        folds, _ = windows(history["curve"], value.trainDays, value.testDays)
        if not folds:
            raise ValueError("连续收盘样本不足以组成一轮完整训练和验证，请缩短窗口")
        request = dict(researchMode="WALK_FORWARD", reference=version, plan=plan, autoStart=False,
                       origin=f"滚动验证 · 基线 #{version}")
        cursor = db.execute("INSERT INTO paper_job(state,request,created,auto_key) VALUES('QUEUED',?,?,?)",
                            (json.dumps(request, ensure_ascii=False), datetime.now(SHANGHAI).isoformat(), key))
        return {"jobId": cursor.lastrowid}


def compare(folds, plan, baseline, replay, progress):
    rows = []
    for i, (train, test) in enumerate(folds, 1):
        progress(f"第 {i}/{len(folds)} 轮 · 训练 {train[0]} 至 {train[-1]}")
        candidates = [dict(value=value, **replay(value, train)) for value in plan["values"]]
        winner = min(candidates, key=lambda r: (-r["net"], abs(r["value"]-baseline), r["value"]))
        selected = winner["value"]
        progress(f"第 {i}/{len(folds)} 轮 · 固定所选参数 {selected:g}，验证 {test[0]} 至 {test[-1]}")
        validation = replay(selected, test)
        original = validation if selected == baseline else replay(baseline, test)
        rows.append(dict(trainStart=train[0], trainEnd=train[-1], testStart=test[0], testEnd=test[-1],
                         candidates=candidates, selected=selected, validation=validation, baseline=original,
                         spread=validation["net"] - original["net"]))
    return rows


def run(jobs, job, request):
    with jobs.lab.db() as db:
        spec, history, body = source(db, request["reference"])
    plan = request["plan"]
    rule = spec["rule"]
    capital = spec["capital"]
    baseline = rule["recipe"][plan["parameter"]]
    folds, coverage = windows(history["curve"], plan["trainDays"], plan["testDays"])
    panel = {}
    for digest in history["classicSnapshots"]:
        panel.update(jobs.read_panel(digest, daily_window=True))

    def replay(value, days):
        recipe = Recipe.model_validate({**rule["recipe"], plan["parameter"]: value})
        code = variant_code(recipe, body)
        raw = classic_study.simulate_panel(panel, history["universeTimeline"], code, rule["image"], capital,
                                           recipe, first=days[0], last=days[-1])
        observed = sorted({p["at"][:10] for p in raw["curve"] if p["at"][11:16] == "15:00"})
        if observed != days:
            raise ValueError("片段的收盘行情与冻结基线不一致")
        metrics = performance.report(raw["curve"], capital, raw["fees"], raw["costs"])
        return dict(net=raw["validationReturn"], drawdown=metrics["maxDrawdown"], fees=raw["fees"], fills=raw["fills"])

    rows = compare(folds, plan, baseline, replay, lambda log: jobs.update(job, "BACKTEST", log))
    result = dict(folds=rows, baseline=baseline, capital=capital, coverage=coverage,
                  wins=sum(r["spread"] > 0 for r in rows), meanSpread=sum(r["spread"] for r in rows)/len(rows))
    with jobs.lab.db() as db:
        db.execute("INSERT OR REPLACE INTO paper_walk_result(job,result) VALUES(?,?)", (job, json.dumps(result)))
        db.execute("UPDATE paper_job SET state='DONE',log=json_insert(log,'$[#]',?) WHERE id=?", ("样本外逐窗验证完成", job))


def results(lab, version):
    with lab.db() as db:
        lab.jobs.schema(db)
        rows = db.execute("""SELECT j.id,j.state,j.created,json_extract(j.log,'$[#-1]') AS latest,
            json_extract(j.request,'$.plan') AS plan,r.result FROM paper_job j
            LEFT JOIN paper_walk_result r ON r.job=j.id WHERE j.auto_key LIKE 'walk:%'
            AND json_extract(j.request,'$.reference')=? ORDER BY j.id DESC""", (version,))
        return [dict(id=row["id"], state=row["state"], created=row["created"], latest=row["latest"],
                     plan=json.loads(row["plan"]), result=json.loads(row["result"]) if row["result"] else None)
                for row in rows]
