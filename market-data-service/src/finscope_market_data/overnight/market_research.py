"""Frozen market-group models run alongside, never overwrite, the live forecast."""
from datetime import datetime

from finscope_market_data.overnight.direction_dataset import FEATURES, TARGET, direction_features
from finscope_market_data.overnight.direction_learning import direction_prediction
from finscope_market_data.overnight.market_context import GROUPS, market_groups

PROTOCOL = 'overnight-market-direction-v1'


def attach_market_direction(report, request, grouped, artifact, contexts):
    parent = report['closeDirection']
    parent['marketGroups'] = []
    decision = report['dataThrough']
    # Check again here so replay/direct callers cannot attach a future model.
    timely = (artifact.get('createdAt', '9999') < decision
              and artifact.get('labelsThrough', '9999') < decision)
    snapshot = contexts.at(request.signal_date, request.cutoff, datetime.fromisoformat(decision))
    local = direction_features(grouped, request.signal_date, request.cutoff, request.instrument_code)
    values = market_groups(request.instrument_code, snapshot) if snapshot else {}
    for group, feature_names in GROUPS.items():
        result = {'group': group, 'protocol': PROTOCOL, 'target': TARGET, 'status': 'WAITING_MODEL',
                  'validated': False, 'reason': '等待真实时点数据积累及后台训练'}
        parent['marketGroups'].append(result)
        fitted = (artifact.get('marketModels') or {}).get(group) or {}
        if (not timely or fitted.get('protocol') != PROTOCOL or fitted.get('target') != TARGET
                or fitted.get('baseFeatures') != list(FEATURES)
                or fitted.get('features') != list(FEATURES + feature_names)):
            continue
        if local is None or group not in values:
            result.update(status='MISSING_FEATURES', reason='判断时点的市场或行业数据不足；未使用盘后数据补填')
            continue
        base = direction_prediction(fitted['baseline'], local, {})
        enhanced = direction_prediction(fitted['enhanced'], local + values[group], {})
        result.update(status='SHADOW', upProbability=enhanced['upProbability'],
                      matchedBaselineProbability=base['upProbability'], liveProbability=parent.get('upProbability'),
                      selectedModel=enhanced['selectedModel'], artifactId=artifact['id'],
                      trainedAt=artifact['createdAt'], dataFingerprint=fitted['dataFingerprint'],
                      contextFingerprint=snapshot['fingerprint'], contextReceivedAt=snapshot['receivedAt'],
                      referenceAt=decision, referencePrice=grouped[request.signal_date][request.cutoff].close,
                      reason='与相同训练样本的基准及当时主预测并行留档；尚未验证有效')
