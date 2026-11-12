"""Off-window shared training; fitting is never performed in a prediction request."""
import asyncio
from datetime import datetime, timedelta
import logging

from finscope_market_data.overnight.automation_models import AutomationContext
from finscope_market_data.overnight.engine import TARGETS
from finscope_market_data.overnight.direction_dataset import PROTOCOL as DIRECTION_PROTOCOL, FEATURES as DIRECTION_FEATURES, build_direction_panel
from finscope_market_data.overnight.direction_learning import fit_direction
from finscope_market_data.overnight.context_research import fit_context_direction
from finscope_market_data.overnight.direction_selection import PROTOCOL as CONTEXT_PROTOCOL
from finscope_market_data.overnight.joint_dataset import (
    PROTOCOL, FEATURES, MIN_SYMBOLS, build_panel, profiles,
)
from finscope_market_data.overnight.joint_learning import fit_target
from finscope_market_data.overnight.universe import POOL_KEY
from finscope_market_data.overnight.candidate_cohort import frozen_cohorts
from finscope_market_data.overnight.up_selection import PROTOCOL as UP_PROTOCOL, fit_up_selection
from finscope_market_data.overnight.candidate_readiness import build_actual_candidate_panel

from finscope_market_data.overnight.market_ablation import evaluate_market_groups
from finscope_market_data.overnight.market_research import PROTOCOL as MARKET_PROTOCOL

from finscope_market_data.overnight.tail_ranking import PROTOCOL as RANKING_PROTOCOL, fit_tail_ranking
from finscope_market_data.overnight.fresh_research import PROTOCOL as FRESH_PROTOCOL, train_fresh_research
from finscope_market_data.overnight.index_history import KEY as INDEX_HISTORY_KEY, history_revision
from finscope_market_data.overnight.selection_evaluation import PROTOCOL as SELECTION_PROTOCOL
from finscope_market_data.overnight.relative_strength import PROTOCOL as RELATIVE_PROTOCOL
from finscope_market_data.overnight.fresh_direction import ADAPTIVE_REVISION
from finscope_market_data.overnight.action_verification import ActionVerificationStore
from finscope_market_data.overnight.training_inputs import input_revision, refresh_allowed, GAP_KEY
from finscope_market_data.overnight.training_checkpoint import TrainingCheckpoint
import hashlib
import json

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
        if not context['enabled']:
            return
        if '13:00' <= now.strftime('%H:%M') < '18:00':
            return
        for previous in meta.jobs(phase='JOINT'):
            if previous['status'] == 'RUNNING' and now - datetime.fromisoformat(previous['startedAt']) >= timedelta(minutes=10):
                identifier = self.research.models.for_job(previous['key'])
                saved = self.research.models.checkpoint(identifier) if identifier else None
                complete = identifier and (not saved or saved[0].get('trainingStatus') != 'PARTIAL')
                meta.finish(previous, now, status='COMPLETED' if complete else 'FAILED', artifactId=identifier,
                            reason='已恢复落库模型' if complete else '联合训练未全部完成，后台有限重试')
        universe = meta.get(POOL_KEY) or {}
        codes = [row['instrumentCode'] for row in universe.get('members', [])]
        coverage = {row['instrumentCode']: row for row in self.automation.service.store.coverage(now)}
        ready = [code for code in codes if coverage.get(code, {}).get('completeDays', 0) >= 140]
        meta.put('jointCoverage', {'updatedAt': now.isoformat(), 'poolSize': len(codes), 'readySymbols': len(ready),
                                  'minimumSymbols': MIN_SYMBOLS, 'targetSize': universe.get('targetSize', 120)})
        if len(ready) < MIN_SYMBOLS:
            return
        for profile in profiles(context):
            index_history = meta.get(INDEX_HISTORY_KEY) or {}
            revision = history_revision(index_history)
            data_revision = input_revision(self.automation.service.store, meta, universe, index_history, now.isoformat())
            if not refresh_allowed(meta.jobs(phase='JOINT'), profile, data_revision, now):
                continue
            key = f'{now.date()}|JOINT|{PROTOCOL}|{DIRECTION_PROTOCOL}|{CONTEXT_PROTOCOL}|{UP_PROTOCOL}|{MARKET_PROTOCOL}|{RANKING_PROTOCOL}|{FRESH_PROTOCOL}|{SELECTION_PROTOCOL}|{RELATIVE_PROTOCOL}|{ADAPTIVE_REVISION}|{revision}|{data_revision}|{profile.key}'
            job = meta.claim(key, now, {'phase': 'JOINT', 'signalDate': str(now.date()),
                                       'inputRevision': data_revision, **profile.dump()})
            if not job:
                continue
            try:
                self.train(job, profile, codes, universe, index_history, now)
            except Exception as error:
                logger.exception('Shared overnight training failed for %s', profile.key)
                meta.finish(job, self.clock(), status='FAILED', reason=f'联合训练失败：{type(error).__name__}；后台有限重试')
            return

    def train(self, job, profile, codes, universe, index_history, now):
        meta, models = self.automation.store, self.research.models
        saved = models.checkpoint(models.for_job(job['key']))
        checkpoint = TrainingCheckpoint(saved[1].get('trainingCheckpoint') if saved else None)
        if not checkpoint.data:
            checkpoint.data = self.prepare(codes, profile, now)
        data = checkpoint.data
        through = datetime.fromisoformat(data['through'])
        panel, direction_rows = data['tradeRows'], data['directionRows']
        targets = checkpoint.run('TRADE', lambda: self.fit_targets(panel, through, profile), {})
        direction = checkpoint.run('DIRECTION', lambda: fit_direction(direction_rows, through.isoformat(), profile.cutoff))
        if not targets and direction is None and not checkpoint.failed:
            meta.finish(job, self.clock(), status='INSUFFICIENT_DATA', **data['data'],
                        reason='有效训练日期不足；保留缺口，数据更新后重试')
            return
        if direction:
            direction.update(data=data['directionData'], features=list(DIRECTION_FEATURES))
        challenger, context_rows = checkpoint.run('CONTEXT', lambda: fit_context_direction(
            self.automation.service.store, codes, profile.cutoff, through), (None, []))
        specialist, selection_rows, readiness, cohorts = None, [], None, {}
        if profile.mode == 'TAIL_ENTRY':
            cohorts = frozen_cohorts(meta, through.isoformat(), profile.cutoff)
            selection_rows, readiness = checkpoint.run('CANDIDATE_INPUTS', lambda: build_actual_candidate_panel(direction_rows,
                self.automation.service.store, cohorts, profile.cutoff, through),
                ([], {'status': 'FAILED', 'computedAt': through.isoformat(), 'records': [], 'reason': '候选样本准备失败，后台有限重试'}))
            meta.put(f'{GAP_KEY}|{profile.cutoff}', readiness)
            if 'CANDIDATE_INPUTS' not in checkpoint.failed:
                specialist = checkpoint.run('CANDIDATE', lambda: fit_up_selection(selection_rows,
                    through.isoformat(), profile.cutoff, cohorts, readiness=readiness))
            readiness['status'] = 'FAILED' if any(name.startswith('CANDIDATE') for name in checkpoint.failed) else 'READY' if specialist else 'INSUFFICIENT_TRAINING_HISTORY'
            if readiness['status'] == 'FAILED':
                readiness['reason'] = '候选滚动择模失败，后台有限重试；上次可用模型继续保留'
        ranking = checkpoint.run('RANKING', lambda: fit_tail_ranking(context_rows, through.isoformat(), profile.cutoff)) if context_rows and profile.mode == 'TAIL_ENTRY' else None
        market_models, market_audit = checkpoint.run('MARKET', lambda: self.fit_market(direction_rows, profile, through), ({}, None))
        previous_fresh = checkpoint.components.get('FRESH', {}).get('result')
        fresh = checkpoint.run('FRESH', lambda: train_fresh_research(direction_rows, index_history,
            through.isoformat(), profile.cutoff, previous=previous_fresh))
        partial = bool(checkpoint.failed)
        artifact = models.publish({'protocol': PROTOCOL, 'profile': profile.dump(),
            'createdAt': self.clock().isoformat(), 'labelsThrough': max(row['exitAt'] for row in panel + direction_rows + context_rows + selection_rows),
            'universeFingerprint': universe['fingerprint'], 'universeCreatedAt': universe['createdAt'],
            'features': list(FEATURES), 'data': data['data'], 'context': data['context'], 'targets': targets,
            'closeDirection': direction, 'contextDirection': challenger, 'upSelection': specialist, 'tailRanking': ranking,
            'candidateReadiness': readiness, 'inputRevision': job['inputRevision'],
            'freshResearch': fresh, 'marketModels': market_models, 'trainingJobKey': job['key'],
            'trainingStatus': 'PARTIAL' if partial else 'COMPLETED', 'componentStatus': checkpoint.summary()},
            {'tradeRows': panel, 'directionRows': direction_rows, 'contextRows': context_rows, 'selectionRows': selection_rows,
             'indexHistory': index_history, 'trainingCheckpoint': checkpoint.dump(),
             'selectionCohorts': [{'signalDate': day, 'cutoff': cutoff, **value} for (day, cutoff), value in cohorts.items()]}, claim=job)
        if market_audit is not None:
            meta.put(f'marketAblation:{profile.cutoff}', market_audit)
        meta.finish(job, self.clock(), status='FAILED' if partial else 'COMPLETED', artifactId=artifact['id'],
                    componentStatus=checkpoint.summary(), failedComponents=checkpoint.failed, **data['data'],
                    reason='部分模型失败，继续使用上次完整版本；最多重试三次' if partial else None)

    def prepare(self, codes, profile, now):
        store = self.automation.service.store
        panel, context, audit = build_panel(store, codes, profile, now)
        rows, direction_data = build_direction_panel(store, codes, profile.cutoff, now)
        rows, actions = ActionVerificationStore(store).annotate(rows, now)
        direction_data.update(actionVerification=actions,
            fingerprint=hashlib.sha256(json.dumps(rows, sort_keys=True, allow_nan=False).encode()).hexdigest())
        return {'through': now.isoformat(), 'tradeRows': panel, 'context': context, 'data': audit,
                'directionRows': rows, 'directionData': direction_data}

    @staticmethod
    def fit_targets(panel, through, profile):
        targets = {target: fit_target([row for row in panel if row['target'] == target], through.isoformat(), profile.cutoff)
                   for target in TARGETS}
        return targets if all(targets.values()) else {}

    def fit_market(self, rows, profile, through):
        models = {}
        audit = evaluate_market_groups(rows, self.research.contexts, profile.cutoff, through, models=models)
        return models, audit

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
