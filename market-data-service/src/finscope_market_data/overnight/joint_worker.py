"""Off-window shared training; fitting is never performed in a prediction request."""
import asyncio
from datetime import datetime, timedelta
import logging

from finscope_market_data.overnight.automation_models import AutomationContext
from finscope_market_data.overnight.engine import TARGETS
from finscope_market_data.overnight.direction_dataset import PROTOCOL as DIRECTION_PROTOCOL, FEATURES as DIRECTION_FEATURES, build_direction_panel
from finscope_market_data.overnight.direction_learning import fit_direction
from finscope_market_data.overnight.direction_validation import summarize_direction
from finscope_market_data.overnight.context_research import fit_context_direction
from finscope_market_data.overnight.direction_selection import PROTOCOL as CONTEXT_PROTOCOL
from finscope_market_data.overnight.joint_dataset import (
    PROTOCOL, FEATURES, MIN_SYMBOLS, build_panel, profiles,
)
from finscope_market_data.overnight.joint_learning import fit_target
from finscope_market_data.overnight.joint_validation import summarize_forward
from finscope_market_data.overnight.universe import POOL_KEY

logger = logging.getLogger(__name__)


class OvernightJointWorker:
    def __init__(self, automation, research):
        self.automation = automation
        self.research = research
        self.clock = automation.clock

    def tick(self):
        now = self.clock()
        meta = self.automation.store
        context = meta.get('context') or AutomationContext().model_dump(mode='json', by_alias=True)
        if not context['enabled'] or '13:00' <= now.strftime('%H:%M') < '18:00':
            return
        meta.put('jointForward', summarize_forward(self.automation.service.store, now, meta))
        meta.put('closeDirectionForward', summarize_direction(self.automation.service.store, now, meta))
        meta.put('contextDirectionForward', summarize_direction(self.automation.service.store, now, meta, challenger=True))
        for previous in meta.jobs(phase='JOINT'):
            if previous['status'] == 'RUNNING' and now - datetime.fromisoformat(previous['startedAt']) >= timedelta(minutes=10):
                identifier = self.research.models.for_job(previous['key'])
                meta.finish(previous, now, status='COMPLETED' if identifier else 'FAILED', artifactId=identifier,
                            reason='已恢复落库模型' if identifier else '联合训练任务中断，后台有限重试')
        universe = meta.get(POOL_KEY) or {}
        codes = [row['instrumentCode'] for row in universe.get('members', [])]
        coverage = {row['instrumentCode']: row for row in self.automation.service.store.coverage(now)}
        ready = [code for code in codes if coverage.get(code, {}).get('completeDays', 0) >= 140]
        meta.put('jointCoverage', {'updatedAt': now.isoformat(), 'poolSize': len(codes), 'readySymbols': len(ready),
                                  'minimumSymbols': MIN_SYMBOLS, 'targetSize': universe.get('targetSize', 120)})
        if len(ready) < MIN_SYMBOLS:
            return
        for profile in profiles(context):
            key = f'{now.date()}|JOINT|{PROTOCOL}|{DIRECTION_PROTOCOL}|{CONTEXT_PROTOCOL}|{profile.key}'
            job = meta.claim(key, now, {'phase': 'JOINT', 'signalDate': str(now.date()), **profile.dump()})
            if not job:
                continue
            try:
                # Labels must have matured before the job started, even if training is slow.
                panel, pool_context, data_audit = build_panel(self.automation.service.store, codes, profile, now)
                targets = {}
                for target in TARGETS:
                    fitted = fit_target([row for row in panel if row['target'] == target], now.isoformat(), profile.cutoff)
                    if fitted is not None:
                        targets[target] = fitted
                direction_rows, direction_data = build_direction_panel(self.automation.service.store, codes, profile.cutoff, now)
                direction = fit_direction(direction_rows, now.isoformat(), profile.cutoff)
                if len(targets) != len(TARGETS):
                    targets = {}
                if not targets and direction is None:
                    meta.finish(job, self.clock(), status='INSUFFICIENT_DATA', **data_audit,
                                reason='有效训练日期不足；涨跌模型另需选择区，分钟覆盖数量不等于可学习样本')
                    return
                if direction:
                    direction['data'] = direction_data
                    direction['features'] = list(DIRECTION_FEATURES)
                challenger, context_rows = None, []
                try:
                    challenger, context_rows = fit_context_direction(self.automation.service.store, codes, profile.cutoff, now)
                except Exception:
                    logger.exception('Context direction training failed; retaining incumbent models')
                artifact = self.research.models.publish({'protocol': PROTOCOL, 'profile': profile.dump(),
                    'createdAt': self.clock().isoformat(), 'labelsThrough': max(row['exitAt'] for row in panel + direction_rows + context_rows),
                    'universeFingerprint': universe['fingerprint'], 'universeCreatedAt': universe['createdAt'],
                    'features': list(FEATURES), 'data': data_audit, 'context': pool_context, 'targets': targets,
                    'closeDirection': direction, 'contextDirection': challenger, 'trainingJobKey': job['key']},
                    {'tradeRows': panel, 'directionRows': direction_rows, 'contextRows': context_rows}, claim=job)
                meta.finish(job, self.clock(), status='COMPLETED', artifactId=artifact['id'], **data_audit)
            except Exception as error:
                logger.exception('Shared overnight training failed for %s', profile.key)
                meta.finish(job, self.clock(), status='FAILED', reason=f'联合训练失败：{type(error).__name__}；后台有限重试')
            return

    async def run(self, stop):
        while not stop.is_set():
            try:
                await asyncio.to_thread(self.tick)
            except Exception:
                logger.exception('Shared overnight worker tick failed')
            try:
                await asyncio.wait_for(stop.wait(), timeout=60)
            except TimeoutError:
                pass
