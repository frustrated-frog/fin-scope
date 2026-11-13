"""Describe missed winners without changing thresholds or frozen predictions."""
from collections import defaultdict
import math

import numpy as np

from finscope_market_data.overnight.fresh_validation import invalid_fresh_forecast
from finscope_market_data.overnight.detection_metrics import detection
from finscope_market_data.overnight.fresh_research import GROUPS
from finscope_market_data.overnight.candidate_validation import valid_candidate


def probability(value):
    return value if isinstance(value, (int, float)) and not isinstance(value, bool) and math.isfinite(value) and 0 <= value <= 1 else None


def evidence(report, direction, snapshot, now, target_at):
    outcome = (report.get('outcome') or {}).get('closeDirection') or {}
    decision = f"{report['signalDate']}T{report['cutoff']}:00"
    alternatives = {}
    for forecast in (report.get('closeDirection') or {}).get('freshModels', []):
        if forecast.get('group') in GROUPS and not invalid_fresh_forecast(
                report, forecast, outcome, decision, target_at, now.isoformat()):
            alternatives[forecast['group']] = forecast['upProbability']
    direction_selection = (report.get('closeDirection') or {}).get('upSelection') or {}
    indices = (snapshot or {}).get('indices', {})
    returns = [row['return'] for row in indices.values() if isinstance(row.get('return'), (int, float)) and math.isfinite(row['return'])]
    return {'rawProbability': probability(direction.get('rawUpProbability')),
        'calibratedProbability': probability(direction.get('calibratedUpProbability')),
        'trainedAt': direction.get('trainedAt'), 'modelFitThrough': direction.get('modelFitThrough'),
        'baselineOnly': direction.get('assessmentStatus') == 'BASELINE_ONLY' or direction.get('selectedModel') == 'PRIOR',
        'indexReturn': float(np.mean(returns)) if len(returns) == 3 else None,
        'alternatives': alternatives,
        'selectionValidated': not valid_candidate(report, direction_selection, outcome, decision, target_at, now.isoformat()),
        'alternativeArtifacts': {f['group']: f.get('artifactId') for f in (report.get('closeDirection') or {}).get('freshModels', []) if f.get('group') in alternatives}}


def summarize_detection(rows):
    daily = defaultdict(list)
    for row in rows:
        daily[row['signalDate']].append(row)
    paired = [row for values in daily.values() if all(
        {'FRESH', 'CALIBRATED', 'INDEX'} <= set(r.get('alternatives', {})) for r in values) for row in values]
    pairs = {}
    for name in GROUPS:
        selected = [row for values in daily.values() if all(name in r.get('alternatives', {}) for r in values)
                    and len({r.get('alternativeArtifacts', {}).get(name) for r in values}) == 1 for row in values]
        pairs[name] = {'pairedDays': len({r['signalDate'] for r in selected}), 'primary': detection(selected),
                       'challenger': detection([{**r, 'probability': r['alternatives'][name]} for r in selected])}
    missed = sorted((r for r in rows if r['actualUp'] and r['probability'] < .5),
                    key=lambda r: (r['signalDate'], r['instrumentCode']), reverse=True)
    fields = ('signalDate', 'instrumentCode', 'probability', 'rawProbability', 'calibratedProbability',
              'trainedAt', 'modelFitThrough', 'marketState', 'indexReturn', 'baselineOnly')
    return {'metrics': detection(rows), 'noUpDays': sum(all(r['probability'] < .5 for r in items) for items in daily.values()),
        'baselineOnlyCount': sum(bool(r.get('baselineOnly')) for r in rows),
        'daily': [{'signalDate': day, **detection(items)} for day, items in sorted(daily.items())],
        'missedExamples': [{key: r.get(key) for key in fields} for r in missed[:20]],
        'comparisonPairs': pairs, 'missedExampleTotal': len(missed), 'pairedDays': len({r['signalDate'] for r in paired}),
        'comparisons': {'PRIMARY': detection(paired), **{name: detection([
            {**r, 'probability': r['alternatives'][name]} for r in paired]) for name in ('FRESH', 'CALIBRATED', 'INDEX')}},
        'scope': '仅完整实际名单日；固定50%方向阈值；描述性诊断不构成模型升级证据'}
