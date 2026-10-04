"""A separately validated direction challenger, never a silent replacement."""
from datetime import datetime
import hashlib
import json

from finscope_market_data.forecast.industry_features import IndustryMembership, load_industry_memberships
from finscope_market_data.overnight.context_features import FEATURES, build_context_panel, context_features, stock_context
from finscope_market_data.overnight.context_store import OvernightContextStore
from finscope_market_data.overnight.direction_dataset import FEATURES as STOCK_FEATURES, direction_features
from finscope_market_data.overnight.direction_learning import direction_prediction, fit_direction
from finscope_market_data.overnight.direction_selection import POLICY, PROTOCOL
from finscope_market_data.overnight.action_verification import ActionVerificationStore


def fit_context_direction(store, members, cutoff, through):
    root = store.path.parent
    memberships = load_industry_memberships(root / 'stock-discovery-constituents.json',
                                            root / 'quant' / 'industry-membership-history.json')
    rows, data = build_context_panel(store, members, cutoff, through,
                                    contexts=OvernightContextStore(store), memberships=memberships)
    rows, action_audit = ActionVerificationStore(store).annotate(rows, through)
    data.update(actionVerification=action_audit,
                fingerprint=hashlib.sha256(json.dumps(rows, sort_keys=True, allow_nan=False).encode()).hexdigest())
    fitted = fit_direction(rows, through.isoformat(), cutoff, selection_policy=POLICY)
    if fitted:
        fitted.update(data=data, features=list(STOCK_FEATURES + FEATURES))
    return fitted, rows


def attach_context_direction(report, request, grouped, artifact, contexts, meta):
    parent = report['closeDirection']
    parent['challenger'] = {'protocol': PROTOCOL, 'target': parent['target'], 'status': 'WAITING_MODEL',
                            'reason': '环境增强方案在后台自动训练并单独验证'}
    fitted = artifact.get('contextDirection')
    if not fitted or fitted.get('protocol') != PROTOCOL:
        return
    decision = datetime.fromisoformat(report['dataThrough'])
    snapshot = contexts.at(request.signal_date, request.cutoff, decision)
    x = direction_features(grouped, request.signal_date, request.cutoff, request.instrument_code)
    if not snapshot or snapshot.get('universeFingerprint') != artifact.get('universeFingerprint'):
        parent['challenger'].update(status='MISSING_CONTEXT', reason='预测时点前的环境快照不足；不使用盘后数据补填')
        return
    memberships = [IndustryMembership(item['industry'], item['available_on'], tuple(item['codes']))
                   for item in snapshot.get('memberships', [])]
    own = stock_context(grouped, request.signal_date, request.cutoff)
    contextual = context_features(request.instrument_code, own, snapshot, memberships)
    if x is None or contextual is None:
        parent['challenger'].update(status='MISSING_CONTEXT', reason='环境样本覆盖不足；需要至少 19 只其他股票且覆盖率达到 75%')
        return
    monitor = meta.get('contextDirectionForward') or {}
    key = f'{request.mode}|{request.cutoff}'
    gate = next((g for g in monitor.get('groups', []) if g['key'] == key), {}) if monitor.get('computedAt', '9999') < report['dataThrough'] else {}
    forecast = direction_prediction(fitted, x + contextual, gate.get('calibrationGate'))
    parent['challenger'] = {**forecast, 'artifactId': artifact['id'], 'trainedAt': artifact['createdAt'],
        'dataFingerprint': fitted['data']['fingerprint'], 'incumbentProbability': parent.get('upProbability'),
        'forwardDays': gate.get('dayCount', 0), 'forwardStatus': gate.get('status', 'ACCUMULATING'),
        'validated': bool((report.get('jointResearch') or {}).get('cohort') == 'AUTOMATIC' and gate.get('eligible')),
        'contextAt': snapshot['observedAt'], 'contextReceivedAt': snapshot['receivedAt'],
        'contextFingerprint': snapshot['fingerprint'], 'contextSymbols': len(snapshot['pool']),
        'industryAvailable': bool(contextual[-7]), 'indexCount': len(snapshot.get('indices', {})),
        'reason': '环境增强候选；与现有模型及简单基准进行相同股票、相同日期的前瞻对照'}
    if parent['challenger']['validated'] and parent.get('upProbability') is not None:
        incumbent = {key: value for key, value in parent.items() if key != 'challenger'}
        parent.update({**parent['challenger'], 'incumbentPrediction': incumbent,
                       'status': 'ACTIVE', 'activeSource': 'CONTEXT_DIRECTION',
                       'reason': '环境增强方案已通过真实前瞻对照，自动采用；退化或数据不足时恢复现有方案'})
