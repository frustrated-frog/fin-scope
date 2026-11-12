"""Describe missed winners without changing thresholds or frozen predictions."""
from collections import defaultdict
import math

import numpy as np

from finscope_market_data.overnight.fresh_validation import invalid_fresh_forecast
from finscope_market_data.overnight.joint_dataset import date_weights


def probability(value):
    return value if isinstance(value, (int, float)) and not isinstance(value, bool) and math.isfinite(value) and 0 <= value <= 1 else None


def evidence(report, direction, snapshot, now):
    outcome = (report.get('outcome') or {}).get('closeDirection') or {}
    decision = f"{report['signalDate']}T{report['cutoff']}:00"
    alternatives = {}
    for forecast in (report.get('closeDirection') or {}).get('freshModels', []):
        if forecast.get('group') in ('FRESH', 'CALIBRATED', 'INDEX') and not invalid_fresh_forecast(
                report, forecast, outcome, decision, outcome.get('exitAt', ''), now.isoformat()):
            alternatives[forecast['group']] = forecast['upProbability']
    indices = (snapshot or {}).get('indices', {})
    returns = [row['return'] for row in indices.values() if isinstance(row.get('return'), (int, float)) and math.isfinite(row['return'])]
    return {'rawProbability': probability(direction.get('rawUpProbability')),
        'calibratedProbability': probability(direction.get('calibratedUpProbability')),
        'trainedAt': direction.get('trainedAt'), 'trainingThrough': direction.get('trainingThrough'),
        'indexReturn': float(np.mean(returns)) if len(returns) == 3 else None,
        'alternatives': alternatives}


def detection(rows):
    if not rows:
        return None
    weights = date_weights(rows)
    p = np.array([r['probability'] for r in rows]) >= .5
    y = np.array([r['actualUp'] for r in rows], dtype=bool)
    hit = p & y
    return {'sampleCount': len(rows), 'dayCount': len({r['signalDate'] for r in rows}),
        'predictedUpCount': int(p.sum()), 'actualUpCount': int(y.sum()), 'trueUpCount': int(hit.sum()),
        'missedUpCount': int((~p & y).sum()), 'falseUpCount': int((p & ~y).sum()),
        'upPrecision': float(weights[hit].sum() / weights[p].sum()) if p.any() else None,
        'upRecall': float(weights[hit].sum() / weights[y].sum()) if y.any() else None,
        'predictedUpRate': float(np.average(p, weights=weights)),
        'accuracy': float(np.average(p == y, weights=weights)),
        'alwaysNotUpAccuracy': float(np.average(~y, weights=weights)),
        'weighting': 'EQUAL_DATE_THEN_STOCK'}


def summarize_detection(rows):
    daily = defaultdict(list)
    for row in rows:
        daily[row['signalDate']].append(row)
    paired = [row for values in daily.values() if all(
        set(r.get('alternatives', {})) == {'FRESH', 'CALIBRATED', 'INDEX'} for r in values) for row in values]
    missed = sorted((r for r in rows if r['actualUp'] and r['probability'] < .5),
                    key=lambda r: (r['signalDate'], r['instrumentCode']), reverse=True)
    fields = ('signalDate', 'instrumentCode', 'probability', 'rawProbability', 'calibratedProbability',
              'trainedAt', 'trainingThrough', 'marketState', 'indexReturn')
    return {'metrics': detection(rows), 'noUpDays': sum(all(r['probability'] < .5 for r in items) for items in daily.values()),
        'daily': [{'signalDate': day, **detection(items)} for day, items in sorted(daily.items())],
        'missedExamples': [{key: r.get(key) for key in fields} for r in missed[:20]],
        'missedExampleTotal': len(missed), 'pairedDays': len({r['signalDate'] for r in paired}),
        'comparisons': {'PRIMARY': detection(paired), **{name: detection([
            {**r, 'probability': r['alternatives'][name]} for r in paired]) for name in ('FRESH', 'CALIBRATED', 'INDEX')}},
        'scope': '仅完整实际名单日；固定50%方向阈值；描述性诊断不构成模型升级证据'}
