"""Keep the minute archive warm for experiment pools. Claims and backoff are per instrument."""

import asyncio
from datetime import datetime, time, timedelta
import logging
import uuid

from finscope_market_data.forecast.trading_calendar import (
    previous_session,
    next_session,
)
from finscope_market_data.overnight.engine import expected_times
from finscope_market_data.providers.base import ProviderError
from .engine import SHANGHAI

log = logging.getLogger(__name__)


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

    def refresh(self, code, now):
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
        try:
            bars = self.archive.research_bars(symbol, end)[-12000:]
            healthy = len(bars) >= 120 * 48 and sum(
                b.volume is not None for b in bars
            ) >= 0.95 * len(bars)
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
                bars = self.archive.research_bars(symbol, end)[-12000:]
            if (
                bars
                and bars[-1].ended_at == end
                and sum(b.volume is not None for b in bars) >= 0.95 * len(bars)
                and len(bars) >= 120 * 48
            ):
                status = "READY"
        except (ProviderError, ValueError, OSError) as problem:
            error = getattr(problem, "error_type", type(problem).__name__)
            status = "RETRY"
        clocks = set(expected_times("15:00"))
        sessions = {}
        for b in bars:
            sessions.setdefault(b.ended_at.date(), set()).add(
                b.ended_at.strftime("%H:%M")
            )
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
                    sum(b.volume is not None for b in bars),
                    sum(clocks <= s for s in sessions.values()),
                    str(max(sessions)) if sessions else None,
                    source,
                    error,
                    code,
                    token,
                ),
            )

    async def ensure(self, codes, now):
        if self.archive is None or self.provider is None:
            return
        self.register(codes)

        async def run(code):
            async with self.slots:
                await asyncio.to_thread(self.refresh, code, now)

        await asyncio.gather(*(run(c) for c in codes))

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
