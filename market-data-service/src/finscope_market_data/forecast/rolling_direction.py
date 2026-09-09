"""Predeclared five-session learning with past, matured out-of-fold calibration."""
from dataclasses import asdict

import numpy as np
from scipy.special import expit, logit

from finscope_market_data.forecast.adaptive_classifiers import date_weights, fit_candidates
from finscope_market_data.forecast.calibration import PlattCalibrator
from finscope_market_data.forecast.direction_calibration import CALIBRATION_MODES, fit_direction_calibration
from finscope_market_data.forecast.direction_evaluation import evaluate_direction

MODEL_CODES = ('BASE_TREE', 'FULL_TREE', 'POOLED_LOGISTIC')
ROLLING_VERSION = 'next-direction-oof-e0-v1'


def calibrated_array(calibrator, probabilities):
    p = np.clip(probabilities, 1e-6, 1 - 1e-6)
    if calibrator.status != 'FITTED':
        return p
    return np.clip(expit(np.clip(calibrator.slope * logit(p) + calibrator.intercept, -30, 30)), 1e-6, 1-1e-6)


def rolling_forecasts(rows, feature_codes, parameters, start_date, *, model_codes=MODEL_CODES,
                      step=5, train_days=505, calibration_days=60, progress=None):
    if step < 1 or train_days < 60 or calibration_days < 1:
        raise ValueError('滚动窗口配置无效')
    rows = tuple(sorted(rows, key=lambda r: (r.sample.signal_date, r.code)))
    days = sorted({r.sample.signal_date for r in rows})
    prediction_days = [day for day in days if day >= start_date]
    output_rows = tuple(r for r in rows if r.sample.signal_date >= start_date)
    if not output_rows:
        raise ValueError('没有滚动预测日期')
    dates = np.array([r.sample.signal_date for r in output_rows])
    exits = np.array([r.sample.exit_date for r in output_rows])
    y = np.array([r.sample.positive for r in output_rows])
    x = np.array([r.sample.features for r in output_rows])
    probabilities = {f'{code}:{mode}': np.full(len(output_rows), np.nan)
                     for code in model_codes for mode in (*CALIBRATION_MODES, 'LEGACY')}
    priors = np.full(len(output_rows), np.nan)
    batches, batch_ids = [], np.zeros(len(output_rows), dtype=int)
    for offset in range(0, len(prediction_days), step):
        chunk = prediction_days[offset:offset+step]
        first = chunk[0]
        training_days = [day for day in days if day < first][-train_days:]
        training_dates = set(training_days)
        training = tuple(r for r in rows if r.sample.signal_date in training_dates and r.sample.exit_date < first)
        if len({r.sample.signal_date for r in training}) < 60:
            raise ValueError('滚动拟合至少需要 60 个成熟训练日期')
        models = fit_candidates(training, feature_codes, parameters, codes=model_codes)
        mask = np.isin(dates, chunk)
        past_days = sorted(set(dates[(dates < first) & (exits < first)]))[-calibration_days:]
        cal_mask = np.isin(dates, past_days) & (exits < first)
        cal_rows = tuple(r for r, keep in zip(output_rows, cal_mask) if keep)
        weights = date_weights(cal_rows) if cal_rows else None
        batch = dict(batchId=len(batches), modelVersion=ROLLING_VERSION, startDate=first, endDate=chunk[-1],
            trainingStart=training[0].sample.signal_date, trainingThrough=max(r.sample.exit_date for r in training),
            trainingSampleCount=len(training), predictionDayCount=len(chunk), modelAgeDaysMax=len(chunk)-1,
            calibrationStart=past_days[0] if past_days else None,
            calibrationThrough=max(exits[cal_mask]) if cal_rows else None,
            calibrationDayCount=len(past_days), calibrationSampleCount=len(cal_rows),
            calibrationSource='PAST_OUT_OF_FOLD_SAME_MODEL_FAMILY', calibrators={})
        priors[mask] = np.average([r.sample.positive for r in training], weights=date_weights(training))
        batch_ids[mask] = batch['batchId']
        for code, model in models.items():
            raw = np.asarray(model.predict_proba(x[mask])[:, 1])
            past = probabilities[f'{code}:RAW'][cal_mask]
            if not np.all(np.isfinite(past)):
                raise ValueError('校准引用了尚未产生的预测')
            for mode in (*CALIBRATION_MODES, 'LEGACY'):
                key = f'{code}:{mode}'
                if not cal_rows:
                    probabilities[key][mask] = raw
                    batch['calibrators'][key] = dict(status='NOT_FITTED', slope=1., intercept=0., reason='尚无成熟 OOF')
                    continue
                calibration = (PlattCalibrator.fit(past, y[cal_mask]) if mode == 'LEGACY'
                               else fit_direction_calibration(past, y[cal_mask], weights, mode))
                probabilities[key][mask] = calibrated_array(calibration, raw)
                batch['calibrators'][key] = asdict(calibration)
        batches.append(batch)
        if progress:
            progress(dict(stage='rolling', batch=len(batches), startDate=first, totalBatches=int(np.ceil(len(prediction_days)/step))))
    return dict(rows=output_rows, probabilities=probabilities, priors=priors, batches=batches, batchIds=batch_ids)


def select_direction_method(result, start, end):
    # The end is exclusive, and crossing labels are purged independently of signal date.
    mask = np.array([start <= r.sample.signal_date < end and r.sample.exit_date < end for r in result['rows']])
    rows = tuple(r for r, keep in zip(result['rows'], mask) if keep)
    dates = np.array([r.sample.signal_date for r in rows])
    y = np.array([r.sample.positive for r in rows])
    if len(set(dates)) < 3:
        raise ValueError('选择区至少需要三个成熟日期')
    prior = result['priors'][mask]
    candidates = {}
    for key, values in result['probabilities'].items():
        if key.endswith(':LEGACY'):
            continue
        p = values[mask]
        audit = evaluate_direction(p, y, dates, {'PRIOR': prior})
        periods = []
        for chunk in np.array_split(sorted(set(dates)), 3):
            period_mask = np.isin(dates, chunk)
            period = evaluate_direction(p[period_mask], y[period_mask], dates[period_mask], {'PRIOR': prior[period_mask]})
            periods.append(dict(startDate=str(chunk[0]), endDate=str(chunk[-1]), accuracy=period['accuracy'],
                                priorAccuracy=period['comparisons']['PRIOR']['accuracy']))
        eligible = bool(audit['brierScore'] <= audit['comparisons']['PRIOR']['brierScore'] + .001
                        and audit['balancedAccuracy'] is not None and audit['balancedAccuracy'] > .5
                        and audit['auc'] is not None and audit['auc'] > .5
                        and sum(p['accuracy'] > p['priorAccuracy'] for p in periods) >= 2)
        candidates[key] = dict(accuracy=audit['accuracy'], balancedAccuracy=audit['balancedAccuracy'],
                               brierScore=audit['brierScore'], auc=audit['auc'], eligible=eligible, periods=periods)
    eligible = [key for key, value in candidates.items() if value['eligible']]
    chosen = (min(eligible, key=lambda key: (-candidates[key]['accuracy'], -candidates[key]['balancedAccuracy'],
                                           candidates[key]['brierScore'], key)) if eligible else 'BASE_TREE:RAW')
    return dict(selected=chosen, passed=bool(eligible), candidates=candidates,
                selectionStart=start, selectionEnd=end, evidenceKind='PRE_TEST_SELECTION',
                rule='Brier 相对先验最多退化 0.001、BA/AUC > 0.5、三个时期至少两个命中优于先验；按命中、BA、Brier 选择')
