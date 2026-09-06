"""Fixed temporal experiments for next-close classification, regression and ranking.

The last 60 dates are a locked acceptance set, never a hyperparameter search set.
Scores measure the supplied universe only, not a survivorship-free market backtest.
"""
from __future__ import annotations

from collections import Counter
import hashlib
import json
import math
from typing import Sequence

import numpy as np
from lightgbm import LGBMClassifier, LGBMRegressor, LGBMRanker
from scipy.stats import rankdata
from sklearn.linear_model import LogisticRegression
from sklearn.pipeline import make_pipeline
from sklearn.preprocessing import StandardScaler

from finscope_market_data.forecast.calibration import PlattCalibrator
from finscope_market_data.forecast.joint_dataset import JointDataset, JointRow

MODEL_VERSION = 'next-session-joint-lgbm-v1'
PARAMETERS = dict(n_estimators=100, learning_rate=.03, num_leaves=15,
                  max_depth=5, min_child_samples=40, reg_lambda=5.,
                  random_state=42, n_jobs=2, verbosity=-1, deterministic=True,
                  force_col_wise=True)


def temporal_split(rows: Sequence[JointRow]) -> dict[str, tuple[JointRow, ...]]:
    dates = sorted({row.sample.signal_date for row in rows})
    if len(dates) < 303:
        raise ValueError('联合模型至少需要 303 个完整截面日期')
    starts = [dates[max(0, len(dates) - 685)], dates[-180], dates[-120], dates[-60]]
    ends = [*starts[1:], '9999-12-31']
    split = {name: tuple(row for row in rows if start <= row.sample.signal_date < end
                        and row.sample.exit_date < end)
             for name, start, end in zip(('train', 'selection', 'calibration', 'test'), starts, ends)}
    if len({r.sample.signal_date for r in split['train']}) < 120 or any(not part for part in split.values()):
        raise ValueError('清除跨段标签后联合训练样本不足')
    return split


def _matrix(rows: Sequence[JointRow]) -> tuple[np.ndarray, np.ndarray]:
    return np.asarray([r.sample.features for r in rows]), np.asarray([r.sample.net_return for r in rows])


def _groups(rows: Sequence[JointRow]) -> list[np.ndarray]:
    dates = np.array([r.sample.signal_date for r in rows])
    boundaries = np.r_[0, np.flatnonzero(dates[1:] != dates[:-1]) + 1, len(rows)]
    return [np.arange(start, end) for start, end in zip(boundaries[:-1], boundaries[1:])]


def ranking_labels(rows: Sequence[JointRow]) -> tuple[list[int], list[int]]:
    if list(rows) != sorted(rows, key=lambda row: (row.sample.signal_date, row.code)):
        raise ValueError('排序样本必须按日期、代码连续分组')
    labels = np.zeros(len(rows), dtype=int)
    groups = _groups(rows)
    for indices in groups:
        values = np.array([rows[i].sample.net_return for i in indices])
        labels[indices] = np.minimum(4, np.floor((rankdata(values) - 1) * 5 / len(indices))).astype(int)
    return labels.tolist(), [len(indices) for indices in groups]


def _daily_mean(values: np.ndarray, rows: Sequence[JointRow]) -> float:
    return float(np.mean([np.mean(values[indices]) for indices in _groups(rows)]))


def _ranking_metrics(scores: np.ndarray, rows: Sequence[JointRow]) -> dict[str, float]:
    returns = np.array([r.sample.net_return for r in rows])
    momentum = np.array([r.sample.features[1] for r in rows])
    correlations, top, excess, momentum_excess = [], [], [], []
    for indices in _groups(rows):
        ranked = indices[np.argsort(-scores[indices], kind='stable')[:5]]
        reference = indices[np.argsort(-momentum[indices], kind='stable')[:5]]
        sr, rr = rankdata(scores[indices]), rankdata(returns[indices])
        correlations.append(float(np.corrcoef(sr, rr)[0, 1]) if np.std(sr) > 0 and np.std(rr) > 0 else 0.)
        value = float(np.mean(returns[ranked]))
        top.append(value)
        excess.append(value - float(np.mean(returns[indices])))
        momentum_excess.append(value - float(np.mean(returns[reference])))
    return dict(rankIc=float(np.mean(correlations)), top5Return=float(np.mean(top)),
                top5PoolExcess=float(np.mean(excess)), top5MomentumExcess=float(np.mean(momentum_excess)))


def _classifiers(x: np.ndarray, returns: np.ndarray) -> dict:
    labels = returns > 0
    if len(np.unique(labels)) < 2:
        raise ValueError('联合训练需要同时存在上涨和下跌样本')
    return {
        'LIGHTGBM': LGBMClassifier(**PARAMETERS).fit(x, labels),
        'POOLED_LOGISTIC': make_pipeline(StandardScaler(), LogisticRegression(C=.1, max_iter=500)).fit(x, labels),
    }


def _probability(model, x: np.ndarray) -> np.ndarray:
    # Booster prediction avoids sklearn feature-name warnings for native array inputs.
    if isinstance(model, LGBMClassifier):
        return np.asarray(model.booster_.predict(x))
    return model.predict_proba(x)[:, 1]


def _calibrated(model, calibration_x, calibration_y, predict_x):
    calibration = PlattCalibrator.fit(_probability(model, calibration_x), calibration_y > 0)
    return np.array([calibration.calibrate(float(p)) for p in _probability(model, predict_x)])


def _radius(regressor, x, y) -> float:
    residuals = np.sort(np.abs(y - regressor.booster_.predict(x)))
    return float(residuals[min(len(residuals) - 1, math.ceil((len(residuals) + 1) * .8) - 1)])


def train_joint_snapshot(dataset: JointDataset) -> dict:
    split = temporal_split(dataset.rows)
    tx, ty = _matrix(split['train'])
    sx, sy = _matrix(split['selection'])
    cx, cy = _matrix(split['calibration'])
    vx, vy = _matrix(split['test'])
    classifiers = _classifiers(tx, ty)
    selection_scores = {code: _daily_mean((_probability(model, sx) - (sy > 0)) ** 2, split['selection'])
                        for code, model in classifiers.items()}
    selected = min(selection_scores, key=lambda code: (selection_scores[code], code))
    probabilities = _calibrated(classifiers[selected], cx, cy, vx)
    logistic = _calibrated(classifiers['POOLED_LOGISTIC'], cx, cy, vx)
    baseline = float(np.mean(ty > 0))
    regressor = LGBMRegressor(**PARAMETERS).fit(tx, ty)
    regression = regressor.booster_.predict(vx)
    radius = _radius(regressor, cx, cy)
    labels, groups = ranking_labels(split['train'])
    ranker = LGBMRanker(**PARAMETERS, objective='lambdarank', label_gain=[0, 1, 2, 3, 4])
    ranker.fit(tx, labels, group=groups)
    selection_rank = _ranking_metrics(ranker.booster_.predict(sx), split['selection'])
    ranking = _ranking_metrics(ranker.booster_.predict(vx), split['test'])
    errors = (probabilities - (vy > 0)) ** 2
    baseline_errors = (baseline - (vy > 0)) ** 2
    brier = _daily_mean(errors, split['test'])
    baseline_brier = _daily_mean(baseline_errors, split['test'])
    logistic_brier = _daily_mean((logistic - (vy > 0)) ** 2, split['test'])
    coverage = _daily_mean((np.abs(vy - regression) <= radius).astype(float), split['test'])
    regression_mse = _daily_mean((regression - vy) ** 2, split['test'])
    baseline_mse = _daily_mean((float(np.mean(ty)) - vy) ** 2, split['test'])
    classification_eligible = brier < baseline_brier and brier <= logistic_brier and .65 <= coverage <= .95
    ranking_eligible = all(metrics['rankIc'] > 0 and metrics['top5PoolExcess'] > 0
                           and metrics['top5MomentumExcess'] > 0 for metrics in (selection_rank, ranking))
    counts = Counter(r.code for r in split['test'])
    evidence = dict(modelVersion=MODEL_VERSION, selectedClassifier=selected,
        featureCount=len(dataset.feature_codes), universeCount=len(dataset.current_features_by_code),
        trainingSampleCount=len(split['train']), validationSampleCount=len(split['test']),
        validationDayCount=len(_groups(split['test'])),
        testStart=split['test'][0].sample.signal_date, testEnd=split['test'][-1].sample.exit_date,
        selectionBrierScore=selection_scores[selected], selectionRankIc=selection_rank['rankIc'],
        pooledBrierScore=brier, baselineBrierScore=baseline_brier, logisticBrierScore=logistic_brier,
        accuracy=_daily_mean(((probabilities >= .5) == (vy > 0)).astype(float), split['test']),
        intervalCoverage=coverage, regressionMse=regression_mse, baselineRegressionMse=baseline_mse,
        classificationEligible=classification_eligible, rankingEligible=ranking_eligible, **ranking)
    # Refit for today's production feature vectors. Locked metrics above stay unchanged.
    dates = sorted({r.sample.signal_date for r in dataset.rows})
    calibration_start = dates[-60]
    production_training = tuple(r for r in dataset.rows if dates[max(0, len(dates) - 565)] <= r.sample.signal_date
                                < calibration_start and r.sample.exit_date < calibration_start)
    production_calibration = tuple(r for r in dataset.rows if r.sample.signal_date >= calibration_start)
    px, py = _matrix(production_training)
    pcx, pcy = _matrix(production_calibration)
    codes = sorted(dataset.current_features_by_code)
    current_x = np.array([dataset.current_features_by_code[code] for code in codes])
    production_model = _classifiers(px, py)[selected]
    current_p = _calibrated(production_model, pcx, pcy, current_x)
    production_regression = LGBMRegressor(**PARAMETERS).fit(px, py)
    current_mean = production_regression.booster_.predict(current_x)
    current_radius = _radius(production_regression, pcx, pcy)
    production_labels, production_groups = ranking_labels(production_training)
    production_ranker = LGBMRanker(**PARAMETERS, objective='lambdarank', label_gain=[0, 1, 2, 3, 4])
    production_ranker.fit(px, production_labels, group=production_groups)
    scores = production_ranker.booster_.predict(current_x)
    percentiles = (rankdata(scores) - .5) / len(scores)
    predictions = {}
    for index, code in enumerate(codes):
        indices = np.array([i for i, row in enumerate(split['test']) if row.code == code], dtype=int)
        stock_brier = float(np.mean(errors[indices])) if len(indices) else None
        stock_baseline = float(np.mean(baseline_errors[indices])) if len(indices) else None
        predictions[code] = dict(upProbability=float(current_p[index]), expectedReturn=float(current_mean[index]),
            lowerReturn=float(current_mean[index] - current_radius), upperReturn=float(current_mean[index] + current_radius),
            rankingScore=float(scores[index]), rankingPercentile=float(percentiles[index]),
            historyFingerprint=dataset.history_fingerprints[code], stockValidationCount=counts[code],
            stockBrierScore=stock_brier, stockBaselineBrierScore=stock_baseline,
            predictionEligible=bool(classification_eligible and counts[code] >= 30 and stock_brier < stock_baseline),
            trainingThrough=production_training[-1].sample.exit_date,
            calibrationThrough=production_calibration[-1].sample.exit_date,
            trainingSampleCount=len(production_training), calibrationSampleCount=len(production_calibration))
    fingerprint_payload = [MODEL_VERSION, PARAMETERS, dataset.as_of, dataset.feature_codes,
                           [(r.code, r.sample.signal_date, r.sample.features, r.sample.net_return) for r in dataset.rows],
                           dataset.current_features_by_code]
    fingerprint = hashlib.sha256(json.dumps(fingerprint_payload, allow_nan=False).encode()).hexdigest()
    return dict(schemaVersion=1, modelVersion=MODEL_VERSION, asOfDate=dataset.as_of,
                dataFingerprint=fingerprint, evidence=evidence, predictions=predictions)
