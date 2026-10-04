"""Automatic pre-decision capture; slow/missing sources never delay prediction."""
import asyncio
from dataclasses import asdict
from datetime import datetime, timedelta
import logging
from zoneinfo import ZoneInfo

from finscope_market_data.forecast.industry_features import load_industry_memberships
from finscope_market_data.forecast.trading_calendar import next_session
from finscope_market_data.models import DataCapability, StockSymbol
from finscope_market_data.overnight.automation_models import AutomationContext
from finscope_market_data.overnight.context_features import INDEX_CODES, context_time, stock_context
from finscope_market_data.overnight.context_store import OvernightContextStore
from finscope_market_data.overnight.engine import group_bars
from finscope_market_data.overnight.universe import POOL_KEY

logger = logging.getLogger(__name__)


class OvernightContextCapture:
    def __init__(self, automation, router, membership_path, history_path):
        self.automation = automation
        self.router = router
        self.clock = automation.clock
        self.contexts = OvernightContextStore(automation.service.store)
        self.membership_path, self.history_path = membership_path, history_path

    async def tick(self):
        now = self.clock()
        meta = self.automation.store
        settings = meta.get('context') or AutomationContext().model_dump(mode='json', by_alias=True)
        if not settings['enabled'] or next_session(now.date() - timedelta(days=1)) != now.date():
            return
        universe = meta.get(POOL_KEY) or {}
        codes = sorted(row['instrumentCode'] for row in universe.get('members', []))[:120]
        if not codes:
            return
        for cutoff in ('14:30', '14:45', '15:00'):
            observed = context_time(now.date(), cutoff)
            # Start after the lagged five-minute bar has closed. Complete or
            # miss before the decision; never backfill this table after hours.
            decision = observed + timedelta(minutes=10)
            if not observed + timedelta(seconds=20) <= now < decision - timedelta(minutes=2):
                continue
            key = f'{now.date()}|CONTEXT|{cutoff}'
            job = meta.claim(key, now, {'phase': 'CONTEXT', 'signalDate': str(now.date()), 'cutoff': cutoff})
            if not job:
                continue
            try:
                snapshot = await self.collect(codes, observed, cutoff, decision - timedelta(seconds=30))
                snapshot['universeFingerprint'] = universe['fingerprint']
                snapshot['memberships'] = [asdict(row) for row in load_industry_memberships(self.membership_path, self.history_path)]
                snapshot['receivedAt'] = self.clock().isoformat()
                snapshot.update(expectedSymbols=len(codes), capturedSymbols=len(snapshot['pool']))
                complete = len(snapshot['pool']) >= 20 and len(snapshot['pool']) / len(codes) >= .75
                snapshot['quality'] = 'SUFFICIENT' if complete else 'INSUFFICIENT_COVERAGE'
                frozen = self.contexts.freeze(snapshot)
                meta.finish(job, self.clock(), status='COMPLETED' if complete else 'PARTIAL', capturedSymbols=frozen['capturedSymbols'],
                            expectedSymbols=len(codes), fingerprint=frozen['fingerprint'])
            except Exception as error:
                logger.warning('Intraday context capture failed: %s', type(error).__name__)
                meta.finish(job, self.clock(), status='FAILED', reason='环境快照未在有效窗口完成；保留数据缺口')

    async def collect(self, codes, observed, cutoff, deadline):
        pool, indices, failures = {}, {}, []
        slots = asyncio.Semaphore(6)

        async def stock(code):
            async with slots:
                if self.clock() >= deadline:
                    return
                try:
                    bars = await asyncio.wait_for(self.automation.service.provider.fetch_async(code, observed), timeout=16)
                    value = stock_context(group_bars(bars), observed.date(), cutoff)
                    if value is not None and self.clock() <= deadline:
                        pool[code] = {**value, 'receivedAt': self.clock().isoformat(),
                                      'source': self.automation.service.provider.source}
                    else:
                        failures.append(code)
                except Exception:
                    failures.append(code)

        async def index(code):
            try:
                envelope = await asyncio.wait_for(self.router.fetch(DataCapability.QUOTE,
                    StockSymbol(market=code[-2:], code=code[:6]), provider_family='TENCENT'), timeout=15)
                quote = envelope.data
                if envelope.source_code != 'TENCENT_QUOTE' or quote.observed_at.tzinfo is None:
                    failures.append(code)
                    return
                stamp = quote.observed_at.astimezone(ZoneInfo('Asia/Shanghai')).replace(tzinfo=None)
                received = self.clock()
                if (observed <= stamp <= observed + timedelta(minutes=2) and stamp <= received <= deadline
                        and quote.previous_close and quote.previous_close > 0
                        and 'STALE' not in str(envelope.quality_status)):
                    indices[code] = {'return': quote.price / quote.previous_close - 1,
                        'observedAt': stamp.isoformat(), 'receivedAt': received.isoformat(), 'source': envelope.source_code}
                else:
                    failures.append(code)
            except Exception:
                failures.append(code)

        await asyncio.gather(*(stock(code) for code in codes), *(index(code) for code in INDEX_CODES))
        return {'signalDate': str(observed.date()), 'cutoff': cutoff, 'observedAt': observed.isoformat(),
                'pool': pool, 'indices': indices, 'failures': sorted(set(failures)),
                'scope': '固定研究股票池，并非全市场涨跌家数'}

    async def run(self, stop):
        while not stop.is_set():
            try:
                await self.tick()
            except Exception:
                logger.exception('Intraday context tick failed')
            try:
                await asyncio.wait_for(stop.wait(), timeout=20)
            except TimeoutError:
                pass
