"""Prospective rolling-selector evidence, paired with the main and momentum ranks."""
from collections import defaultdict

import numpy as np

from finscope_market_data.overnight.direction_dataset import TARGET
from finscope_market_data.overnight.market_validation import missing_reason, summarize_market_forward
from finscope_market_data.overnight.selection_evaluation import selection_weights
from finscope_market_data.overnight.detection_metrics import detection
from finscope_market_data.overnight.up_selection import PROTOCOL
from finscope_market_data.overnight.candidate_training import PROTOCOL as TRAINING_PROTOCOL, RECIPE, MIN_DAYS, MIN_ROWS

ARMS = ('ROLLING', 'INCUMBENT', 'MOMENTUM', 'UNIFORM')


def introduced_cohorts(store, through, field, protocol, names, *, training_policy=None):
    starts = {(mode, cutoff, name): None for mode, cutoff in
              (('TAIL_ENTRY', '14:30'), ('TAIL_ENTRY', '14:45'), ('AFTER_CLOSE_HOLDING', '15:00')) for name in names}
    for report in sorted(store.iter_history(), key=lambda r: (r['generatedAt'], r['id'])):
        if (report.get('evidenceKind') != 'FORWARD' or report['generatedAt'] >= through
                or (report.get('jointResearch') or {}).get('cohort') != 'AUTOMATIC'):
            continue
        for forecast in (report.get('closeDirection') or {}).get(field) or []:
            key = (report['mode'], report['cutoff'], forecast.get('group'))
            if (key in starts and forecast.get('protocol') == protocol and forecast.get('target') == TARGET
                    and (training_policy is None or forecast.get('trainingPolicy') == training_policy)):
                starts[key] = min(starts[key] or report['signalDate'], report['signalDate'])
    return starts


def paired_ranks(rows, *, include_days=False):
    by_day = defaultdict(list)
    for row in rows:
        by_day[row['signalDate']].append(row)
    days = []
    for day, values in sorted(by_day.items()):
        scores = {'ROLLING': np.array([r['probability'] for r in values]),
                  'INCUMBENT': np.array([r['live'] for r in values]),
                  'MOMENTUM': np.array([r['momentumScore'] for r in values]), 'UNIFORM': np.zeros(len(values))}
        actual = np.array([r['actualUp'] for r in values], dtype=float)
        forced, count = min(3, len(values)), min(3, int(np.sum(scores['ROLLING'] >= .5)))
        days.append({'signalDate': day, 'candidateCount': count,
            'forced': {name: float(selection_weights(p, forced) @ actual / forced) for name, p in scores.items()},
            'matched': {name: float(selection_weights(p, count) @ actual / count) if count else None for name, p in scores.items()}})
    result = {'dayCount': len(days), 'noCandidateDays': sum(not r['candidateCount'] for r in days),
              'topK': 3, 'tiePolicy': 'FRACTIONAL_AT_BOUNDARY', 'weighting': 'EQUAL_DATE'}
    for kind in ('forced', 'matched'):
        selected = [r for r in days if kind == 'forced' or r['candidateCount']]
        result[kind] = {'selectedDays': len(selected), 'precision': {
            name: float(np.mean([r[kind][name] for r in selected])) if selected else None for name in ARMS}}
    return {**result, **({'days': days} if include_days else {})}


def valid_candidate(report, forecast, outcome, decision, target_at, now):
    reason = missing_reason(report, forecast, outcome, decision, target_at, now, protocol=PROTOCOL, require_context=False)
    if reason:
        return reason
    if forecast.get('selectedModel') == RECIPE:
        training = forecast.get('candidateTraining') or {}
        if (training.get('protocol') != TRAINING_PROTOCOL or training.get('status') != 'READY'
                or (training.get('trainingThrough') or '9999') >= forecast['trainedAt']
                or training.get('trainingDays', 0) < MIN_DAYS or training.get('trainingRows', 0) < MIN_ROWS):
            return 'INVALID_CANDIDATE_TRAINING'
    momentum = forecast.get('momentumScore')
    selection = forecast.get('selection') or {}
    if (not forecast.get('featureFingerprint') or forecast.get('modelFitThrough', '9999') >= forecast['trainedAt']
            or not selection.get('reason')
            or (selection.get('selectionThrough') is not None and selection['selectionThrough'] >= forecast['trainedAt'])
            or isinstance(momentum, bool) or not isinstance(momentum, (int, float)) or not np.isfinite(momentum)):
        return 'INVALID_EVIDENCE'
    return None


def valid_cohort(rows):
    return 'MIXED_MODEL_ARTIFACTS' if len({r['artifactId'] for r in rows}) != 1 else None


def summarize(key, group):
    rows = group['rows']
    mode, cutoff, _ = key
    return {'key': '|'.join(key), 'mode': mode, 'cutoff': cutoff, 'status': 'SHADOW', 'eligible': False,
        'scheduledCount': group['scheduled'], 'pairedCount': group['paired'],
        'dayCount': len({r['signalDate'] for r in rows}), 'scheduledDays': group['days'],
        'missing': dict(group['missing']), 'ranking': paired_ranks(rows),
        'direction': {'ROLLING': detection(rows), 'INCUMBENT': detection([{**r, 'probability': r['live']} for r in rows])},
        'scope': '新版首次留档起的完整真实候选日；旧版本不混入，失败候选仍保留分母'}


def summarize_candidate_forward(store, meta, now):
    starts = introduced_cohorts(store, now.isoformat(), 'rollingSelectionModels', PROTOCOL, ('ROLLING',))
    return summarize_market_forward(store, meta, now, protocol=PROTOCOL, groups=('ROLLING',),
        forecast_field='rollingSelectionModels', validate=valid_candidate, row_fields=('momentumScore', 'artifactId'),
        summarizer=summarize, starts=starts, cohort_validate=valid_cohort)
