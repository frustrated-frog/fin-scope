"""Persistent, first-attempt-only capture; downtime is recorded, never backfilled."""
import asyncio
from concurrent.futures import ThreadPoolExecutor
from datetime import datetime, timedelta
import logging

from finscope_market_data.forecast.trading_calendar import next_session
from finscope_market_data.overnight.models import OvernightRequest

SLOTS = ('14:30', '14:45')
logger = logging.getLogger(__name__)


class OvernightCapture:
    def __init__(self, service):
        self.service = service
        self.store = service.store
        self.last_settlement = None

    def configure(self, plan):
        previous = self.store.plan()
        now = self.service.clock().isoformat()
        if previous['enabled']:
            self._missed_before_change(previous, datetime.fromisoformat(now))
        payload = plan.model_dump(mode='json', by_alias=True)
        payload['enabledSince'] = now
        payload['updatedAt'] = now
        return self.store.save_plan(payload)

    def _missed_before_change(self, plan, now):
        since = datetime.fromisoformat(plan['enabledSince'])
        day = since.date()
        while day <= now.date():
            if next_session(day - timedelta(days=1)) == day:
                for cutoff in SLOTS:
                    start = datetime.fromisoformat(f'{day}T{cutoff}:00')
                    if since <= start <= now:
                        self.store.claim_run({'signalDate': day.isoformat(), 'cutoff': cutoff,
                            'status': 'MISSED', 'startedAt': now.isoformat(), 'instrumentCodes': plan['instrumentCodes'],
                            'costBps': plan['costBps'], 'results': [], 'reason': '计划变更前未完成留档，未补跑'})
            day += timedelta(days=1)

    def status(self):
        now = self.service.clock()
        runs = self.store.runs()
        for run in runs:
            end = datetime.fromisoformat(f"{run['signalDate']}T{run['cutoff']}:00") + timedelta(minutes=5)
            if run['status'] == 'RUNNING' and now >= end:
                run = {**run, 'status': 'INTERRUPTED', 'reason': '留档窗口已结束，未收到完成记录；不补造预测'}
                self.store.finish_run(run)
        return {'plan': self.store.plan(), 'slots': list(SLOTS), 'runs': self.store.runs(),
                'serverTime': now.isoformat(), 'calendarAvailable': next_session(now.date()) is not None}

    def _capture_one(self, code, day, cutoff, cost):
        now = self.service.clock()
        deadline = datetime.fromisoformat(f'{day}T{cutoff}:00') + timedelta(minutes=5)
        if now >= deadline:
            return {'instrumentCode': code, 'status': 'MISSED', 'reason': '等待采集时窗口已结束'}
        try:
            request = OvernightRequest(instrument_code=code, signal_date=day, mode='TAIL_ENTRY',
                                       cutoff=cutoff, cost_bps=cost)
            report = self.service.generate(request, freeze_all=True)
            return {'instrumentCode': code, 'status': report['status'], 'predictionId': report['id'],
                    'evidenceKind': report['evidenceKind'], 'dataThrough': report['dataThrough'],
                    'generatedAt': report['generatedAt'], 'warnings': report['warnings']}
        except Exception as error:
            logger.exception('Overnight capture failed for %s', code)
            return {'instrumentCode': code, 'status': 'FAILED', 'reason': type(error).__name__}

    def tick(self):
        now = self.service.clock()
        plan = self.store.plan()
        self.status()
        if plan['enabled']:
            since = datetime.fromisoformat(plan['enabledSince'])
            day = since.date()
            # Materialize missed slots since enabling, including days the process was stopped.
            while day <= now.date():
                if next_session(day - timedelta(days=1)) == day:
                    for cutoff in SLOTS:
                        start = datetime.fromisoformat(f'{day}T{cutoff}:00')
                        if start < since or now < start:
                            continue
                        live = now < start + timedelta(minutes=5)
                        run = {'signalDate': day.isoformat(), 'cutoff': cutoff,
                               'status': 'RUNNING' if live else 'MISSED', 'startedAt': now.isoformat(),
                               'instrumentCodes': plan['instrumentCodes'], 'costBps': plan['costBps'],
                               'results': [], 'reason': None if live else '服务未在窗口内完成采集，未补跑'}
                        if not self.store.claim_run(run) or not live:
                            continue
                        with ThreadPoolExecutor(max_workers=3) as pool:
                            run['results'] = list(pool.map(lambda code: self._capture_one(
                                code, day, cutoff, plan['costBps']), plan['instrumentCodes']))
                        run['completedAt'] = self.service.clock().isoformat()
                        run['status'] = 'COMPLETED'
                        self.store.finish_run(run)
                day += timedelta(days=1)
        # Settlement is independent of whether capture is enabled, and covers all archives.
        if self.last_settlement is None or now - self.last_settlement >= timedelta(minutes=5):
            self.service.refresh_outcomes()
            self.last_settlement = now

    async def run(self, stop):
        while not stop.is_set():
            try:
                await asyncio.to_thread(self.tick)
            except Exception:
                logger.exception('Overnight capture tick failed')
            try:
                await asyncio.wait_for(stop.wait(), timeout=20)
            except asyncio.TimeoutError:
                pass
