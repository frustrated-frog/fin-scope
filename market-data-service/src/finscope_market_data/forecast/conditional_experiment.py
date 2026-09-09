"""Chronological market/ranking OOF generation and conditional direction experiments."""
from collections import defaultdict

import numpy as np
from lightgbm import LGBMRanker
from sklearn.linear_model import Ridge
from sklearn.pipeline import make_pipeline
from sklearn.preprocessing import StandardScaler

from finscope_market_data.forecast.adaptive_classifiers import date_weights
from finscope_market_data.forecast.conditional_direction import (
    CONDITIONAL_MODES, MARKET_FEATURE_CODES, conditional_features, daily_market_data, fit_residual_direction,
)
from finscope_market_data.forecast.direction_evaluation import evaluate_direction
from finscope_market_data.forecast.joint_training import PARAMETERS, ranking_labels

CONDITIONAL_VERSION = 'next-direction-conditional-e1-v1'


def conditional_rollout(rows, feature_codes, base, start, *, parameters=None, market_columns=None, progress=None):
    rows = tuple(sorted(rows, key=lambda r: (r.sample.signal_date, r.code)))
    x = np.asarray([r.sample.features for r in rows])
    y = np.asarray([r.sample.positive for r in rows])
    dates = np.asarray([r.sample.signal_date for r in rows])
    exits = np.asarray([r.sample.exit_date for r in rows])
    target = dates >= start
    base = np.asarray(base, dtype=float)
    if base.shape != (int(target.sum()),) or not np.all(np.isfinite(base)) or np.any((base <= 0) | (base >= 1)):
        raise ValueError('基础 OOF 概率必须与预测股票日完全对齐')
    cols = list(market_columns) if market_columns is not None else [feature_codes.index(c) for c in MARKET_FEATURE_CODES]
    grouped = defaultdict(list)
    for i, day in enumerate(dates):
        grouped[day].append(i)
    last_exit = {day: max(exits[indices]) for day, indices in grouped.items()}
    target_days = sorted(set(dates[target]))
    target_dates, target_exits = dates[target], exits[target]
    output_rows = tuple(r for r, keep in zip(rows, target) if keep)
    q, rank = np.full(len(base), np.nan), np.full(len(base), np.nan)
    probabilities = {f'{mode}_{strength}': base.copy() for mode in CONDITIONAL_MODES for strength in (.01, .1)}
    probabilities['BASE'] = base.copy()
    batches, batch_ids = [], np.zeros(len(base), dtype=int)
    params = PARAMETERS if parameters is None else parameters
    for offset in range(0, len(target_days), 5):
        chunk = target_days[offset:offset+5]
        first = chunk[0]
        mature_days = sorted(day for day in grouped if last_exit[day] < first)[-505:]
        if len(mature_days) < 60:
            raise ValueError('子模型至少需要 60 个完整成熟日期')
        train_mask = np.isin(dates, mature_days)
        training = tuple(r for r, keep in zip(rows, train_mask) if keep)
        _, mx, my = daily_market_data(x[train_mask][:, cols], y[train_mask], dates[train_mask])
        market = make_pipeline(StandardScaler(), Ridge(alpha=100.)).fit(mx, my)
        labels, groups = ranking_labels(training, 'ABSOLUTE')
        ranker = LGBMRanker(**params, objective='lambdarank', label_gain=[0, 1, 2, 3, 4], lambdarank_truncation_level=8)
        ranker.fit(x[train_mask], labels, group=groups)
        mask = np.isin(target_dates, chunk)
        current_x = x[target][mask]
        # Each forecast day's common conditions have one market prediction.
        prediction_days, dx, _ = daily_market_data(current_x[:, cols], np.zeros(mask.sum()), target_dates[mask])
        day_probability = dict(zip(prediction_days, np.clip(market.predict(dx), .01, .99)))
        q[mask] = [day_probability[day] for day in target_dates[mask]]
        rank[mask] = ranker.booster_.predict(current_x)
        past_days = sorted(day for day in target_days if day < first and last_exit[day] < first)[-120:]
        past = np.isin(target_dates, past_days)
        past_rows = tuple(r for r, keep in zip(output_rows, past) if keep)
        batch = dict(batchId=len(batches), startDate=first, endDate=chunk[-1],
            childTrainingThrough=str(max(exits[train_mask])), childTrainingSampleCount=int(train_mask.sum()),
            marketTrainingDayCount=len(mature_days), metaTrainingDayCount=len(past_days),
            metaTrainingThrough=str(max(target_exits[past])) if past_rows else None,
            rankingTarget='ABSOLUTE', marketTarget='NEXT_DAY_POOL_UP_BREADTH', corrections={})
        batch_ids[mask] = batch['batchId']
        for mode in CONDITIONAL_MODES:
            current_features = conditional_features(base[mask], q[mask], rank[mask], target_dates[mask], mode)
            for strength in (.01, .1):
                key = f'{mode}_{strength}'
                if len(past_days) < 30:
                    batch['corrections'][key] = dict(status='WARMUP_BASE', coefficients=[])
                    continue
                features = conditional_features(base[past], q[past], rank[past], target_dates[past], mode)
                model = fit_residual_direction(base[past], features, y[target][past], date_weights(past_rows), strength)
                probabilities[key][mask] = model.predict(base[mask], current_features)
                batch['corrections'][key] = dict(status=model.status, coefficients=model.coefficients.tolist(),
                                                 mean=model.mean.tolist(), scale=model.scale.tolist())
        batches.append(batch)
        if progress:
            progress(dict(stage='conditional', batch=len(batches), totalBatches=int(np.ceil(len(target_days)/5)), startDate=first))
    return dict(rows=output_rows, probabilities=probabilities, marketProbability=q, rankingScore=rank,
                batches=batches, batchIds=batch_ids)


def select_conditional_method(result, start, end):
    mask = np.array([start <= r.sample.signal_date < end and r.sample.exit_date < end for r in result['rows']])
    dates = np.array([r.sample.signal_date for r, keep in zip(result['rows'], mask) if keep])
    y = np.array([r.sample.positive for r, keep in zip(result['rows'], mask) if keep])
    base = result['probabilities']['BASE'][mask]
    candidates = {}
    for key, probabilities in result['probabilities'].items():
        p = probabilities[mask]
        audit = evaluate_direction(p, y, dates, {'BASE': base})
        periods = []
        for chunk in np.array_split(sorted(set(dates)), 3):
            part = np.isin(dates, chunk)
            values = evaluate_direction(p[part], y[part], dates[part], {'BASE': base[part]})
            periods.append(dict(startDate=str(chunk[0]), endDate=str(chunk[-1]), accuracy=values['accuracy'],
                                baseAccuracy=values['comparisons']['BASE']['accuracy']))
        eligible = bool(audit['balancedAccuracy'] is not None and audit['balancedAccuracy'] > .5
                        and audit['brierScore'] <= audit['comparisons']['BASE']['brierScore'] + .001
                        and sum(v['accuracy'] > v['baseAccuracy'] for v in periods) >= 2)
        candidates[key] = dict(accuracy=audit['accuracy'], balancedAccuracy=audit['balancedAccuracy'],
                               brierScore=audit['brierScore'], eligible=eligible, periods=periods)
    eligible = [key for key, candidate in candidates.items() if candidate['eligible']]
    selected = min(eligible, key=lambda key: (-candidates[key]['accuracy'], -candidates[key]['balancedAccuracy'],
                                             candidates[key]['brierScore'], key)) if eligible else 'BASE'
    return dict(selected=selected, candidates=candidates, passed=bool(eligible),
                startDate=start, endExclusive=end, evidenceKind='PRE_TEST_SELECTION')
