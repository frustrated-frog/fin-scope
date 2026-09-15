"""Settle frozen research predictions without fitting or changing any stored probability."""
import hashlib
import json

from finscope_market_data.forecast.features import _validated_bars


def settle_predictions(report, frozen_input, outcome_bars, as_of):
    payload = {key: frozen_input[key] for key in ('bars', 'calendar')}
    digest = hashlib.sha256(json.dumps(payload, sort_keys=True, allow_nan=False).encode()).hexdigest()
    if digest != report['inputFingerprint']:
        raise ValueError('冻结输入指纹不匹配')
    if report['instrumentCode'] != frozen_input['code']:
        raise ValueError('预测标的与冻结输入不匹配')
    bars = _validated_bars(outcome_bars)
    if f'{bars[0].symbol.code}.{bars[0].symbol.market.value}' != report['instrumentCode']:
        raise ValueError('结算行情标的不匹配')
    prices = {bar.trade_date: bar.close for bar in bars if bar.trade_date <= as_of}
    outcomes = []
    for horizon, item in report['horizons'].items():
        current = item['current']
        target, signal = current['targetDate'], current['asOf']
        row = dict(horizon=int(horizon), signalDate=signal, targetDate=target,
                   probability=current['upProbability'], modelCode=current['modelCode'])
        if target is None:
            row['status'] = 'CALENDAR_UNAVAILABLE'
        elif target > as_of:
            row['status'] = 'PENDING'
        elif target not in prices or signal not in prices:
            row['status'] = 'MISSING_OUTCOME_PRICE'
        else:
            actual = prices[target] / prices[signal] - 1
            row.update(status='SETTLED', actualReturn=actual,
                       correct=(current['upProbability'] >= .5) == (actual > 0),
                       brier=(current['upProbability'] - float(actual > 0))**2,
                       absoluteError=abs(current['expectedReturn']-actual),
                       intervalCovered=current['lowerReturn'] <= actual <= current['upperReturn'],
                       outcomeReferenceClose=prices[signal], outcomeTargetClose=prices[target])
        outcomes.append(row)
    return dict(modelVersion=report['modelVersion'], inputFingerprint=digest,
                instrumentCode=report['instrumentCode'], settledAsOf=as_of, outcomes=outcomes,
                priceBasis='QFQ: signal and target prices use the same outcome snapshot adjustment basis')
