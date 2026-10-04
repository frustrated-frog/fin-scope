import asyncio
import hashlib
import json
import logging
from concurrent.futures import ThreadPoolExecutor
from datetime import datetime, timedelta

from finscope_market_data.forecast.trading_calendar import next_session
from finscope_market_data.overnight.automation_models import AutomationContext
from finscope_market_data.overnight.automation_store import AutomationStore
from finscope_market_data.overnight.models import OvernightRequest
from finscope_market_data.overnight.history_backfill import DESIRED_DAYS

logger = logging.getLogger(__name__)
SLOTS = (('14:20', '14:30'), ('14:40', '14:45'))


class OvernightAutomation:
    def __init__(self, service, scanner):
        self.service = service
        self.scanner = scanner
        self.store = AutomationStore(service.store)
        self.clock = service.clock

    def sync_context(self, context: AutomationContext):
        value = {**context.model_dump(mode='json', by_alias=True), 'receivedAt': self.clock().isoformat()}
        self.store.put('context', value)
        return {'receivedAt': value['receivedAt']}

    def status(self):
        now = self.clock()
        context = self.store.get('context') or AutomationContext().model_dump(mode='json', by_alias=True)
        received = context.get('receivedAt')
        fresh = bool(received and 0 <= (now - datetime.fromisoformat(received)).total_seconds() <= 180)
        next_day = next_session(now.date() - timedelta(days=1))
        next_tail = None
        for _, slot in SLOTS:
            if next_day == now.date() and now < datetime.fromisoformat(f'{next_day}T{slot}'):
                next_tail = f'{next_day}T{slot}:00'
                break
        if not next_tail and next_day:
            next_day = next_session(now.date())
            next_tail = f'{next_day}T14:30:00' if next_day else None
        holding_status = 'WAITING_CLOSE'
        if not fresh:
            holding_status = 'WAITING_LEDGER'
        elif not context['positions']:
            holding_status = 'EMPTY'
        elif now.hour >= 18:
            holding_status = 'WINDOW_CLOSED'
        elif now.strftime('%H:%M') >= '15:10':
            holding_status = 'ACTIVE'
        jobs = self.store.jobs()
        return {'enabled': context['enabled'], 'candidateLimit': context['candidateLimit'],
            'serverTime': now.isoformat(), 'calendarAvailable': next_day is not None,
            'tradingDay': next_session(now.date() - timedelta(days=1)) == now.date(),
            'nextTailAt': next_tail, 'ledgerReceivedAt': received, 'ledgerFresh': fresh,
            'positionCount': len(context['positions']), 'holdingStatus': holding_status,
            'joint': self.service.joint.status(now) if getattr(self.service, 'joint', None) is not None else None,
            'heartbeat': self.store.get('heartbeat'),
            'history': {'desiredDays': DESIRED_DAYS, 'coverage': self.service.store.coverage(now),
                'jobs': [{k: v for k, v in job.items() if k != 'token'} for job in self.store.jobs(history=True)]},
            'jobs': [{k: v for k, v in job.items() if k not in ('token', 'observations')} for job in jobs]}

    def tick(self):
        now = self.clock()
        old_heartbeat = self.store.get('heartbeat')
        self.store.put('heartbeat', {'lastTickAt': now.isoformat(), 'error': None})
        context = self.store.get('context') or AutomationContext().model_dump(mode='json', by_alias=True)
        if not context['enabled']:
            return
        for job in self.store.jobs():
            if job['status'] == 'RUNNING' and now - datetime.fromisoformat(job['startedAt']) >= timedelta(minutes=10):
                self.store.finish(job, now, status='FAILED', reason='任务租约到期；仅在原有效窗口内重试')
        # Materialize downtime once. The UI retains the latest 100 jobs, without inventing signals.
        start = datetime.fromisoformat(old_heartbeat['lastTickAt']).date() if old_heartbeat else now.date()
        day = max(start, now.date() - timedelta(days=30))
        while day <= now.date():
            if next_session(day - timedelta(days=1)) == day:
                for prepare, cutoff in SLOTS:
                    self._tail(day, prepare, cutoff, context)
            day += timedelta(days=1)
        if next_session(now.date() - timedelta(days=1)) == now.date():
            self._holdings(context)

    def _tail(self, day, prepare, cutoff, context):
        now = self.clock()
        cutoff_at = datetime.fromisoformat(f'{day}T{cutoff}')
        metadata = {'signalDate': str(day), 'cutoff': cutoff, 'mode': 'TAIL_ENTRY', 'phase': 'PREDICT'}
        key = f'{day}|TAIL|{cutoff}'
        if now >= cutoff_at + timedelta(minutes=5):
            existing = self.store.job(key)
            if existing and existing['status'] == 'RUNNING':
                self.store.finish(existing, now, status='INTERRUPTED', reason='运行超过尾盘有效窗口，未记作按时预测')
            self.store.miss(key, now, metadata)
            return
        if now < datetime.fromisoformat(f'{day}T{prepare}'):
            return
        scan_key = f'{day}|SCAN|{cutoff}'
        if now < cutoff_at:
            job = self.store.claim(scan_key, now, {**metadata, 'phase': 'DISCOVER'})
            if job:
                try:
                    result = self.scanner.scan(now, context['candidateLimit'])
                    completed = self.clock()
                    if completed >= cutoff_at:
                        self.store.finish(job, completed, status='MISSED', reason='候选扫描完成时已过决策时点')
                    else:
                        self.store.finish(job, completed, status='COMPLETED', snapshotAt=completed.isoformat(), **result)
                except Exception as error:
                    self.store.finish(job, self.clock(), status='FAILED', reason=f'候选扫描失败：{type(error).__name__}: {error}')
            return
        job = self.store.claim(key, now, metadata)
        if not job:
            return
        scan = self.store.job(scan_key)
        if not scan or scan['status'] != 'COMPLETED' or datetime.fromisoformat(scan['snapshotAt']) >= cutoff_at:
            self.store.finish(job, now, status='MISSED', reason='决策前没有有效候选快照；下个窗口自动再扫描')
            return
        requests = [OvernightRequest(instrument_code=row['instrumentCode'], signal_date=day,
            mode='TAIL_ENTRY', cutoff=cutoff, cost_bps=context['tailCostBps']) for row in scan['candidates']]
        with ThreadPoolExecutor(max_workers=3) as pool:
            results = list(pool.map(self._generate, requests))
        incomplete = any(row['status'] in {'FAILED', 'MISSED'} or row.get('evidenceKind') == 'RETROSPECTIVE' for row in results)
        self.store.finish(job, self.clock(), status=('PARTIAL' if incomplete else 'COMPLETED') if requests else 'EMPTY',
                          candidates=scan['candidates'], results=results, snapshotAt=scan['snapshotAt'],
                          ranking=rank_candidates(results))

    def _generate(self, request):
        cutoff = datetime.fromisoformat(f'{request.signal_date}T{request.cutoff}')
        if request.mode == 'TAIL_ENTRY' and self.clock() >= cutoff + timedelta(minutes=5):
            return {'instrumentCode': request.instrument_code, 'status': 'MISSED', 'reason': '队列等待超过尾盘窗口'}
        try:
            # The independent settlement loop rotates old outcomes; do not rescan
            # every archive for every stock in the time-critical acquisition batch.
            report = self.service.generate(request, freeze_all=True, settle_cached=False, cohort='AUTOMATIC')
            primary = next((target.get('joint') for target in report.get('targets', []) if target['target'] == '10:00'), None)
            return {'instrumentCode': request.instrument_code, 'status': report['status'],
                'reportId': report['id'], 'evidenceKind': report['evidenceKind'], 'warnings': report['warnings'],
                'joint': primary}
        except Exception as error:
            return {'instrumentCode': request.instrument_code, 'status': 'FAILED',
                    'reason': f'研究失败：{type(error).__name__}'}

    def _holdings(self, context):
        now = self.clock()
        if now.hour < 15 or (now.hour == 15 and now.minute < 10) or now.hour >= 18:
            return
        received = context.get('receivedAt')
        if not received or not 0 <= (now - datetime.fromisoformat(received)).total_seconds() <= 180:
            return
        processed = 0
        for position in context['positions']:
            # A ledger revision has its own job, while unchanged reports remain immutable.
            identity = {**position, 'costBps': context['holdingCostBps']}
            digest = hashlib.sha256(json.dumps(identity, sort_keys=True).encode()).hexdigest()[:16]
            key = f'{now.date()}|HOLDING|{position["instrumentCode"]}|{digest}'
            job = self.store.claim(key, self.clock(), {'signalDate': str(now.date()), 'cutoff': '15:00',
                'mode': 'AFTER_CLOSE_HOLDING', 'phase': 'PREDICT', 'instrumentCode': position['instrumentCode'],
                'instrumentName': position.get('instrumentName'), 'ledgerReceivedAt': received})
            if not job:
                continue
            try:
                request = OvernightRequest(instrument_code=position['instrumentCode'], signal_date=now.date(),
                    mode='AFTER_CLOSE_HOLDING', cutoff='15:00', cost_bps=context['holdingCostBps'],
                    cost_basis=position['averageCost'], quantity=position['quantity'],
                    position_opened_on=position.get('openedOn'))
                result = self._generate(request)
                self.store.finish(job, self.clock(), status='FAILED' if result['status'] == 'FAILED' else 'COMPLETED',
                                  results=[result])
            except ValueError:
                self.store.finish(job, self.clock(), status='SKIPPED', reason='持仓代码、成本或建仓日期不满足研究条件')
            processed += 1
            if processed >= 6:
                break

    async def run(self, stop):
        while not stop.is_set():
            try:
                await asyncio.to_thread(self.tick)
            except Exception as error:
                logger.exception('Automatic overnight research failed; next tick will retry')
                self.store.put('heartbeat', {'lastTickAt': self.clock().isoformat(), 'error': type(error).__name__})
            try:
                await asyncio.wait_for(stop.wait(), timeout=20)
            except TimeoutError:
                pass


def rank_candidates(results):
    usable = [row for row in results if row.get('evidenceKind') == 'FORWARD'
              and (row.get('joint') or {}).get('status') == 'AVAILABLE']
    active = any(row['joint'].get('adopted') for row in usable)
    selected = [row for row in usable if row['joint']['qualified'] and (not active or row['joint'].get('adopted'))]
    selected.sort(key=lambda row: (-row['joint']['rankScore'], row['instrumentCode']))
    return {'status': 'ACTIVE' if active else 'SHADOW' if usable else 'WAITING_MODEL', 'target': '10:00',
            'opportunityStatus': 'QUALIFIED' if active and selected else 'NO_QUALIFIED' if active else 'RESEARCH_ONLY',
            'evaluatedCount': len(usable), 'candidates': [{**row['joint'], 'instrumentCode': row['instrumentCode'],
                'reportId': row['reportId']} for row in selected[:3]]}
