"""Automatic historical sample collection, independent of live prediction deadlines."""
import asyncio
from datetime import datetime, timedelta
import logging

from finscope_market_data.overnight.automation_models import AutomationContext
from finscope_market_data.overnight.models import OvernightRequest

logger = logging.getLogger(__name__)
DESIRED_DAYS = 140


class OvernightHistoryBackfill:
    def __init__(self, automation, provider):
        self.automation = automation
        self.store = automation.store
        self.minutes = automation.service.store
        self.clock = automation.clock
        self.provider = provider

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
        codes = [position['instrumentCode'] for position in context['positions']]
        for job in self.store.jobs():
            if job.get('phase') == 'DISCOVER' and job['status'] == 'COMPLETED':
                codes.extend(row['instrumentCode'] for row in job.get('candidates', []))
        codes.extend(coverage)
        for code in list(dict.fromkeys(codes))[:200]:
            if coverage.get(code, {}).get('completeDays', 0) >= DESIRED_DAYS:
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
