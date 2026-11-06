"""Same original automatic cohorts as direction validation, with equal selection counts."""
import math

from finscope_market_data.overnight.fresh_validation import invalid_fresh_forecast
from finscope_market_data.overnight.market_validation import summarize_market_forward, summarize
from finscope_market_data.overnight.selection_evaluation import PROTOCOL, paired_selection


def invalid_selection(report, forecast, outcome, decision, target_at, now):
    reason = invalid_fresh_forecast(report, forecast, outcome, decision, target_at, now, protocol=PROTOCOL)
    if reason:
        return reason
    p = forecast.get('indexOnlyProbability')
    if isinstance(p, bool) or not isinstance(p, (int, float)) or not math.isfinite(p) or not 0 <= p <= 1:
        return 'MISSING_INDEX_ONLY'
    if (not forecast.get('indexOnlyTrainingThrough')
            or forecast['indexOnlyTrainingThrough'] >= forecast['trainedAt']):
        return 'INVALID_EVIDENCE'
    return None


def selection_summary(key, group):
    result = summarize(key, group)
    rows = [{'signalDate': r['signalDate'], 'instrumentCode': r['instrumentCode'], 'actualUp': r['actualUp'],
             'scores': {'INDEX': r['probability'], 'STOCK_ONLY': r['baseline'], 'INDEX_ONLY': r['indexOnlyProbability']}}
            for r in group['rows']]
    result['sameDaySelection'] = paired_selection(rows)
    return result


def invalid_cohort(rows):
    if not rows:
        return 'MISSING_PREDICTION'
    probabilities = [r['indexOnlyProbability'] for r in rows]
    if (max(probabilities) - min(probabilities) > 1e-12
            or any(len({r[key] for r in rows}) != 1 for key in ('artifactId', 'indexHistoryFingerprint'))):
        return 'INCONSISTENT_PAIRED_MODEL'
    return None


def summarize_selection_forward(store, meta, now):
    return summarize_market_forward(store, meta, now, protocol=PROTOCOL, groups=('INDEX',),
        forecast_field='stockSelectionModels', validate=invalid_selection,
        row_fields=('indexOnlyProbability', 'artifactId', 'indexHistoryFingerprint'),
        summarizer=selection_summary, cohort_validate=invalid_cohort)
