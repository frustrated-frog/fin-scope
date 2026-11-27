"""Keep the minute archive warm for experiment pools. Claims and backoff are per instrument."""

import asyncio
from datetime import datetime, time, timedelta
from types import SimpleNamespace
import logging
import sqlite3
import uuid

from finscope_market_data.forecast.trading_calendar import (
    previous_session,
    next_session,
)
from finscope_market_data.overnight.engine import expected_times
from finscope_market_data.providers.base import ProviderError
from .engine import SHANGHAI

log = logging.getLogger(__name__)


def coverage_counts(bars):
    clocks = set(expected_times("15:00"))
    sessions = {}
    measured = 0
    for bar in bars:
        sessions.setdefault(bar.ended_at.date(), set()).add(
            bar.ended_at.strftime("%H:%M")
        )
        measured += bar.volume is not None
    return sum(clocks <= observed for observed in sessions.values()), measured


class ResearchDatasets:
    def __init__(self, lab, archive, provider, fallback):
        self.lab, self.archive = lab, archive
        self.provider, self.fallback = provider, fallback
        self.slots = asyncio.Semaphore(2)

    @staticmethod
    def schema(db):
        db.execute("""CREATE TABLE IF NOT EXISTS paper_dataset(code TEXT PRIMARY KEY, due TEXT DEFAULT '',
             lease TEXT, checked TEXT, status TEXT DEFAULT 'PENDING', bars INTEGER DEFAULT 0,
             measured INTEGER DEFAULT 0, complete INTEGER DEFAULT 0, lastDay TEXT, source TEXT, error TEXT,
             poolActive INTEGER DEFAULT 0)""")

        db.execute("""CREATE TABLE IF NOT EXISTS paper_measured_bar(code TEXT,at TEXT,open REAL,high REAL,
                   low REAL,close REAL,volume REAL,amount REAL, PRIMARY KEY(code,at))""")

    def save_quotes(self, code, rows, now):
        with self.lab.db() as db:
            self.schema(db)
            db.executemany(
                """INSERT INTO paper_measured_bar VALUES(?,?,?,?,?,?,?,?)
                      ON CONFLICT(code,at) DO NOTHING""",
                [
                    (
                        code,
                        b.observed_at.astimezone(SHANGHAI)
                        .replace(tzinfo=None)
                        .isoformat(),
                        b.open,
                        b.high,
                        b.low,
                        b.close,
                        b.volume,
                        b.amount,
                    )
                    for b in rows
                    if b.observed_at <= now and b.volume is not None
                ],
            )
            db.execute(
                """DELETE FROM paper_measured_bar WHERE code=? AND at <
                      (SELECT at FROM paper_measured_bar WHERE code=? ORDER BY at DESC LIMIT 1 OFFSET 11999)""",
                (code, code),
            )

    def bars(self, code, end, *, since=None):
        symbol = code + (".SH" if code.startswith("6") else ".SZ")
        historical = []
        if self.archive:
            historical = self.archive.research_bars(symbol, end, since=since) if since else self.archive.research_bars(symbol, end)
        combined = {b.ended_at: b for b in historical}
        with self.lab.db() as db:
            self.schema(db)
            for r in db.execute(
                "SELECT * FROM paper_measured_bar WHERE code=? AND at BETWEEN ? AND ? ORDER BY at",
                (code, since.isoformat() if since else "", end.isoformat()),
            ):
                at = datetime.fromisoformat(r["at"])
                original = combined.get(at)
                if original and (
                    original.volume is not None
                    or any(
                        abs(getattr(original, k) - r[k]) > 0.000101
                        for k in ("open", "high", "low", "close")
                    )
                ):
                    continue
                item = {
                    k: r[k]
                    for k in ("open", "high", "low", "close", "volume", "amount")
                }
                if original:
                    # A feed that omits turnover still needs a plausible share count
                    # against the retained archive amount, including rounding to yuan.
                    amount = original.amount
                    volume = r["volume"]
                    if (
                        r["amount"] is not None
                        and abs(amount - r["amount"]) > 0.501
                        or amount + 0.501 < (original.low - 0.005) * volume
                        or amount - 0.501 > (original.high + 0.005) * volume
                    ):
                        continue
                    item["amount"] = original.amount
                combined[at] = SimpleNamespace(ended_at=at, **item)
        times = sorted(combined)
        return [combined[t] for t in (times if since else times[-12000:])]

    def register(self, codes, *, current_pool=False):
        with self.lab.db() as db:
            self.schema(db)
            if current_pool:
                db.execute("UPDATE paper_dataset SET poolActive=0 WHERE poolActive=1")
            db.executemany(
                """INSERT INTO paper_dataset(code,poolActive) VALUES(?,?)
               ON CONFLICT(code) DO UPDATE SET poolActive=max(poolActive,excluded.poolActive)""",
                [(c, int(current_pool)) for c in codes],
            )

    def list(self):
        with self.lab.db() as db:
            self.schema(db)
            return [
                dict(r)
                for r in db.execute(
                    "SELECT code,status,checked,bars,measured,complete,lastDay,source,error FROM paper_dataset ORDER BY code"
                )
            ]

    def claim(self, code, now):
        token = uuid.uuid4().hex
        with self.lab.db() as db:
            claimed = db.execute(
                "UPDATE paper_dataset SET due=?,lease=?,status='SYNCING' WHERE code=? AND due<=?",
                (
                    (now + timedelta(minutes=5)).isoformat(),
                    token,
                    code,
                    now.isoformat(),
                ),
            )
            if not claimed.rowcount:
                return
        return token

    def refresh(self, code, now, token):
        symbol = code + (".SH" if code.startswith("6") else ".SZ")
        naive = now.replace(tzinfo=None)
        day = previous_session(
            now.date() + timedelta(days=int(now.time() >= time(15, 10)))
        )
        if not day:
            day = now.date() - timedelta(days=1)
        end = datetime.combine(day, time(15))
        status, source, error = "PARTIAL", None, None
        bars = []
        complete = measured = 0
        try:
            bars = self.bars(code, end)
            complete, measured = coverage_counts(bars)
            healthy = complete >= 120 and measured >= 0.95 * len(bars)
            if not healthy or bars[-1].ended_at < end:
                try:
                    start = (
                        max(
                            end.date() - timedelta(days=365),
                            bars[-1].ended_at.date() - timedelta(days=1),
                        )
                        if healthy
                        else None
                    )
                    incoming = (
                        self.provider.fetch(symbol, end, start=start)
                        if start
                        else self.provider.fetch(symbol, end)
                    )
                    source = self.provider.source
                except (ProviderError, ValueError, OSError) as problem:
                    if self.fallback is None:
                        raise
                    error = type(problem).__name__
                    incoming = self.fallback.fetch(symbol, end)
                    source = incoming.source
                self.archive.import_history(symbol, incoming, source, naive)
                bars = self.bars(code, end)
                complete, measured = coverage_counts(bars)
            if (
                bars
                and bars[-1].ended_at == end
                and measured >= 0.95 * len(bars)
                and complete >= 120
            ):
                status = "READY"
        except (
            ProviderError,
            ValueError,
            OSError,
            sqlite3.OperationalError,
        ) as problem:
            if isinstance(
                problem, sqlite3.OperationalError
            ) and problem.sqlite_errorcode not in (
                sqlite3.SQLITE_BUSY,
                sqlite3.SQLITE_LOCKED,
            ):
                raise
            error = getattr(problem, "error_type", type(problem).__name__)
            status = "RETRY"
        next_day = next_session(day)
        due = now + timedelta(minutes=15 if status == "RETRY" else 180)
        if status == "READY" and next_day:
            due = datetime.combine(next_day, time(15, 10), SHANGHAI)
        with self.lab.db() as db:
            db.execute(
                """UPDATE paper_dataset SET lease=NULL,due=?,checked=?,status=?,bars=?,measured=?,complete=?,
                    lastDay=?,source=COALESCE(?,source),error=? WHERE code=? AND lease=?""",
                (
                    due.isoformat(),
                    now.isoformat(),
                    status,
                    len(bars),
                    measured,
                    complete,
                    str(bars[-1].ended_at.date()) if bars else None,
                    source,
                    error,
                    code,
                    token,
                ),
            )

    def syncing(self, codes):
        with self.lab.db() as db:
            return any(
                db.execute(
                    "SELECT 1 FROM paper_dataset WHERE code=? AND status='SYNCING'",
                    (c,),
                ).fetchone()
                for c in codes
            )

    async def ensure(self, codes, now, *, wait=False):
        if self.archive is None or self.provider is None:
            return
        self.register(codes)

        async def run(code):
            async with self.slots:
                token = await asyncio.to_thread(self.claim, code, now)
                if token is None:
                    return
                if self.lab.router() is not None:
                    try:
                        quotes = await self.lab.bars(code)
                        await asyncio.to_thread(self.save_quotes, code, quotes, now)
                    except (ProviderError, ValueError, OSError) as exc:
                        log.info("分钟快照暂不可用 %s %s", code, type(exc).__name__)
                await asyncio.to_thread(self.refresh, code, now, token)

        await asyncio.gather(*(run(c) for c in codes))
        if wait and codes:
            # Another worker can already own the claim. Reuse its results on a cold start.
            deadline = asyncio.get_running_loop().time() + 90
            while await asyncio.to_thread(self.syncing, codes):
                if asyncio.get_running_loop().time() >= deadline:
                    break
                await asyncio.sleep(0.5)

    async def run(self, stop):
        if not self.archive or not self.provider:
            return
        while not stop.is_set():
            try:
                now = datetime.now(SHANGHAI)
                with self.lab.db() as db:
                    self.schema(db)
                    self.lab.jobs.schema(db)
                    codes = [
                        r[0]
                        for r in db.execute("""SELECT DISTINCT value FROM paper_version,json_each(spec,'$.codes')
                                UNION SELECT DISTINCT value FROM paper_job,json_each(request,'$.codes')""")
                    ]
                    db.executemany(
                        "INSERT OR IGNORE INTO paper_dataset(code) VALUES(?)",
                        [(c,) for c in codes],
                    )
                    due = [
                        r[0]
                        for r in db.execute(
                            """SELECT code FROM paper_dataset WHERE due<=?
                          AND (poolActive=1 OR code IN (SELECT value FROM paper_job,json_each(request,'$.codes')
                          UNION SELECT value FROM paper_version,json_each(spec,'$.codes'))) ORDER BY due,code LIMIT 2""",
                            (now.isoformat(),),
                        )
                    ]
                await self.ensure(due, now)
            except Exception:
                log.exception("量化行情档案维护失败")
            try:
                await asyncio.wait_for(stop.wait(), 20)
            except TimeoutError:
                pass
