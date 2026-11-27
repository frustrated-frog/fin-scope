"""Run a dated research cohort against a fixed tradable subset with two independent ledgers."""

import asyncio
from datetime import datetime, time, timedelta
import json
import uuid
from typing import Literal

from pydantic import BaseModel, ConfigDict, Field
from finscope_market_data.forecast.trading_calendar import previous_session
from finscope_market_data.overnight.automation_store import AutomationStore
from finscope_market_data.overnight.universe import POOL_KEY
from .analysis import coverage
from .engine import SHANGHAI
from .python_runtime import image_id
from . import tail_model


class RidgeRequest(BaseModel):
    model_config = ConfigDict(extra="forbid")
    researchMode: Literal["RIDGE"]
    capital: float = Field(ge=.01, le=1e10, allow_inf_nan=False)
    requestId: uuid.UUID | None = None
    autoStart: bool = False


def freeze_request(archive, value, now):
    universe = AutomationStore(archive).get(POOL_KEY) if archive else None
    members = []
    for m in (universe or {}).get("members", []):
        joined = datetime.fromisoformat(m["joinedAt"])
        joined = joined.replace(tzinfo=SHANGHAI) if joined.tzinfo is None else joined.astimezone(SHANGHAI)
        if joined <= now:
            members.append(dict(code=m["instrumentCode"].split(".")[0], joined=joined.isoformat()))
    members.sort(key=lambda m: m["joined"])
    if len(members) < 3:
        raise ValueError("已登记首次观察时间的研究成员不足三只")
    completed = previous_session(now.date() + timedelta(days=int(now.time() >= time(15, 10))))
    if not completed:
        raise ValueError("交易日历缺少最近收盘日")
    return dict(**value.model_dump(mode="json"), origin=tail_model.HYPOTHESIS, pool="COHORT",
                source="首次登记的研究股票池 · 按入池顺序冻结交易名单", members=members,
                codes=[m["code"] for m in members[:24]], dataThrough=str(completed),
                universeHash=universe["fingerprint"])


async def run(jobs, job, request):
    from .research import Program, simulate

    image = await asyncio.to_thread(image_id)
    codes = request["codes"]
    members = {m["code"]: m["joined"] for m in request["members"]}
    # All traded names must already be known at the first historical decision.
    first = max(members[c][:10] for c in codes)
    start = datetime.fromisoformat(first).date()
    cutoff = datetime.fromisoformat(request["dataThrough"] + "T15:00").replace(tzinfo=SHANGHAI)
    digests = request.get("cohortSnapshots", [])
    other = [c for c in members if c not in codes]
    batches = [codes] + [other[i:i+12] for i in range(0, len(other), 12)]
    training = []
    for i, batch in enumerate(batches):
        jobs.update(job, "DATA", f"读取冻结研究名单第 {i+1}/{len(batches)} 批")
        if i == len(digests):
            digest = await jobs.snapshot(batch, job, first_day=start, cutoff=cutoff)
            digests.append(digest)
            with jobs.lab.db() as db:
                db.execute("UPDATE paper_job SET request=json_set(request,'$.cohortSnapshots',json(?)) WHERE id=?",
                           (json.dumps(digests), job))
        panel = await asyncio.to_thread(jobs.read_panel, digests[i])
        if i == 0:
            trade_panel = panel
            dates = sorted({str(b.observed_at.date()) for bars in panel.values() for b in bars
                            if b.observed_at.strftime("%H:%M") == "15:00"})
            if len(dates) < 15:
                raise ValueError(f"交易池仅有 {len(dates)} 个收盘日，至少需要 15 日")
            split = dates[int(len(dates)*.8)]
        def extract():
            rows = []
            for code, bars in panel.items():
                development = [b for b in bars if str(b.observed_at.date()) < split]
                rows.extend(tail_model.samples(code, development, members[code]))
            return rows
        training.extend(await asyncio.to_thread(extract))
    jobs.update(job, "VALIDATING", "按交易日加权拟合 Ridge，冻结系数，与等权因子同窗回测")
    model = await asyncio.to_thread(tail_model.train, training, split)
    program = Program(name=tail_model.NAME, hypothesis=tail_model.HYPOTHESIS, code=tail_model.source(model),
                      factor="四项标准化收益因子按开发段 Ridge 系数组合；尾盘选前三，每只目标 32%，次日首个可执行窗口退出。")
    baseline = dict(kind="python", name="多周期因子等权对照", code=tail_model.source(model, equal=True),
                    factor="同一批标准化因子采用等权评分。", hypothesis=tail_model.HYPOTHESIS,
                    image=image, snapshot=digests[0], exDateAccounting=True)
    validation = {c: [b for b in bars if str(b.observed_at.date()) >= split] for c, bars in trade_panel.items()}
    valid_coverage = coverage(validation)
    summary = await asyncio.to_thread(simulate, trade_panel, program, image, request["capital"], baseline,
                                      validation_start=split)
    summary.update(model=model, researchPoolSize=len(members), baselineRule=baseline, validationMode="RIDGE",
                   universeHash=request["universeHash"], researchSnapshots=digests, members=request["members"],
                   symbolCoverage=coverage(trade_panel), validationCoverage=valid_coverage,
                   unverifiedSymbols=sum(not r["actionsVerified"] for r in valid_coverage.values()),
                   incompleteActionTerms=sum(event.get("termsKnown") is False for bars in validation.values()
                                             for b in bars for event in b.actions),
                   information=dict(knowledgeThrough=datetime.now(SHANGHAI).isoformat(),
                   dataThrough=cutoff.isoformat()))
    jobs.save_version(job, request, program, image, digests[0], summary)
