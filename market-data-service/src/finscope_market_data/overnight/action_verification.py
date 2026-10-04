"""Persist company-action coverage and mark contaminated training windows."""
import asyncio
from datetime import datetime, timedelta
import json
import logging

from finscope_market_data.forecast.trading_calendar import previous_session
from finscope_market_data.overnight.automation_models import AutomationContext
from finscope_market_data.overnight.universe import POOL_KEY

logger = logging.getLogger(__name__)


class ActionVerificationStore:
    def __init__(self, store):
        self.store = store
        with store.connect() as db:
            db.execute('''CREATE TABLE IF NOT EXISTS overnight_action_verification (
                code TEXT NOT NULL, received_at TEXT NOT NULL, payload TEXT NOT NULL,
                PRIMARY KEY(code, received_at))''')

    def save(self, result, received):
        value = {**result, 'receivedAt': received.isoformat()}
        if result['throughDate'] >= str(received.date()):
            raise ValueError('仅核验已完成交易日的公司行为')
        with self.store.connect() as db:
            db.execute('INSERT OR IGNORE INTO overnight_action_verification VALUES(?,?,?)',
                       (result['instrumentCode'], received.isoformat(), json.dumps(value)))

    def latest(self, code, through):
        with self.store.connect() as db:
            row = db.execute('SELECT payload FROM overnight_action_verification WHERE code=? AND received_at<=? ORDER BY received_at DESC LIMIT 1',
                             (code, through.isoformat())).fetchone()
        return json.loads(row[0]) if row else None

    def annotate(self, rows, through):
        coverage = {code: self.latest(code, through) for code in {row['instrumentCode'] for row in rows}}
        result = []
        for row in rows:
            record = coverage[row['instrumentCode']]
            start = datetime.fromisoformat(row['signalDate']).date()
            for _ in range(6):
                start = previous_session(start) if start else None
            verified = bool(record and start and record['fromDate'] <= str(start)
                            and record['throughDate'] >= row['exitAt'][:10])
            affected = bool(verified and any(str(start) < day <= row['exitAt'][:10] for day in record['exDates']))
            # Retain every test/selection outcome. Only fitting excludes known
            # raw-price jumps; we do not improve scores by dropping hard tests.
            result.append({**row, 'trainingEligible': not affected, 'actionAffected': affected,
                           'actionsVerified': verified})
        return result, {'verifiedRows': sum(row['actionsVerified'] for row in result),
                        'affectedRows': sum(row['actionAffected'] for row in result), 'totalRows': len(result),
                        'method': 'EX_DATE_AUDIT_NO_PRICE_REWRITE',
                        'limitation': '单一来源的事后除权日核验；不代表当时已知公告，也不等于含分红总收益'}


class OvernightActionVerification:
    def __init__(self, automation, provider):
        self.automation, self.provider = automation, provider
        self.store = ActionVerificationStore(automation.service.store)

    def tick(self):
        now, meta = self.automation.clock(), self.automation.store
        settings = meta.get('context') or AutomationContext().model_dump(mode='json', by_alias=True)
        if not settings['enabled'] or '13:00' <= now.strftime('%H:%M') < '18:00':
            return
        end = previous_session(now.date())
        if end is None:
            return
        universe = meta.get(POOL_KEY) or {}
        for member in universe.get('members', [])[:120]:
            code = member['instrumentCode']
            last = self.store.latest(code, now)
            if last and last['throughDate'] >= str(end):
                continue
            job = meta.claim(f'{now.date()}|ACTIONS|{code}', now,
                             {'phase': 'ACTIONS', 'signalDate': str(now.date()), 'instrumentCode': code})
            if not job:
                continue
            try:
                result = self.provider.fetch(code, end - timedelta(days=380), end)
                self.store.save(result, self.automation.clock())
                meta.finish(job, self.automation.clock(), status='COMPLETED', throughDate=str(end), exDateCount=len(result['exDates']))
            except Exception as error:
                logger.warning('Company action verification failed: %s', type(error).__name__)
                meta.finish(job, self.automation.clock(), status='FAILED', reason='公司行为源暂不可用；不把未知当作无除权')
            return

    async def run(self, stop):
        while not stop.is_set():
            try:
                await asyncio.to_thread(self.tick)
            except Exception:
                logger.exception('Company action verification tick failed')
            try:
                await asyncio.wait_for(stop.wait(), timeout=30)
            except TimeoutError:
                pass
