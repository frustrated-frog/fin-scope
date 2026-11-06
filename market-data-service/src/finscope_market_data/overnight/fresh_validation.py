"""Reuse complete actual-cohort validation for the independent freshness arms."""
from finscope_market_data.overnight.fresh_research import GROUPS, PROTOCOL
from finscope_market_data.overnight.market_validation import missing_reason, summarize_market_forward


def invalid_fresh_forecast(report, forecast, outcome, decision, target_at, now, *, protocol=PROTOCOL):
    reason = missing_reason(report, forecast, outcome, decision, target_at, now,
                            protocol=protocol, require_context=False)
    if reason:
        return reason
    if (not forecast.get('featureFingerprint') or not forecast.get('trainingThrough')
            or forecast['trainingThrough'] >= forecast['trainedAt']
            or not forecast.get('calibrationThrough') or forecast['calibrationThrough'] >= forecast['trainedAt']):
        return 'INVALID_EVIDENCE'
    if forecast['group'] == 'INDEX' and (not forecast.get('indexHistoryFingerprint')
                                        or forecast.get('indexHistoryReceivedAt', '9999') >= decision):
        return 'INVALID_EVIDENCE'
    return None


def summarize_fresh_forward(store, meta, now):
    return summarize_market_forward(store, meta, now, protocol=PROTOCOL, groups=GROUPS,
                                    forecast_field='freshModels', validate=invalid_fresh_forecast)
