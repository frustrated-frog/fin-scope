"""Persist a bounded parameter grid in the existing classic backtest queue."""
from datetime import datetime
from itertools import product
import json
from typing import Literal
from uuid import UUID

from pydantic import BaseModel, ConfigDict, Field

from .classic_recipe import Recipe
from .engine import SHANGHAI


class GridRequest(BaseModel):
    model_config = ConfigDict(extra="forbid")
    requestId: UUID
    frequencies: list[Literal["DAILY", "WEEKLY", "MONTHLY"]] = Field(min_length=1, max_length=3)
    allocations: list[Literal["EQUAL", "INVERSE_VOL", "SCORE", "MIN_VARIANCE", "RISK_PARITY"]] = Field(min_length=1, max_length=5)
    holdings: list[int] = Field(min_length=1, max_length=10)


def enqueue(lab, version, grid):
    batch = str(grid.requestId)
    axes = [sorted(set(values)) for values in (grid.frequencies, grid.allocations, grid.holdings)]
    combinations = list(product(*axes))
    if not 2 <= len(combinations) <= 24:
        raise ValueError("每批可选 2 至 24 种不同的参数组合")
    with lab.db() as db:
        lab.jobs.schema(db)
        db.execute("BEGIN IMMEDIATE")
        saved = db.execute("SELECT json_extract(request,'$.reference'), json_extract(request,'$.gridAxes') "
                           "FROM paper_job WHERE auto_key=?", ("grid:"+batch+":0",)).fetchone()
        if saved:
            if saved[0] != version or json.loads(saved[1]) != axes:
                raise ValueError("该提交编号已用于另一组参数")
            return {"batch":batch, "count":len(combinations)}
        row = db.execute("SELECT request FROM paper_job WHERE version=? AND state='DONE' "
                         "AND json_extract(request,'$.researchMode')='CLASSIC' ORDER BY id LIMIT 1", (version,)).fetchone()
        if not row:
            raise ValueError("请选择已完成的经典策略实验作为基线")
        base = json.loads(row[0])
        codes = {code for entry in base["timeline"] for code in entry["codes"]}
        if (not codes or len(base.get("classicSnapshots", [])) != (len(codes)+15)//16
                or base.get("executionVersion") != 2):
            raise ValueError("基线缺少完整冻结快照或使用不同的执行版本")
        created = datetime.now(SHANGHAI).isoformat()
        for i, (cycle, allocation, holdings) in enumerate(combinations):
            recipe = Recipe.model_validate(dict(base["recipe"], rebalance=cycle, allocation=allocation, holdings=holdings))
            request = dict(base, reference=version, recipe=recipe.model_dump(), grid=batch, gridAxes=axes,
                           question=None, origin=f"参数研究 · 基线 #{version}", requestId=None, autoStart=False)
            db.execute("INSERT INTO paper_job(state,request,created,auto_key) VALUES('QUEUED',?,?,?)",
                       (json.dumps(request,ensure_ascii=False), created, f"grid:{batch}:{i}"))
    return {"batch":batch, "count":len(combinations)}


def results(lab, version):
    with lab.db() as db:
        lab.jobs.schema(db)
        rows = db.execute("""
          SELECT j.id,j.state,j.version,j.created,json_extract(j.log,'$[#-1]') AS latest,
            json_extract(j.request,'$.grid') AS batch,json_extract(j.request,'$.recipe') AS recipe,
            json_extract(v.history,'$.validationReturn') AS net,json_extract(v.history,'$.validationBenchmark') AS benchmark,
            json_extract(v.history,'$.fees') AS fees,json_extract(v.history,'$.fills') AS fills
          FROM paper_job j LEFT JOIN paper_version v ON j.version=v.id WHERE j.auto_key LIKE 'grid:%'
            AND json_extract(j.request,'$.reference')=? ORDER BY j.id DESC""",(version,))
        groups = {}
        for row in rows:
            group = groups.setdefault(row["batch"],dict(batch=row["batch"],created=row["created"],rows=[]))
            recipe = json.loads(row["recipe"])
            group["rows"].append({**{key:row[key] for key in ("id","state","version","latest","net","benchmark","fees","fills")},
                                  **{key:recipe[key] for key in ("rebalance","allocation","holdings")}})
        for group in groups.values():
            group["rows"].reverse()
    return list(groups.values())
