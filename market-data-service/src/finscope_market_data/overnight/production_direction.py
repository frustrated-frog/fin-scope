"""Frozen prospective qualification and reversible activation of direction models."""
from datetime import datetime, timedelta
import hashlib
import json

import numpy as np

from finscope_market_data.forecast.direction_evaluation import evaluate_direction
from finscope_market_data.forecast.trading_calendar import next_session
from finscope_market_data.overnight.candidate_validation import introduced_cohorts, paired_ranks, valid_candidate, valid_cohort
from finscope_market_data.overnight.detection_metrics import detection
from finscope_market_data.overnight.direction_dataset import TARGET
from finscope_market_data.overnight.fresh_research import PROTOCOL as FRESH_PROTOCOL
from finscope_market_data.overnight.fresh_validation import invalid_fresh_forecast
from finscope_market_data.overnight.market_validation import summarize_market_forward
from finscope_market_data.overnight.training_inputs import REVISION as TRAINING_POLICY
from finscope_market_data.overnight.up_selection import PROTOCOL as CANDIDATE_PROTOCOL

PROTOCOL = 'overnight-production-direction-v1'
KEY = 'productionDirection'
REQUIRED_DAYS = 60
RECENT_DAYS = 20
FAMILY_SIZE = 12


def direction_score(rows):
    return evaluate_direction([r['probability'] for r in rows], [r['actualUp'] for r in rows],
        [r['signalDate'] for r in rows], {'INCUMBENT': [r['live'] for r in rows],
                                       'PRIOR': [r['baselineProbability'] for r in rows]}, family_size=FAMILY_SIZE)


def recall_healthy(rows):
    up = detection(rows)
    down = detection([{**r, 'probability': 1-r['probability'] if r['probability'] != .5 else 0.,
                       'actualUp': not r['actualUp']} for r in rows])
    return bool(up and down and (up['upRecall'] or 0) >= .35 and (down['upRecall'] or 0) >= .35)


def rank_healthy(rows, *, confident):
    days = paired_ranks(rows, include_days=True)['days']
    selected = [r for r in days if r['candidateCount']]
    if not days or len(selected) / len(days) < .5:
        return False
    if np.mean([r['matched']['ROLLING'] for r in selected]) <= .5:
        return False
    for kind, values in (('forced', days), ('matched', selected)):
        block = min(5, len(values))
        starts = np.random.default_rng(20261112).integers(0, len(values), size=(2000, int(np.ceil(len(values)/block))))
        indices = ((starts[:, :, None] + np.arange(block)) % len(values)).reshape(2000, -1)[:, :len(values)]
        for baseline in ('INCUMBENT', 'MOMENTUM', 'UNIFORM'):
            delta = np.array([r[kind]['ROLLING']-r[kind][baseline] for r in values])
            bound = np.quantile(delta[indices].mean(axis=1), .05/(FAMILY_SIZE*3*2)) if confident else delta.mean()
            if bound <= 0:
                return False
    return True


def qualify(key, group, meta, now):
    rows, planned = group['rows'], sorted(set(group['plannedDates']))
    complete = sorted({r['signalDate'] for r in rows})
    checkpoint_key = f'{PROTOCOL}|{TRAINING_POLICY}|{"|".join(key)}'
    checkpoint = meta.get(checkpoint_key)
    if checkpoint is None and len(complete) >= REQUIRED_DAYS:
        dates = complete[:REQUIRED_DAYS]
        selected = [r for r in rows if r['signalDate'] in set(dates)]
        coverage = len(dates) / len([d for d in planned if d <= dates[-1]])
        metrics = direction_score(selected)
        passed = metrics['eligible'] and recall_healthy(selected) and coverage >= .9
        if key[2] == 'ROLLING':
            passed = passed and rank_healthy(selected, confident=True)
        checkpoint = meta.put_once(checkpoint_key, {'computedAt': now.isoformat(), 'dates': dates,
            'passed': bool(passed), 'coverage': coverage, 'metrics': metrics,
            'evidenceFingerprint': hashlib.sha256(json.dumps(selected, sort_keys=True, allow_nan=False).encode()).hexdigest()})
    recent_dates = planned[-RECENT_DAYS:]
    recent = [r for r in rows if r['signalDate'] in set(recent_dates)]
    coverage = len({r['signalDate'] for r in recent}) / max(len(recent_dates), 1)
    healthy = bool(len(recent_dates) == RECENT_DAYS and coverage >= .9 and recall_healthy(recent))
    if healthy:
        metrics = direction_score(recent)
        healthy = all(v['accuracyDifference'] > 0 and v['brierDifference'] < 0 for v in metrics['comparisons'].values())
        if key[2] == 'ROLLING':
            healthy = healthy and rank_healthy(recent, confident=False)
    status = 'ACCUMULATING' if checkpoint is None else 'CHECKPOINT_FAILED' if not checkpoint['passed'] else 'QUALIFIED' if healthy else 'MONITORING_DEGRADED'
    last = next_session(datetime.fromisoformat(complete[-1]).date()) if complete else None
    return {'key': '|'.join(key), 'mode': key[0], 'cutoff': key[1], 'group': key[2],
        'status': status, 'eligible': status == 'QUALIFIED', 'dayCount': len(complete), 'requiredDays': REQUIRED_DAYS,
        'checkpointPassed': bool(checkpoint and checkpoint['passed']), 'checkpoint': checkpoint,
        'scheduledCount': group['scheduled'], 'pairedCount': group['paired'], 'missing': dict(group['missing']),
        'recentCoverage': coverage, 'outcomeThrough': f'{last}T15:00:00' if last else None}


def valid_production(report, forecast, outcome, decision, target_at, now):
    validator = valid_candidate if forecast.get('group') == 'ROLLING' else invalid_fresh_forecast
    reason = validator(report, forecast, outcome, decision, target_at, now)
    if reason:
        return reason
    baseline = forecast.get('baselineProbability')
    if (forecast.get('trainingPolicy') != TRAINING_POLICY or isinstance(baseline, bool)
            or not isinstance(baseline, (int, float)) or not np.isfinite(baseline) or not 0 <= baseline <= 1):
        return 'INCOMPATIBLE_TRAINING_POLICY'
    return None


def summarize_production(store, meta, now):
    groups = []
    for name, field, protocol in (('ROLLING', 'rollingSelectionModels', CANDIDATE_PROTOCOL), ('ADAPTIVE', 'freshModels', FRESH_PROTOCOL)):
        starts = introduced_cohorts(store, now.isoformat(), field, protocol, (name,), training_policy=TRAINING_POLICY)
        result = summarize_market_forward(store, meta, now, protocol=protocol, groups=(name,), forecast_field=field,
            validate=valid_production, starts=starts, row_fields=('artifactId', 'baselineProbability') + (('momentumScore',) if name == 'ROLLING' else ()),
            cohort_validate=valid_cohort, summarizer=lambda key, group: qualify(key, group, meta, now))
        groups.extend(result['groups'])
    return {'protocol': PROTOCOL, 'trainingPolicy': TRAINING_POLICY, 'target': TARGET,
            'computedAt': now.isoformat(), 'groups': groups}


def activate_direction(report, monitor):
    parent = report['closeDirection']
    decision = report['dataThrough']
    parent['productionStatus'] = 'BASELINE'
    if ((report.get('jointResearch') or {}).get('cohort') != 'AUTOMATIC' or report.get('evidenceKind') != 'FORWARD'
            or monitor.get('protocol') != PROTOCOL or monitor.get('trainingPolicy') != TRAINING_POLICY
            or monitor.get('target') != TARGET or monitor.get('computedAt', '9999') >= decision):
        return
    try:
        age = datetime.fromisoformat(decision) - datetime.fromisoformat(monitor['computedAt'])
    except (ValueError, TypeError):
        return
    if age > timedelta(days=1):
        parent['productionStatus'] = 'STALE_VALIDATION'
        return
    forecasts = (parent.get('rollingSelectionModels') or []) + (parent.get('freshModels') or [])
    for name in ('ROLLING', 'ADAPTIVE'):
        gate = next((g for g in monitor.get('groups', []) if g['mode'] == report['mode'] and g['cutoff'] == report['cutoff'] and g['group'] == name), {})
        forecast = next((f for f in forecasts if f.get('group') == name), {})
        if not gate.get('eligible') or gate.get('status') != 'QUALIFIED' or (gate.get('outcomeThrough') or '9999') >= decision:
            continue
        if (forecast.get('status') != 'SHADOW' or forecast.get('trainingPolicy') != TRAINING_POLICY
                or forecast.get('assessmentStatus') != 'MODEL_ESTIMATE' or forecast.get('selectedModel') == 'PRIOR'
                or forecast.get('protocol') != (CANDIDATE_PROTOCOL if name == 'ROLLING' else FRESH_PROTOCOL)
                or not forecast.get('modelFitThrough') or forecast['modelFitThrough'] >= forecast.get('trainedAt', '')
                or forecast.get('trainedAt', '9999') >= decision or forecast.get('referenceAt') != decision
                or forecast.get('referencePrice') != parent.get('referencePrice')):
            continue
        p = forecast.get('upProbability')
        if isinstance(p, bool) or not isinstance(p, (int, float)) or not np.isfinite(p) or not 0 <= p <= 1:
            continue
        fallback = {key: value for key, value in parent.items() if key not in ('challenger', 'freshModels', 'upSelection', 'rollingSelectionModels', 'incumbentPrediction', 'stockSelectionModels', 'marketGroups', 'tailRanking')}
        parent.setdefault('incumbentPrediction', fallback)
        parent.update({key: forecast[key] for key in ('upProbability', 'rawUpProbability', 'baselineProbability',
            'selectedModel', 'assessmentStatus', 'modelFitThrough', 'trainedAt', 'artifactId') if key in forecast})
        parent.update(notUpProbability=1-p, direction='UP' if p >= .5 else 'NOT_UP', probabilitySource='RAW',
            calibratedUpProbability=p, calibrationStatus='NOT_APPLIED', status='ACTIVE', activeModelProtocol=forecast['protocol'],
            activeSource=f'{name}_DIRECTION', validated=True, forwardStatus='QUALIFIED', forwardDays=gate['dayCount'],
            productionStatus='ACTIVE', productionGate=gate['key'], fallbackPrediction=fallback,
            reason='新方案通过冻结前瞻检验并持续监测；数据不足或表现退化时回到原判断')
        parent.update(trainingThrough=forecast['modelFitThrough'], initialTrainingThrough=None,
                      historical=forecast.get('historical'))
        return
    parent['productionStatus'] = 'FALLBACK'
