"""Freeze paired shadow scores and evaluate only complete actual candidate dates."""
from collections import defaultdict
from datetime import datetime

import numpy as np

from finscope_market_data.forecast.industry_features import IndustryMembership
from finscope_market_data.overnight.context_features import context_features, stock_context
from finscope_market_data.overnight.context_research import MODEL_FEATURES
from finscope_market_data.overnight.direction_dataset import TARGET, direction_features
from finscope_market_data.overnight.multiscale_features import multiscale_features
from finscope_market_data.overnight.tail_ranking import PROTOCOL, predict_pair, ranking_comparison


def attach_tail_ranking(report, grouped, request, artifact, contexts):
    if request.mode != 'TAIL_ENTRY':
        return
    parent = report['closeDirection']
    parent['tailRanking'] = {'protocol': PROTOCOL, 'target': TARGET, 'status': 'WAITING_MODEL',
                             'validated': False, 'reason': '上涨排序与分类对照等待后台训练'}
    fitted = artifact.get('tailRanking')
    decision = report['dataThrough']
    if (not fitted or fitted.get('protocol') != PROTOCOL or fitted.get('target') != TARGET
            or fitted.get('features') != list(MODEL_FEATURES)
            or artifact.get('createdAt', '9999') >= decision or artifact.get('labelsThrough', '9999') >= decision):
        return
    snapshot = contexts.at(request.signal_date, request.cutoff, datetime.fromisoformat(decision))
    if not snapshot or snapshot.get('universeFingerprint') != artifact.get('universeFingerprint'):
        parent['tailRanking'].update(status='MISSING_CONTEXT', reason='判断时点环境不足，保留主预测')
        return
    memberships = [IndustryMembership(v['industry'], v['available_on'], tuple(v['codes'])) for v in snapshot.get('memberships', [])]
    local = direction_features(grouped, request.signal_date, request.cutoff, request.instrument_code)
    history = multiscale_features(grouped, request.signal_date, request.cutoff)
    context = context_features(request.instrument_code, stock_context(grouped, request.signal_date, request.cutoff), snapshot, memberships)
    if local is None or history is None or context is None:
        parent['tailRanking'].update(status='MISSING_FEATURES', reason='排序所需分钟历史或环境特征不足')
        return
    scores = predict_pair(fitted, [local + context + history])
    parent['tailRanking'] = {'protocol': PROTOCOL, 'target': TARGET, 'status': 'SHADOW', 'validated': False,
        'rankScore': float(scores['RANKER'][0]), 'classifierProbability': float(scores['CLASSIFIER'][0]),
        'momentumScore': local[0], 'artifactId': artifact['id'], 'trainedAt': artifact['createdAt'],
        'referenceAt': decision, 'referencePrice': grouped[request.signal_date][request.cutoff].close,
        'reason': '排序分数仅用于候选间比较，不是上涨概率；同输入分类方案同步留档'}


def valid_forecast(forecast, direction, report):
    if not isinstance(forecast, dict):
        return False
    decision = report['dataThrough']
    if (forecast.get('protocol') != PROTOCOL or forecast.get('target') != TARGET
            or forecast.get('status') != 'SHADOW' or not forecast.get('artifactId')
            or forecast.get('trainedAt', '9999') >= decision
            or forecast.get('referenceAt') != decision
            or forecast.get('referencePrice') != direction.get('referencePrice')):
        return False
    for key in ('rankScore', 'classifierProbability', 'momentumScore'):
        value = forecast.get(key)
        if isinstance(value, bool) or not isinstance(value, (float, int)) or not np.isfinite(value):
            return False
    reference = forecast.get('referencePrice')
    return (isinstance(reference, (int, float)) and np.isfinite(reference) and reference > 0
            and 0 <= forecast['classifierProbability'] <= 1)


def summarize_ranking(rows):
    by_day = defaultdict(list)
    for row in rows:
        by_day[row['signalDate']].append(row)
    paired, missing = [], 0
    for values in by_day.values():
        forecasts = [r.get('tailRanking') for r in values]
        if (not all(forecasts) or len({f['artifactId'] for f in forecasts if f}) != 1):
            missing += 1
            continue
        paired.extend({**r, **r['tailRanking']} for r in values)
    return {**ranking_comparison(paired), 'missingPairedDays': missing,
            'scope': 'ACTUAL_COMPLETE_CANDIDATES', 'protocol': PROTOCOL}
