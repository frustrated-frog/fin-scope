"""Persist immutable research versions and independently funded forward accounts."""
import asyncio
from bisect import bisect_right
from contextlib import closing, contextmanager
from datetime import datetime, timedelta
import hashlib
import json
import logging
import sqlite3
from typing import Literal

from fastapi import APIRouter, HTTPException
from pydantic import BaseModel, Field

from finscope_market_data.models import DataCapability, MinutePriceBar, StockSymbol
from .engine import ENGINE, INTERVAL, SHANGHAI, Rule, advance, money, new_book, valid_bar

from .research import JobRequest, PythonRule, ResearchJobs
from .python_runtime import PythonStrategy

log = logging.getLogger(__name__)


class Review(BaseModel):
    at: str = Field(max_length=50)
    text: str = Field(min_length=1, max_length=6000)
    model: str = Field(max_length=200)


class Research(BaseModel):
    rule: Rule | PythonRule
    pool: Literal["DISCOVERY", "WATCHLIST"]
    source: str = Field(max_length=500)
    capital: float = Field(gt=0, le=1e10, allow_inf_nan=False)
    codes: list[str] = Field(min_length=1, max_length=24)
    origin: str = Field(max_length=2000)
    parent: int | None = None


def stamp():
    return datetime.now(SHANGHAI)


def dumps(value):
    return json.dumps(value, ensure_ascii=False, allow_nan=False, separators=(",", ":"))


class PaperLab:
    def __init__(self, path, router, archive=None, history_provider=None):
        self.path = path
        self.router = router
        self.jobs = ResearchJobs(self, archive, history_provider)
        self.limit = asyncio.Semaphore(4)

    @contextmanager
    def db(self):
        self.path.parent.mkdir(parents=True, exist_ok=True)
        with closing(sqlite3.connect(self.path, timeout=10)) as db, db:
            db.row_factory = sqlite3.Row
            db.executescript("""
              CREATE TABLE IF NOT EXISTS paper_version (
                id INTEGER PRIMARY KEY, fingerprint TEXT UNIQUE NOT NULL, spec TEXT NOT NULL,
                history TEXT NOT NULL, created TEXT NOT NULL);
              CREATE TABLE IF NOT EXISTS paper_account (
                id INTEGER PRIMARY KEY REFERENCES paper_version(id), started TEXT NOT NULL,
                book TEXT NOT NULL, revision INTEGER NOT NULL DEFAULT 0, status TEXT NOT NULL);
              CREATE TABLE IF NOT EXISTS paper_review (
                id INTEGER NOT NULL, at TEXT NOT NULL, review TEXT NOT NULL, PRIMARY KEY(id,at));
              CREATE TABLE IF NOT EXISTS paper_point (
                id INTEGER NOT NULL, at TEXT NOT NULL, point TEXT NOT NULL, PRIMARY KEY(id,at));
            """)
            if "lifecycle" not in {r["name"] for r in db.execute("PRAGMA table_info(paper_account)")}:
                db.execute("ALTER TABLE paper_account ADD COLUMN lifecycle TEXT NOT NULL DEFAULT 'RUNNING'")
            yield db

    async def bars(self, code):
        symbol = StockSymbol(market="SH" if code.startswith("6") else "SZ", code=code)
        async with self.limit:
            envelope = await self.router().fetch(DataCapability.MINUTE_BARS, symbol, frequency=5, limit=320)
        bars = [MinutePriceBar.model_validate(b) for b in envelope.data or []]
        return sorted((b for b in bars if b.symbol == symbol and valid_bar(b)), key=lambda b: b.observed_at)

    async def panel(self, codes):
        result = await asyncio.gather(*(self.bars(c) for c in codes))
        return dict(zip(codes, result))

    @staticmethod
    def frames(panel, cutoff):
        # Restrict all comparisons to timestamps actually present for every member.
        times = {c: [b.observed_at.astimezone(SHANGHAI) for b in bars] for c, bars in panel.items()}
        common = set.intersection(*(set(t) for t in times.values()))
        for end in sorted(t for t in common if t <= cutoff):
            yield end, {c: bars[max(0, (n := bisect_right(times[c], end)) - 49):n] for c, bars in panel.items()}

    async def research(self, spec, auto_start=False):
        if isinstance(spec.rule, PythonRule):
            raise HTTPException(422, "Python 策略须经 Codex 任务验证")
        spec.codes = list(dict.fromkeys(spec.codes))
        if any(len(c) != 6 or not c.isdigit() or not c.startswith(("00","30","60","68")) for c in spec.codes):
            raise HTTPException(422, "股票池仅支持有五分钟行情的沪深 A 股")
        spec.capital = money(spec.capital)
        if spec.capital <= 0:
            raise HTTPException(422, "本金至少一分钱")
        payload = spec.model_dump(mode="json")
        fingerprint = hashlib.sha256(dumps([ENGINE, payload]).encode()).hexdigest()
        parent = None
        with self.db() as db:
            row = db.execute("SELECT id FROM paper_version WHERE fingerprint=?", (fingerprint,)).fetchone()
            if row:
                if auto_start:
                    self.open_account(db, row[0], spec)
                return {"id":row[0]}
            if spec.parent:
                parent = self.spec(db, spec.parent)
                if isinstance(parent.rule, PythonRule):
                    raise HTTPException(422, "请使用 Codex 创建 Python 策略对照")
                if (spec.codes, spec.pool, spec.capital) != (parent.codes, parent.pool, parent.capital):
                    raise HTTPException(422, "因子对照须沿用原版本股票池和本金")
                if (spec.rule.entry, spec.rule.exit, spec.rule.slots) != (parent.rule.entry, parent.rule.exit, parent.rule.slots):
                    raise HTTPException(422, "因子实验须沿用原版本阈值和仓位")
        now = stamp()
        panel = await self.panel(spec.codes)
        book = new_book(spec.capital)
        control = new_book(spec.capital) if parent else None
        curve = []
        for end, history in self.frames(panel, now - timedelta(seconds=5)):
            available = end + timedelta(seconds=3)
            point = advance(book, spec.rule, history, end, available, spec.capital)
            sample = {k:point[k] for k in ("at","equity","benchmark")}
            if parent:
                advance(control, parent.rule, history, end, available, spec.capital)
                sample["control"] = control["equity"]
            curve.append(sample)
        if len(curve) <= max(t.window for t in spec.rule.terms):
            raise HTTPException(422, "股票池共同的历史五分钟行情不足，无法验证此版本")
        summary = dict(curve=curve, fills=book["fills"], fees=book["fees"], engine=ENGINE)
        with self.db() as db:
            db.execute("INSERT INTO paper_version(fingerprint,spec,history,created) VALUES(?,?,?,?) ON CONFLICT(fingerprint) DO NOTHING",
                       (fingerprint, dumps(payload), dumps(summary), now.isoformat()))
            version = db.execute("SELECT id FROM paper_version WHERE fingerprint=?", (fingerprint,)).fetchone()[0]
            if auto_start:
                self.open_account(db, version, spec)
            return {"id": version}

    @staticmethod
    def spec(db, version):
        row = db.execute("SELECT spec FROM paper_version WHERE id=?", (version,)).fetchone()
        if not row:
            raise HTTPException(404, "实验版本不存在")
        return Research.model_validate_json(row[0])

    def open_account(self, db, version, spec):
        book = new_book(spec.capital)
        if spec.parent:
            book["control"] = new_book(spec.capital)
            book["controlRule"] = self.spec(db, spec.parent).rule.model_dump()
        db.execute("INSERT OR IGNORE INTO paper_account(id,started,book,status) VALUES(?,?,?,?)",
                   (version, stamp().isoformat(), dumps(book), "等待盘中行情"))

    def deploy(self, version):
        with self.db() as db:
            self.open_account(db, version, self.spec(db, version))
        return {"id":version}

    def catalog(self):
        with self.db() as db:
            rows = db.execute("""SELECT v.id,v.spec,v.created,a.started,a.status,a.lifecycle,a.book FROM paper_version v
                LEFT JOIN paper_account a ON v.id=a.id ORDER BY v.id DESC LIMIT 200""").fetchall()
        return [dict(id=r["id"], spec=json.loads(r["spec"]), created=r["created"], started=r["started"],
                     status=r["status"], lifecycle=r["lifecycle"], book=json.loads(r["book"]) if r["book"] else None) for r in rows]

    def transition(self, version, state):
        allowed = {"PAUSED": ("RUNNING",), "RUNNING": ("PAUSED",),
                   "STOPPED": ("RUNNING","PAUSED"), "ARCHIVED": ("STOPPED",)}
        labels = {"PAUSED":"已暂停","RUNNING":"等待新行情","STOPPED":"已停止","ARCHIVED":"已归档"}
        with self.db() as db:
            db.execute("BEGIN IMMEDIATE")
            row = db.execute("SELECT lifecycle,book FROM paper_account WHERE id=?", (version,)).fetchone()
            if not row:
                raise HTTPException(404, "账户不存在")
            if row["lifecycle"] == state:
                return {"id": version}
            if row["lifecycle"] not in allowed[state]:
                raise HTTPException(422, "账户状态不允许此操作")
            book = json.loads(row["book"])
            for account in [book, book.get("control")]:
                if account:
                    account["pending"] = []
            if state == "RUNNING":
                book["resumedAt"] = stamp().isoformat()
            db.execute("UPDATE paper_account SET lifecycle=?,status=?,book=?,revision=revision+1 WHERE id=?",
                       (state,labels[state],dumps(book),version))
        return {"id": version}

    def pending_reviews(self):
        now = stamp()
        through = now.date()
        if now.strftime("%H:%M") < "15:10":
            through -= timedelta(days=1)
        with self.db() as db:
            self.jobs.schema(db)
            for row in db.execute("""SELECT r.id,r.review FROM paper_review r JOIN paper_job j ON j.version=r.id
                    JOIN paper_account a ON a.id=r.id WHERE j.state='DONE' AND a.lifecycle='RUNNING'
                    AND json_extract(j.request,'$.autoIterate')=1 AND r.at<?
                    AND NOT EXISTS (SELECT 1 FROM paper_job child WHERE child.auto_key='forward:'||r.id)
                    AND r.at=(SELECT max(at) FROM paper_review WHERE id=r.id AND at<?)""",
                    ((through + timedelta(days=1)).isoformat(),)*2).fetchall():
                self.jobs.after_review(db,row["id"],Review.model_validate_json(row["review"]))
            rows = db.execute("""
                WITH closes AS (
                    SELECT id, substr(at,1,10) AS day, max(at) AS at FROM paper_point
                    WHERE at < ? GROUP BY id, substr(at,1,10)
                )
                SELECT c.id, c.day FROM closes c LEFT JOIN paper_review r ON r.id=c.id AND r.at=c.at
                WHERE r.id IS NULL ORDER BY c.day DESC,c.id
            """, ((through + timedelta(days=1)).isoformat(),)).fetchall()
        return [dict(row) for row in rows]

    @staticmethod
    def day_summary(points, prior, capital):
        if not points:
            return None
        close = points[-1]
        events = {}
        fees = 0.0
        fills = 0
        for point in points:
            for event in point["events"]:
                key = (event["code"], event["side"], event["action"], event["reason"])
                group = events.setdefault(key, dict(code=key[0], side=key[1], action=key[2],
                                                    reason=key[3], count=0, shares=0))
                group["count"] += 1
                if event["action"] == "成交":
                    group["shares"] += event["quantity"]
                    fills += 1
                    fees += event["fee"]
        opening_equity = prior["equity"] if prior else capital
        opening_benchmark = prior["benchmark"] if prior else capital
        return dict(at=close["at"], firstAt=points[0]["at"], observations=len(points),
                    equity=close["equity"], openingEquity=opening_equity, pnl=money(close["equity"]-opening_equity),
                    benchmark=close["benchmark"], openingBenchmark=opening_benchmark,
                    cash=close.get("cash"), holdings=close.get("holdings"), control=close.get("control"),
                    fills=fills, fees=money(fees), decisions=list(events.values()))

    def detail(self, version, day):
        with self.db() as db:
            self.jobs.schema(db)
            db.execute("BEGIN")
            spec = self.spec(db, version)
            history = json.loads(db.execute("SELECT history FROM paper_version WHERE id=?", (version,)).fetchone()[0])
            days = [r[0] for r in db.execute("SELECT DISTINCT substr(at,1,10) FROM paper_point WHERE id=? ORDER BY 1 DESC LIMIT 365", (version,))]
            selected = day or (days[0] if days else "")
            points = [json.loads(r[0]) for r in db.execute(
                "SELECT point FROM paper_point WHERE id=? AND at>=? AND at<? ORDER BY at LIMIT 256",
                (version, selected, selected+"Z"))]
            closes = [json.loads(r[0]) for r in db.execute("""
                SELECT p.point FROM paper_point p JOIN
                (SELECT max(at) AS at FROM paper_point WHERE id=? GROUP BY substr(at,1,10)
                 ORDER BY at DESC LIMIT 365) d ON p.at=d.at WHERE p.id=? ORDER BY p.at""", (version,version))]
            daily = [{k:p[k] for k in ("at","equity","benchmark","control") if k in p} for p in closes]
            prior = db.execute("SELECT point FROM paper_point WHERE id=? AND at<? ORDER BY at DESC LIMIT 1",
                               (version, selected)).fetchone()
            row = db.execute("SELECT review FROM paper_review WHERE id=? AND at>=? AND at<? ORDER BY at DESC LIMIT 1",
                             (version, selected, selected+"Z")).fetchone()
            evidence=db.execute("SELECT evidence FROM paper_forward_evidence WHERE version=?", (version,)).fetchone()
            account = db.execute("SELECT book FROM paper_account WHERE id=?", (version,)).fetchone()
        return dict(forwardEvidence=json.loads(evidence[0]) if evidence else None,history=history, days=days, day=selected, points=points, daily=daily, spec=spec.model_dump(),
                    daySummary=self.day_summary(points, json.loads(prior[0]) if prior else None, spec.capital),
                    book=json.loads(account[0]) if account else None, review=json.loads(row[0]) if row else None)

    def save_review(self, version, review):
        with self.db() as db:
            existing = db.execute("SELECT 1 FROM paper_point WHERE id=? AND at=?", (version, review.at)).fetchone()
            if not existing:
                raise HTTPException(422, "没有对应行情时点的前向记录")
            db.execute("INSERT OR IGNORE INTO paper_review(id,at,review) VALUES(?,?,?)",
                       (version, review.at, review.model_dump_json()))
            now = stamp()
            if review.at[:10] < str(now.date()) or (review.at[:10] == str(now.date()) and now.strftime("%H:%M") >= "15:10"):
                self.jobs.after_review(db, version, review)
            return json.loads(db.execute("SELECT review FROM paper_review WHERE id=? AND at=?", (version,review.at)).fetchone()[0])

    async def tick(self):
        now = stamp()
        if now.weekday() >= 5 or not ("09:35" <= now.strftime("%H:%M") <= "11:36"
                                      or "13:05" <= now.strftime("%H:%M") <= "15:06"):
            return
        with self.db() as db:
            rows = db.execute("SELECT a.*,v.spec FROM paper_account a JOIN paper_version v ON a.id=v.id WHERE lifecycle='RUNNING'").fetchall()
        codes = sorted({c for row in rows for c in json.loads(row["spec"])["codes"]})
        data = dict(zip(codes, await asyncio.gather(*(self.bars(c) for c in codes), return_exceptions=True)))
        slots = asyncio.Semaphore(3)
        async def update(row):
            async with slots:
                await self.update_account(row, data, now)
        await asyncio.gather(*(update(row) for row in rows))

    async def update_account(self, row, data, now):
        spec = Research.model_validate_json(row["spec"])
        panel = {c: data[c] if isinstance(data[c], list) else [] for c in spec.codes}
        cutoff = now - timedelta(seconds=3)
        closed = {c: [b for b in bars if b.observed_at <= cutoff] for c, bars in panel.items()}
        end = max((bars[-1].observed_at for bars in closed.values() if bars), default=None)
        seen = stamp()
        if not end or seen - end > INTERVAL:
            with self.db() as db:
                db.execute("UPDATE paper_account SET status=? WHERE id=? AND revision=?",
                           ("行情缺失或延迟，等待同步", row["id"], row["revision"]))
            return
        end = end.astimezone(SHANGHAI)
        history = {c: bars[-49:] for c, bars in closed.items() if bars and bars[-1].observed_at == end}
        if len(history) < len(panel) and seen - end < timedelta(seconds=45):
            return
        book = json.loads(row["book"])
        last = max(book["last"] or row["started"], book.get("resumedAt", ""))
        if end.isoformat() <= last:
            return
        try:
            point = await self.forward(book, spec.rule, spec, history, end, seen, decision_clock=stamp)
            if "controlRule" in book:
                rule = book["controlRule"]
                control_rule = PythonRule.model_validate(rule) if rule.get("kind") == "python" else Rule.model_validate(rule)
                control = await self.forward(book["control"], control_rule, spec, history, end, seen, decision_clock=stamp)
                point["control"] = control["equity"]
                point["controlEvents"] = control["events"]
        except Exception as error:
            log.warning("策略 %s 运行失败: %s", row["id"], type(error).__name__)
            with self.db() as db:
                db.execute("UPDATE paper_account SET status=? WHERE id=? AND revision=?",
                           ("策略运行失败，本时点未执行",row["id"],row["revision"]))
            return
        point["previousAt"] = last
        previous = datetime.fromisoformat(last).astimezone(SHANGHAI)
        # Count missing observations within today's two sessions, excluding the lunch break.
        morning = end.replace(hour=9, minute=35, second=0, microsecond=0)
        afternoon = morning.replace(hour=13, minute=5)
        point["missedBars"] = sum(
            previous < start + i * INTERVAL < end
            for start in (morning, afternoon) for i in range(24)
        )
        with self.db() as db:
            changed = db.execute("UPDATE paper_account SET book=?,revision=revision+1,status=? WHERE id=? AND revision=?",
                                 (dumps(book), "部分行情缺失，沿用最近估值" if book["stale"] else "已同步盘中行情", row["id"], row["revision"]))
            if changed.rowcount == 1:
                db.execute("INSERT INTO paper_point VALUES(?,?,?)", (row["id"], point["at"], dumps(point)))

    async def forward(self, book, rule, spec, history, end, seen, decision_clock=None):
        if isinstance(rule, PythonRule):
            def compute():
                with PythonStrategy(rule.code, rule.image) as strategy:
                    return advance(book, None, history, end, seen, spec.capital, universe=spec.codes, decide=strategy.decide, decision_clock=decision_clock)
            return await asyncio.to_thread(compute)
        return advance(book, rule, history, end, seen, spec.capital, universe=spec.codes, decision_clock=decision_clock)

    async def run(self, stop):
        while not stop.is_set():
            try:
                await self.tick()
            except Exception:
                log.exception("paper account update failed")
            try:
                await asyncio.wait_for(stop.wait(), timeout=25)
            except TimeoutError:
                pass

    def routes(self):
        router = APIRouter(prefix="/v1/paper")
        router.get("/versions")(self.catalog)

        @router.post("/versions")
        async def create(spec: Research, auto_start: bool = False):
            return await self.research(spec, auto_start)

        router.get("/versions/pending-reviews")(self.pending_reviews)

        router.get("/versions/jobs")(self.jobs.list)

        @router.post("/versions/jobs")
        def queue(value: JobRequest):
            try:
                return self.jobs.enqueue(value)
            except ValueError as exc:
                raise HTTPException(422,str(exc)) from exc

        @router.post("/versions/jobs/{job}/retry")
        def retry(job: int):
            try:
                return self.jobs.retry(job)
            except ValueError as exc:
                raise HTTPException(422,str(exc)) from exc

        @router.get("/versions/{version}")
        def read(version: int, day: str = ""):
            return self.detail(version, day)

        @router.post("/versions/{version}/review")
        def review(version: int, value: Review):
            return self.save_review(version, value)

        @router.post("/versions/{version}/replay")
        def replay(version: int):
            try:
                return self.jobs.replay(version)
            except ValueError as exc:
                raise HTTPException(422,str(exc)) from exc

        @router.post("/versions/{version}/state/{state}")
        def transition(version: int, state: Literal["RUNNING","PAUSED","STOPPED","ARCHIVED"]):
            return self.transition(version, state)

        @router.post("/versions/{version}/deploy")
        def deploy(version: int):
            return self.deploy(version)
        return router
