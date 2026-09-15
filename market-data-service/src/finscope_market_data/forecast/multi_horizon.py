"""One- and five-session close-return forecasts with paired rolling audits and frozen predictions."""
from __future__ import annotations

from datetime import date, timedelta
import hashlib
import json

import numpy as np

from finscope_market_data.forecast.direction_evaluation import evaluate_direction
from finscope_market_data.forecast.features import ForecastSample, _features, _validated_bars
from finscope_market_data.forecast.local_prediction import fit_local, MODEL_VERSION
from finscope_market_data.forecast.next_session import _fit_at_legacy
from finscope_market_data.forecast.trading_calendar import next_session


def horizon_samples(bars, calendar, horizon):
    if horizon not in (1, 5):
        raise ValueError('只支持 1 和 5 个交易日预测')
    ordered = _validated_bars(bars)
    if list(calendar) != sorted(set(calendar)):
        raise ValueError('交易日历必须严格递增且唯一')
    by_day = {bar.trade_date: bar for bar in ordered}
    positions = {day: index for index, day in enumerate(calendar)}
    result = []
    for index in range(60, len(ordered)):
        bar = ordered[index]
        slot = positions.get(bar.trade_date)
        if slot is None:
            raise ValueError('研究行情日期不在参考日历中')
        if slot + horizon >= len(calendar):
            continue
        exit_date = calendar[slot+horizon]
        if exit_date not in by_day:
            continue
        result.append(ForecastSample(bar.trade_date, bar.trade_date, exit_date,
            _features(ordered, index, None), by_day[exit_date].close/bar.close-1))
    return result


def _audit(observations, horizon):
    p = [row['probability'] for row in observations]
    y = [row['actualReturn'] > 0 for row in observations]
    days = [row['signalDate'] for row in observations]
    audit = evaluate_direction(p, y, days, {
        'PRIOR': [row['prior'] for row in observations],
        'MOMENTUM': [row['momentumProbability'] for row in observations],
        'LEGACY': [row['legacyProbability'] for row in observations],
    })
    audit['task'] = f'CLOSE_RETURN_{horizon}D'
    audit['returnMae'] = float(np.mean([abs(row['expectedReturn']-row['actualReturn']) for row in observations]))
    audit['priorReturnMae'] = float(np.mean([abs(row['priorReturn']-row['actualReturn']) for row in observations]))
    audit['intervalCoverage'] = float(np.mean([row['lowerReturn'] <= row['actualReturn'] <= row['upperReturn'] for row in observations]))
    return audit


def run_multi_horizon(bars, calendar, *, instrument_code, test_days=60):
    if test_days < 60 or test_days > 180:
        raise ValueError('滚动测试范围必须为 60 至 180 个交易日')
    ordered = _validated_bars(bars)
    if instrument_code != f'{ordered[0].symbol.code}.{ordered[0].symbol.market.value}':
        raise ValueError('预测代码与行情不一致')
    panels = {horizon: horizon_samples(ordered, calendar, horizon) for horizon in (1, 5)}
    common_dates = sorted(set(row.signal_date for row in panels[1]) & set(row.signal_date for row in panels[5]))
    if len(common_dates) < test_days + 230:
        raise ValueError('多周期滚动验证历史不足')
    tested_dates = common_dates[-test_days:]
    reports = {}
    for horizon, samples in panels.items():
        by_day = {row.signal_date: row for row in samples}
        observations, fits = [], []
        fitted = legacy = None
        for index, day in enumerate(tested_dates):
            sample = by_day[day]
            if index % 20 == 0:
                fitted = fit_local(samples, day)
                legacy = _fit_at_legacy(samples, day)
                prior_return = float(np.mean([row.net_return for row in samples
                    if row.exit_date <= fitted.training_through][-fitted.training_count:]))
                fits.append(dict(signalDate=day, trainingThrough=fitted.training_through,
                                 calibrationThrough=fitted.calibration_through, **fitted.audit))
            p, expected, lower, upper = fitted.predict(sample.features)
            legacy_p = legacy.predict(sample.features)[0]
            observations.append(dict(signalDate=day, targetDate=sample.exit_date, probability=p,
                expectedReturn=expected, lowerReturn=lower, upperReturn=upper, actualReturn=sample.net_return,
                prior=fitted.baseline, priorReturn=prior_return, legacyProbability=legacy_p,
                momentumProbability=.55 if sample.features[0] > 0 else .45, modelCode=fitted.code))
        as_of = date.fromisoformat(ordered[-1].trade_date)
        current_fit = fit_local(samples, (as_of+timedelta(days=1)).isoformat())
        p, expected, lower, upper = current_fit.predict(_features(ordered, len(ordered)-1, None))
        target = as_of
        for _ in range(horizon):
            target = next_session(target) if target is not None else None
        audit = _audit(observations, horizon)
        segments = [_audit(observations[start:start+20], horizon) for start in range(0, len(observations), 20)]
        reports[str(horizon)] = dict(label=f'CLOSE_RETURN_{horizon}D', horizon=horizon,
            current=dict(asOf=as_of.isoformat(), targetDate=target.isoformat() if target else None,
                         upProbability=p, expectedReturn=expected, lowerReturn=lower, upperReturn=upper,
                         status='WATCH' if not audit['eligible'] else 'RESEARCH_ELIGIBLE',
                         modelCode=current_fit.code, trainingThrough=current_fit.training_through,
                         calibrationThrough=current_fit.calibration_through, selection=current_fit.audit),
            evaluation=audit, segments=segments, refits=fits, observations=observations)
    payload = dict(bars=[bar.model_dump(mode='json') for bar in ordered], calendar=list(calendar))
    digest = hashlib.sha256(json.dumps(payload, sort_keys=True, allow_nan=False).encode()).hexdigest()
    return dict(modelVersion=MODEL_VERSION, instrumentCode=instrument_code, inputFingerprint=digest,
                testStart=tested_dates[0], testEnd=tested_dates[-1], horizons=reports,
                limitations=['收盘到收盘价格预测，不是账户净收益', '历史区间已经用于研发，属于回顾验证',
                             '同一股票五日标签存在重叠，按日期分块评价不确定性',
                             '当前预测为数据截止时点的研究输出，不保证生成时仍可用于下一交易日',
                             '本次单股量价对照不评估跨股票排序，也不证明全市场效果'])
