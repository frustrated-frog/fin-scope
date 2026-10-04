"""Automatic historical sample collection, independent of live prediction deadlines."""
import asyncio
from datetime import datetime, timedelta
import logging

from finscope_market_data.overnight.automation_models import AutomationContext
from finscope_market_data.overnight.models import OvernightRequest
from finscope_market_data.overnight.universe import POOL_KEY, POOL_SIZE, extend_universe
from finscope_market_data.forecast.trading_calendar import previous_session

logger = logging.getLogger(__name__)
DESIRED_DAYS = 140


class OvernightHistoryBackfill:
    def __init__(self, automation, provider, snapshots=None):
        self.automation = automation
        self.store = automation.store
        self.minutes = automation.service.store
        self.clock = automation.clock
        self.provider = provider
        self.snapshots = snapshots

    def tick(self):
        now = self.clock()
        context = self.store.get('context') or AutomationContext().model_dump(mode='json', by_alias=True)
        if not context['enabled']:
            return
        # One serial, <=45s request at a time; none can enter the decision windows.
        if '14:24' <= now.strftime('%H:%M') < '15:05':
            return
        coverage = {row['instrumentCode']: row for row in self.minutes.coverage(now)}
        self._recover_interrupted(coverage, now)
        universe = self.store.get(POOL_KEY)
        if self.snapshots is not None and len((universe or {}).get('members', [])) < POOL_SIZE:
            universe = extend_universe(universe, self.snapshots.daily_bar_symbols(), now)
            self.store.put(POOL_KEY, universe)
        pool = [row['instrumentCode'] for row in (universe or {}).get('members', [])]
        latest_session = previous_session(now.date())
        codes = [position['instrumentCode'] for position in context['positions']]
        scans = [job for job in self.store.jobs() if job.get('phase') == 'DISCOVER' and job['status'] == 'COMPLETED']
        for job in scans[:2]:
            codes.extend(row['instrumentCode'] for row in job.get('candidates', []))
        codes.extend(pool)
        codes.extend(coverage)
        for code in list(dict.fromkeys(codes))[:200]:
            enough = coverage.get(code, {}).get('completeDays', 0) >= DESIRED_DAYS
            imported = self.minutes.history_import(code)
            current = latest_session and (imported or {}).get('lastDate', '') >= str(latest_session)
            if enough and (code not in pool or current):
                continue
            try:
                OvernightRequest(instrument_code=code, signal_date=now.date(), mode='TAIL_ENTRY', cutoff='14:30')
            except ValueError:
                continue
            key = f'{now.date()}|HISTORY|{code}'
            job = self.store.claim(key, now, {'phase': 'HISTORY', 'signalDate': str(now.date()),
                'instrumentCode': code, 'sourceCode': self.provider.source})
            if not job:
                continue
            try:
                # Today remains the live provider's responsibility, even after close.
                through = datetime.combine(now.date() - timedelta(days=1), datetime.min.time()).replace(hour=15)
                if enough and imported and code in pool:
                    start = datetime.fromisoformat(imported['lastDate']) - timedelta(days=5)
                    bars = self.provider.fetch(code, through, start=start.date())
                else:
                    bars = self.provider.fetch(code, through)
                result = self.minutes.import_history(code, bars, self.provider.source, self.clock())
                self.store.finish(job, self.clock(), status='COMPLETED', **result)
            except Exception as error:
                logger.warning('Minute history backfill failed for %s: %s', code, type(error).__name__)
                reason = str(error) if isinstance(error, ValueError) else '历史分钟源暂不可用；后台将有限重试'
                self.store.finish(job, self.clock(), status='FAILED', reason=reason)
            return

    def _recover_interrupted(self, coverage, now):
        for job in self.store.jobs(history=True):
            if job['status'] != 'RUNNING' or now - datetime.fromisoformat(job['startedAt']) < timedelta(minutes=10):
                continue
            code = job['instrumentCode']
            imported = self.minutes.history_import(code)
            if imported and coverage.get(code, {}).get('completeDays', 0) >= DESIRED_DAYS:
                self.store.finish(job, now, status='COMPLETED', reason='任务中断后已核实历史数据落库', **imported)
            else:
                self.store.finish(job, now, status='FAILED', reason='上次补数任务中断，后台将有限重试')

    async def run(self, stop):
        while not stop.is_set():
            try:
                await asyncio.to_thread(self.tick)
            except Exception:
                logger.exception('Minute history backfill tick failed')
            try:
                await asyncio.wait_for(stop.wait(), timeout=20)
            except asyncio.TimeoutError:
                pass
