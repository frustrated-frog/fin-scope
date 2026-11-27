import json
from uuid import uuid4
import pytest

from finscope_market_data.paper.classic_recipe import Recipe
from finscope_market_data.paper.lab import PaperLab
from finscope_market_data.paper.parameter_grid import GridRequest, enqueue, results


def baseline(lab, frozen=True):
    request=dict(researchMode="CLASSIC",recipe=Recipe(family="TREND").model_dump(),capital=10000,
                 timeline=[dict(at="2026-10-08",codes=["600001"])],executionVersion=2,
                 start="2026-10-08",cutoff="2026-11-27",origin="基线",autoStart=True,
                 classicSnapshots=["frozen"] if frozen else [])
    job=lab.jobs.submit(request)["jobId"]
    with lab.db() as db:
        db.execute("UPDATE paper_job SET version=8,state='DONE' WHERE id=?", (job,))


def test_grid_reuses_frozen_dates_and_snapshots_and_survives_retry(tmp_path):
    lab=PaperLab(tmp_path/"paper.db",lambda:None)
    baseline(lab)
    req=GridRequest(requestId=uuid4(),frequencies=["DAILY","WEEKLY"],allocations=["EQUAL"],holdings=[3,5])
    assert enqueue(lab,8,req)["count"]==4
    assert enqueue(lab,8,req)["count"]==4
    with pytest.raises(ValueError,match="另一组参数"):
        enqueue(lab,8,req.model_copy(update={"holdings":[2]}))
    rows=results(lab,8)[0]["rows"]
    assert len(rows)==4 and all(r["net"] is None for r in rows)
    with lab.db() as db:
        frozen=json.loads(db.execute("SELECT request FROM paper_job WHERE id=?",(rows[0]["id"],)).fetchone()[0])
        assert frozen["start"]=="2026-10-08" and frozen["classicSnapshots"]==["frozen"]
        assert frozen["reference"]==8 and frozen["autoStart"] is False
        db.execute("INSERT INTO paper_version VALUES(9,'fixture','{}',?,'day')",
                   (json.dumps(dict(validationReturn=0,validationBenchmark=.01,fees=10,fills=2)),))
        db.execute("UPDATE paper_job SET version=9,state='DONE' WHERE id=?",(rows[0]["id"],))
    result=results(lab,8)[0]["rows"][0]
    assert result["version"]==9 and result["net"]==0 and result["fees"]==10


def test_invalid_grid_rolls_back_every_member_and_rejects_missing_snapshot(tmp_path):
    lab=PaperLab(tmp_path/"paper.db",lambda:None)
    baseline(lab)
    req=GridRequest(requestId=uuid4(),frequencies=["DAILY","WEEKLY"],allocations=["EQUAL"],holdings=[3,11])
    with pytest.raises(ValueError):
        enqueue(lab,8,req)
    assert not results(lab,8)
    other=PaperLab(tmp_path/"other.db",lambda:None)
    baseline(other,False)
    with pytest.raises(ValueError,match="冻结快照"):
        enqueue(other,8,req)
