"""Direct binary rolling forecasts for every observable row, then attach mature labels."""
from dataclasses import asdict

import numpy as np
from lightgbm import LGBMClassifier

from finscope_market_data.forecast.adaptive_classifiers import date_weights
from finscope_market_data.forecast.calibration_gate import select_calibration_outputs
from finscope_market_data.forecast.direction_calibration import fit_direction_calibration
from finscope_market_data.forecast.joint_training import PARAMETERS
from finscope_market_data.forecast.rolling_direction import calibrated_array


PANEL_DIRECTION_VERSION = 'observable-direct-v1'


def rolling_panel_direction(dataset, features, start, *, parameters=None, step=5, train_days=505,
                            calibration_days=60, progress=None):
    observable = dataset.observable_rows
    keys = [(r.code, r.signal_date) for r in observable]
    if not keys or len(set(keys)) != len(keys) or keys != sorted(keys, key=lambda key: (key[1],key[0])):
        raise ValueError('完整可观测截面必须非空、唯一并按日期股票排序')
    x = np.asarray(features, dtype=float)
    if x.ndim != 2 or len(x) != len(keys) or np.any(np.isinf(x)):
        raise ValueError('直接分类特征矩阵未对齐')
    if step < 1 or train_days < 60 or calibration_days < 1:
        raise ValueError('滚动窗口配置无效')
    labels = {(r.code,r.sample.signal_date): r for r in dataset.rows}
    if len(labels) != len(dataset.rows) or not labels.keys() <= set(keys):
        raise ValueError('标签必须唯一且属于可观测截面')
    for row in dataset.rows:
        if row.sample.exit_date > dataset.as_of or row.sample.exit_date <= row.sample.signal_date:
            raise ValueError('标签时间与截止日不一致')
    dates = np.array([r.signal_date for r in observable])
    y = np.array([int(labels[key].sample.positive) if key in labels else -1 for key in keys])
    exits = np.array([labels[key].sample.exit_date if key in labels else '' for key in keys])
    target = dates >= start
    target_indices = np.flatnonzero(target)
    target_dates, target_y, target_exits = dates[target], y[target], exits[target]
    target_keys = [keys[i] for i in target_indices]
    days = sorted(set(target_dates))
    if not days:
        raise ValueError('没有可观测预测日期')
    output = {mode: np.full(len(target_keys), np.nan) for mode in ('RAW','INTERCEPT','GATED_DIRECTION','GATED_PROBABILITY')}
    batches, batch_ids = [], np.full(len(target_keys), -1, dtype=int)
    for offset in range(0,len(days),step):
        chunk = days[offset:offset+step]
        first = chunk[0]
        mature = (y >= 0) & (exits < first) & (dates < first)
        training_days = sorted(set(dates[mature]))[-train_days:]
        train = mature & np.isin(dates,training_days)
        if len(training_days) < 60 or len(set(y[train])) < 2:
            raise ValueError('直接分类需要至少 60 个成熟日期及两个方向类别')
        train_rows = tuple(labels[keys[i]] for i in np.flatnonzero(train))
        model = LGBMClassifier(**(PARAMETERS if parameters is None else parameters)).fit(x[train],y[train],
            sample_weight=date_weights(train_rows))
        mask = np.isin(target_dates,chunk)
        raw = np.clip(model.booster_.predict(x[target_indices[mask]]), 1e-6, 1-1e-6)
        output['RAW'][mask] = raw
        past = (target_y >= 0) & (target_exits < first) & (target_dates < first)
        cal_days = sorted(set(target_dates[past]))[-calibration_days:]
        cal = past & np.isin(target_dates,cal_days)
        output['INTERCEPT'][mask] = raw
        calibration = dict(status='WARMUP_RAW', slope=1., intercept=0.)
        if np.any(cal):
            if not np.all(np.isfinite(output['RAW'][cal])):
                raise ValueError('校准概率不是先前生成的 OOF')
            cal_rows = tuple(labels[target_keys[i]] for i in np.flatnonzero(cal))
            fitted = fit_direction_calibration(output['RAW'][cal],target_y[cal],date_weights(cal_rows),'INTERCEPT')
            output['INTERCEPT'][mask] = calibrated_array(fitted,raw)
            calibration = asdict(fitted)
        gate = select_calibration_outputs(output['RAW'][past], output['INTERCEPT'][past],
                                          target_y[past], target_dates[past])
        output['GATED_DIRECTION'][mask] = output[gate['directionSource']][mask]
        output['GATED_PROBABILITY'][mask] = output[gate['probabilitySource']][mask]
        gate['maturityThrough'] = str(max(target_exits[past])) if np.any(past) else None
        batch_ids[mask] = len(batches)
        batch = dict(batchId=len(batches),startDate=first,endDate=chunk[-1],
            trainingStart=training_days[0],trainingThrough=str(max(exits[train])),
            trainingDayCount=len(training_days),trainingSampleCount=int(train.sum()),
            predictionCount=int(mask.sum()),calibrationDayCount=len(cal_days),
            calibrationThrough=str(max(target_exits[cal])) if np.any(cal) else None,calibration=calibration,calibrationGate=gate)
        batches.append(batch)
        if progress:
            progress(dict(stage='direct',batch=len(batches),totalBatches=int(np.ceil(len(days)/step)),startDate=first))
    return dict(keys=target_keys,dates=target_dates,labels=target_y,exits=target_exits,
        probabilities=output,batches=batches,batchIds=batch_ids)
