"""Paired freshness/index challengers; never replace an incumbent from backtests."""
import hashlib
import json
import logging

import numpy as np

from finscope_market_data.forecast.direction_evaluation import evaluate_direction
from finscope_market_data.overnight.direction_dataset import FEATURES, TARGET, direction_features
from finscope_market_data.overnight.direction_learning import direction_prediction
from finscope_market_data.overnight.fresh_direction import PROTOCOL, MIN_RESEARCH_DAYS, TEST_DAYS, fit_fresh_direction
from finscope_market_data.overnight.index_history import FEATURES as INDEX_FEATURES, index_features, paired_index_rows, history_fingerprint
from finscope_market_data.overnight.selection_research import train_index_only, attach_selection
from finscope_market_data.overnight.training_inputs import REVISION as TRAINING_POLICY

GROUPS = ('FRESH', 'CALIBRATED', 'INDEX', 'ADAPTIVE')
INDEX_CANDIDATE = 'SHAPE_ENSEMBLE'
logger = logging.getLogger(__name__)


def train_fresh_research(rows, history, through, cutoff, *, previous=None):
    result = previous or {'protocol': PROTOCOL, 'target': TARGET, 'status': 'ACCUMULATING', 'models': {}, 'indexAudit': {}}
    if not result['models'].get('FRESH'):
        fitted = fit_fresh_direction(rows, through, cutoff, with_adaptive=True)
        if fitted is None:
            return result
        fitted['features'] = list(FEATURES)
        result['models']['FRESH'] = fitted
        adaptive = fitted.pop('adaptive')
        adaptive['features'] = list(FEATURES)
        result['models']['ADAPTIVE'] = adaptive
    result['status'] = 'SHADOW'
    try:
        return train_index_research(result, rows, history, through, cutoff)
    except Exception:
        logger.exception('Index training failed; retaining fresh and adaptive checkpoints')
        result.update(status='PARTIAL', indexAudit={'status': 'FAILED', 'reason': '指数训练失败，保留基础训练结果并重试'})
        return result


def train_index_research(result, rows, history, through, cutoff):
    base, enhanced = paired_index_rows(rows, history)
    result['indexAudit'] = {'rowCount': len(base), 'dayCount': len({r['signalDate'] for r in base}),
        'featureCount': len(INDEX_FEATURES), 'scope': 'PREVIOUS_COMPLETED_INDEX_DAYS',
        'historyFingerprint': history_fingerprint(history),
        'nonConstantFeatures': int(np.sum(np.ptp([r['features'][-len(INDEX_FEATURES):] for r in enhanced], axis=0) > 0)) if enhanced else 0}
    if result['indexAudit']['dayCount'] < MIN_RESEARCH_DAYS or result['indexAudit']['nonConstantFeatures'] == 0:
        result['indexAudit']['status'] = 'INSUFFICIENT_HISTORY'
        return result
    # PRIOR and SESSION_LOGISTIC ignore appended inputs. Use a predeclared
    # full-feature recipe on BOTH arms so the index comparison measures input.
    matched = fit_fresh_direction(base, through, cutoff, fixed_candidate=INDEX_CANDIDATE, include_checks=True)
    indexed = fit_fresh_direction(enhanced, through, cutoff, selector_rows=base,
                                  fixed_candidate=INDEX_CANDIDATE, include_checks=True)
    if matched is None or indexed is None:
        result['indexAudit']['status'] = 'INSUFFICIENT_HISTORY'
        return result
    # The two arms share exact stocks, dates, labels and a fixed recipe.
    left, right = matched.pop('checks'), indexed.pop('checks')
    test_days = set(sorted({r['signalDate'] for r in right})[-TEST_DAYS:])
    left = [r for r in left if r['signalDate'] in test_days]
    right = [r for r in right if r['signalDate'] in test_days]
    if [(r['signalDate'], r['instrumentCode']) for r in left] != [(r['signalDate'], r['instrumentCode']) for r in right]:
        raise ValueError('指数实验样本未配对')
    comparison = evaluate_direction([r['raw'] for r in right], [r['actualReturn'] > 0 for r in right],
        [r['signalDate'] for r in right], {'MATCHED_BASELINE': [r['raw'] for r in left]})
    indexed['features'], matched['features'] = list(FEATURES + INDEX_FEATURES), list(FEATURES)
    indexed['audit']['historical']['comparisons'].update(comparison['comparisons'])
    result['models'].update(INDEX=indexed, INDEX_BASELINE=matched)
    result['indexAudit']['status'] = 'SHADOW'
    try:
        index_only, selection = train_index_only(enhanced, through, cutoff, left, right)
        result['stockSelection'] = selection
        if index_only:
            result['models']['INDEX_ONLY'] = index_only
    except Exception:
        logger.exception('Index-only experiment failed; preserving existing freshness models')
        result['status'] = 'PARTIAL'
        result['stockSelection'] = {'status': 'FAILED', 'reason': '仅指数对照训练失败，保留已有模型'}
    return result


def attach_fresh_research(report, request, grouped, artifact, history):
    parent, decision = report['closeDirection'], report['dataThrough']
    parent['freshModels'] = []
    research = artifact.get('freshResearch') or {}
    local = direction_features(grouped, request.signal_date, request.cutoff, request.instrument_code)
    timely = artifact.get('createdAt', '9999') < decision and artifact.get('labelsThrough', '9999') < decision
    models = research.get('models') or {}
    try:
        index = index_features(history, request.signal_date)
    except (TypeError, ValueError):
        logger.exception('Index inputs invalid; retaining independent base forecasts')
        index = None
    received = max((r.get('receivedAt', '9999') for r in history.values()), default='9999')
    for name in GROUPS:
        value = {'group': name, 'protocol': PROTOCOL, 'target': TARGET, 'validated': False,
                 'trainingPolicy': TRAINING_POLICY,
                 'status': 'WAITING_MODEL', 'reason': '等待新训练方案；原判断继续保留'}
        parent['freshModels'].append(value)
        model = models.get(name if name in ('INDEX', 'ADAPTIVE') else 'FRESH')
        expected = FEATURES + INDEX_FEATURES if name == 'INDEX' else FEATURES
        if (not timely or research.get('protocol') != PROTOCOL or not model
                or model.get('features') != list(expected)):
            continue
        if local is None or (name == 'INDEX' and (index is None or received >= decision)):
            value.update(status='MISSING_FEATURES', reason='判断前的完整输入不足，保留缺口')
            continue
        baseline = (models.get('INDEX_BASELINE') if name == 'INDEX' else models.get('FRESH')
                    if name == 'ADAPTIVE' else artifact.get('closeDirection'))
        if name == 'INDEX' and (not baseline or baseline.get('features') != list(FEATURES)):
            continue
        if name in ('FRESH', 'ADAPTIVE') and not baseline:
            continue
        x = local + index if name == 'INDEX' else local
        forecast = direction_prediction(model, x, {})
        raw = forecast['upProbability']
        matched = raw if name == 'CALIBRATED' else direction_prediction(baseline, local, {})['rawUpProbability']
        if matched is None or parent.get('upProbability') is None:
            continue
        probability = forecast['calibratedUpProbability'] if name == 'CALIBRATED' else raw
        audit = model['audit']
        value.update(status='SHADOW', upProbability=probability, rawUpProbability=raw,
            trainingPolicy=artifact.get('trainingPolicy'), baselineProbability=forecast['baselineProbability'],
            historical=forecast['historical'],
            assessmentStatus=forecast['assessmentStatus'], modelFitThrough=forecast['modelFitThrough'],
            matchedBaselineProbability=matched, liveProbability=parent['upProbability'],
            selectedModel=forecast['selectedModel'], calibrationStatus=forecast['calibrationStatus'],
            artifactId=artifact['id'], trainedAt=artifact['createdAt'], referenceAt=decision,
            referencePrice=grouped[request.signal_date][request.cutoff].close,
            featureFingerprint=hashlib.sha256(json.dumps(x, allow_nan=False).encode()).hexdigest(),
            trainingThrough=audit['trainingThrough'], legacyTrainingThrough=audit['legacyTrainingThrough'],
            calibrationThrough=audit['calibrationThrough'],
            selection=audit.get('selection') if name == 'ADAPTIVE' else None,
            indexHistoryReceivedAt=received if name == 'INDEX' else None,
            indexHistoryFingerprint=history_fingerprint(history) if name == 'INDEX' else None,
            reason='新训练方案独立留档，尚未证明优于当前预测')
    try:
        attach_selection(parent, research, index)
    except Exception:
        logger.exception('Selection attachment failed; preserving existing direction forecasts')
        parent['stockSelectionModels'] = []
