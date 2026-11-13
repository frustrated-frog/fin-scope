"""Independent prospective validation; never shares the training executor."""
import asyncio
from concurrent.futures import ThreadPoolExecutor
from datetime import datetime, timedelta
import logging
from threading import Lock

from finscope_market_data.overnight.automation_models import AutomationContext
from finscope_market_data.overnight.direction_validation import summarize_direction
from finscope_market_data.overnight.error_audit import build_error_audit
from finscope_market_data.overnight.joint_validation import summarize_forward
from finscope_market_data.overnight.market_validation import summarize_market_forward
from finscope_market_data.overnight.fresh_validation import summarize_fresh_forward
from finscope_market_data.overnight.selection_validation import summarize_selection_forward
from finscope_market_data.overnight.weekly_diagnostics import build_weekly_diagnostics
from finscope_market_data.overnight.candidate_validation import summarize_candidate_forward
from finscope_market_data.overnight.production_direction import summarize_production, KEY as PRODUCTION_KEY

logger = logging.getLogger(__name__)
REFRESH_INTERVAL = timedelta(minutes=5)
POLL_SECONDS = 20
STATUS_KEY = 'validationWorker'


class OvernightValidationWorker:
    def __init__(self, automation, research):
        self.automation = automation
        self.research = research
        self.clock = automation.clock
        self._tick_lock = Lock()

    def tick(self):
        # A slow evaluation cannot queue overlapping copies in this process.
        if not self._tick_lock.acquire(blocking=False):
            return
        try:
            self._refresh()
        finally:
            self._tick_lock.release()

    def _refresh(self):
        now = self.clock()
        meta = self.automation.store
        settings = meta.get('context') or AutomationContext().model_dump(mode='json', by_alias=True)
        previous = meta.get(STATUS_KEY) or {}
        status = {'lastTickAt': now.isoformat(), 'lastCompletedAt': previous.get('lastCompletedAt'),
                  'intervalSeconds': int(REFRESH_INTERVAL.total_seconds()), 'status': 'RUNNING',
                  'errors': {}, 'refreshedKeys': []}
        if not settings['enabled']:
            meta.put(STATUS_KEY, {**status, 'status': 'DISABLED'})
            return
        meta.put(STATUS_KEY, status)
        store = self.automation.service.store
        evaluations = {
            'errorAudit': lambda: build_error_audit(store, meta, self.research.contexts, now),
            'weeklyDirectionAudit': lambda: build_weekly_diagnostics(store, meta, self.research.contexts, now),
            'marketForward': lambda: summarize_market_forward(store, meta, now),
            'freshForward': lambda: summarize_fresh_forward(store, meta, now),
            'selectionForward': lambda: summarize_selection_forward(store, meta, now),
            'candidateForward': lambda: summarize_candidate_forward(store, meta, now),
            PRODUCTION_KEY: lambda: summarize_production(store, meta, now),
            'jointForward': lambda: summarize_forward(store, now, meta),
            'closeDirectionForward': lambda: summarize_direction(store, now, meta),
            'contextDirectionForward': lambda: summarize_direction(store, now, meta, challenger=True),
        }
        for key, evaluate in evaluations.items():
            try:
                if not needs_refresh(meta.get(key), now):
                    continue
                # Keep the last successful value if this evaluation fails.
                meta.put(key, evaluate())
                status['refreshedKeys'].append(key)
            except Exception as error:
                status['errors'][key] = type(error).__name__
                logger.exception('Overnight validation failed for %s; retaining previous result', key)
        if status['refreshedKeys'] and not status['errors']:
            status['lastCompletedAt'] = self.clock().isoformat()
        meta.put(STATUS_KEY, {**status, 'status': 'PARTIAL' if status['errors'] else 'IDLE',
                              'lastFinishedAt': self.clock().isoformat()})

    async def run(self, stop):
        # to_thread() uses a shared executor also used by training/backfills.
        # A dedicated bounded worker keeps validation runnable under that load.
        with ThreadPoolExecutor(max_workers=1, thread_name_prefix='overnight-validation') as executor:
            loop = asyncio.get_running_loop()
            while not stop.is_set():
                try:
                    await loop.run_in_executor(executor, self.tick)
                except Exception:
                    logger.exception('Overnight validation worker tick failed')
                if stop.is_set():
                    break
                try:
                    await asyncio.wait_for(stop.wait(), timeout=POLL_SECONDS)
                except TimeoutError:
                    pass


def needs_refresh(value, now):
    try:
        age = now - datetime.fromisoformat((value or {})['computedAt'])
        return age >= REFRESH_INTERVAL or age < timedelta(0)
    except (KeyError, TypeError, ValueError):
        return True
