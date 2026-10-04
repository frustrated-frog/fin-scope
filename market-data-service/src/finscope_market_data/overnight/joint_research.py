"""Attach frozen challengers and adopt only a qualified past-only forward protocol."""
from datetime import datetime

from finscope_market_data.overnight.engine import group_bars
from finscope_market_data.overnight.joint_dataset import JointProfile, PROTOCOL, current_features
from finscope_market_data.overnight.joint_learning import prediction
from finscope_market_data.overnight.joint_store import OvernightJointStore
from finscope_market_data.overnight.universe import POOL_KEY


class OvernightJointResearch:
    def __init__(self, store, meta):
        self.models = OvernightJointStore(store)
        self.meta = meta

    def attach(self, report, request, bars):
        cutoff = datetime.fromisoformat(report['dataThrough'])
        profile = JointProfile(request.mode, request.cutoff, request.cost_bps)
        artifact = self.models.latest(profile.key, cutoff)
        report['jointResearch'] = {'protocol': PROTOCOL, 'status': 'WAITING_MODEL',
                                   'reason': '公共样本库积累中；后台自动训练后开始对照'}
        if not artifact or artifact['protocol'] != PROTOCOL:
            return
        research = report['jointResearch']
        research.update({'artifactId': artifact['id'], 'trainedAt': artifact['createdAt'],
                         'dataFingerprint': artifact['data']['fingerprint'],
                         'symbolCount': artifact['data']['symbolCount'], 'status': 'MISSING_CONTEXT',
                         'reason': '决策前分钟或前一交易日公共样本环境不足'})
        grouped = group_bars([bar for bar in bars if bar.ended_at <= cutoff])
        x = current_features(grouped, request.signal_date, request.cutoff, request.instrument_code, artifact['context'])
        if x is None or not report['targets']:
            return
        monitor = self.meta.get('jointForward') or {}
        # A stored monitoring decision cannot travel backwards into historical replay.
        gates = {row['key']: row for row in monitor.get('groups', [])} if monitor.get('computedAt', '9999') < report['dataThrough'] else {}
        research.update({'status': 'SHADOW', 'reason': '联合模型与现有判断并行留档，等待真实前瞻验证',
                         'contextDate': str(max(day for day in artifact['context'] if day < str(request.signal_date)))})
        for target in report['targets']:
            fitted = artifact['targets'].get(target['target'])
            if not fitted:
                continue
            forecast = prediction(fitted['model'], x)
            gate = gates.get(f"{profile.key}|{target['target']}", {})
            adopted = bool(gate.get('eligible') and target['status'] == 'WATCH'
                           and forecast['calibrationStatus'] == 'FITTED')
            target['joint'] = {**forecast, 'status': 'AVAILABLE', 'adopted': adopted,
                'artifactId': artifact['id'], 'protocol': PROTOCOL, 'forwardStatus': gate.get('status', 'ACCUMULATING'),
                'forwardDays': gate.get('dayCount', 0), 'incumbentProbability': target.get('upProbability'),
                'incumbentExpectedNetReturn': target.get('expectedNetReturn')}
            if adopted:
                target.update({key: forecast[key] for key in ('upProbability', 'expectedNetReturn', 'lowerNetReturn', 'upperNetReturn')})
                target['probabilitySource'] = 'JOINT_MODEL'
                target['selectionReason'] = 'FORWARD_QUALIFIED'
                target['costBasisReturn'] = (report['referencePrice'] * (1 + forecast['expectedNetReturn'])
                    / request.cost_basis - 1) if request.cost_basis else None
                research.update({'status': 'ACTIVE', 'reason': '已通过固定前瞻对照；持续监控退化'})

    def status(self, now):
        universe = self.meta.get(POOL_KEY) or {}
        coverage = self.meta.get('jointCoverage') or {}
        return {'protocol': PROTOCOL, 'poolSize': len(universe.get('members', [])),
                'targetSize': universe.get('targetSize', 120), 'readySymbols': coverage.get('readySymbols', 0),
                'minimumSymbols': coverage.get('minimumSymbols', 20), 'coverageAt': coverage.get('updatedAt'),
                'scope': universe.get('scope'), 'limitation': universe.get('limitation'),
                'models': self.models.summaries(now), 'forward': self.meta.get('jointForward'),
                'jobs': [{k: v for k, v in row.items() if k != 'token'} for row in self.meta.jobs(limit=6, phase='JOINT')]}
