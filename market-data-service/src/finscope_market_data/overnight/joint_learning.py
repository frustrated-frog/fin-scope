"""Fixed pooled linear/tree ensemble; portable JSON, no pickle or test-set tuning."""
from functools import lru_cache

import lightgbm as lgb
import numpy as np
from sklearn.linear_model import LogisticRegression, Ridge
from sklearn.preprocessing import StandardScaler

from finscope_market_data.forecast.direction_evaluation import evaluate_direction
from finscope_market_data.overnight.joint_dataset import (
    CALIBRATION_DAYS, MAX_DAYS, MIN_SYMBOLS, TRAIN_DAYS, date_weights, split_dates,
)

TREE_PARAMETERS = dict(n_estimators=80, learning_rate=.035, num_leaves=7, max_depth=3,
    min_child_samples=60, reg_lambda=10, random_state=42, n_jobs=2, verbosity=-1,
    deterministic=True, force_col_wise=True)
LOSS_THRESHOLD = -.02


def sigmoid(values):
    return 1 / (1 + np.exp(-np.clip(values, -35, 35)))


@lru_cache(maxsize=48)
def booster(model):
    return lgb.Booster(model_str=model)


def fit_classifier(x, labels, weights):
    prior = float(np.average(labels, weights=weights))
    if len(set(labels)) < 2:
        return {'prior': prior}
    linear = LogisticRegression(C=.1, max_iter=400, random_state=42).fit(x, labels, sample_weight=weights)
    tree = lgb.LGBMClassifier(**TREE_PARAMETERS).fit(x, labels, sample_weight=weights)
    return {'prior': prior, 'coef': linear.coef_[0].tolist(), 'intercept': float(linear.intercept_[0]),
            'tree': tree.booster_.model_to_string()}


def raw_probabilities(model, x):
    if 'tree' not in model:
        return np.full(len(x), model['prior'])
    return .5 * sigmoid(x @ model['coef'] + model['intercept']) + .5 * booster(model['tree']).predict(x, num_threads=2)


def fit_calibration(raw, labels, rows):
    # Dates carry equal mass; count independent dates, not pooled rows, for readiness.
    positive_days = {row['signalDate'] for row, label in zip(rows, labels) if label}
    negative_days = {row['signalDate'] for row, label in zip(rows, labels) if not label}
    if min(len(positive_days), len(negative_days)) < 5:
        return {'status': 'UNAVAILABLE', 'coef': 1., 'intercept': 0.}
    bounded = np.clip(raw, 1e-6, 1 - 1e-6)
    logits = np.log(bounded / (1 - bounded)).reshape(-1, 1)
    fitted = LogisticRegression(C=1, max_iter=300, random_state=42).fit(logits, labels, sample_weight=date_weights(rows))
    return {'status': 'FITTED', 'coef': float(fitted.coef_[0, 0]), 'intercept': float(fitted.intercept_[0])}


def calibrated(raw, calibration):
    bounded = np.clip(raw, 1e-6, 1 - 1e-6)
    return sigmoid(calibration['coef'] * np.log(bounded / (1 - bounded)) + calibration['intercept'])


def transform(model, features):
    x = np.clip(np.asarray(features), model['clipLow'], model['clipHigh'])
    return (x - model['mean']) / model['scale']


def estimates(model, x):
    regression = model['regression']
    return .5 * (x @ regression['coef'] + regression['intercept']) + .5 * booster(regression['tree']).predict(x, num_threads=2)


def predict_many(model, features):
    x = transform(model, features)
    p = calibrated(raw_probabilities(model['direction'], x), model['calibration'])
    downside = calibrated(raw_probabilities(model['downside'], x), model['downsideCalibration'])
    expected = estimates(model, x)
    return p, expected, downside


def prediction(model, features):
    p, expected, downside = (float(values[0]) for values in predict_many(model, [features]))
    lower, upper = expected + model['residualLow'], expected + model['residualHigh']
    # Fixed before forward evaluation; the model may abstain on every stock.
    qualified = (p >= .55 and expected > 0 and downside <= .25 and lower >= -.04
                 and model['calibration']['status'] == 'FITTED'
                 and model['downsideCalibration']['status'] == 'FITTED')
    return {'upProbability': p, 'expectedNetReturn': expected, 'downsideProbability': downside,
            'lowerNetReturn': lower, 'upperNetReturn': upper, 'qualified': qualified,
            'rankScore': expected - .01 * downside, 'baselineProbability': model['baselineProbability'],
            'calibrationStatus': model['calibration']['status'],
            'downsideCalibrationStatus': model['downsideCalibration']['status']}


def fit_before(rows, through):
    matured = [row for row in rows if row['exitAt'] < through]
    dates = sorted({row['signalDate'] for row in matured})[-MAX_DAYS:]
    if len(dates) < TRAIN_DAYS + CALIBRATION_DAYS + 1:
        return None
    calibration_start = dates[-CALIBRATION_DAYS]
    training = [row for row in matured if dates[0] <= row['signalDate'] < calibration_start
                and row['exitAt'][:10] < calibration_start]
    calibration = [row for row in matured if row['signalDate'] >= calibration_start]
    if (len({row['signalDate'] for row in training}) < TRAIN_DAYS
            or min(len({row['instrumentCode'] for row in part}) for part in (training, calibration)) < MIN_SYMBOLS):
        return None
    raw_x = np.asarray([row['features'] for row in training])
    weights = date_weights(training)
    clip_low, clip_high = np.quantile(raw_x, [.01, .99], axis=0)
    clipped = np.clip(raw_x, clip_low, clip_high)
    scaler = StandardScaler().fit(clipped, sample_weight=weights)
    x = scaler.transform(clipped)
    returns = np.asarray([row['actualNetReturn'] for row in training])
    linear = Ridge(alpha=50).fit(x, returns, sample_weight=weights)
    tree = lgb.LGBMRegressor(**TREE_PARAMETERS).fit(x, returns, sample_weight=weights)
    model = {'mean': scaler.mean_.tolist(), 'scale': scaler.scale_.tolist(),
             'clipLow': clip_low.tolist(), 'clipHigh': clip_high.tolist(),
             'direction': fit_classifier(x, returns > 0, weights),
             'downside': fit_classifier(x, returns < LOSS_THRESHOLD, weights),
             'regression': {'coef': linear.coef_.tolist(), 'intercept': float(linear.intercept_),
                            'tree': tree.booster_.model_to_string()}}
    cx = transform(model, [row['features'] for row in calibration])
    cy = np.asarray([row['actualNetReturn'] for row in calibration])
    model['calibration'] = fit_calibration(raw_probabilities(model['direction'], cx), cy > 0, calibration)
    model['downsideCalibration'] = fit_calibration(raw_probabilities(model['downside'], cx), cy < LOSS_THRESHOLD, calibration)
    residuals = cy - estimates(model, cx)
    order = np.argsort(residuals)
    calibration_weights = date_weights(calibration)
    cumulative = np.cumsum(calibration_weights[order]) / calibration_weights.sum()
    model['residualLow'], model['residualHigh'] = [float(residuals[order[min(len(order) - 1,
        np.searchsorted(cumulative, q))]]) for q in (.1, .9)]
    baseline_rows = training + calibration
    model['baselineProbability'] = float(np.average([row['actualNetReturn'] > 0 for row in baseline_rows],
                                                    weights=date_weights(baseline_rows)))
    return {'model': model, 'audit': {
        'trainingDays': len({r['signalDate'] for r in training}), 'trainingRows': len(training),
        'calibrationDays': len({r['signalDate'] for r in calibration}),
        'trainingThrough': max(r['exitAt'] for r in training), 'calibrationStart': calibration_start,
        'calibrationThrough': max(r['exitAt'] for r in calibration),
        'calibrationStatus': model['calibration']['status']}}


def fit_target(rows, through, decision_time='14:30'):
    split = split_dates(rows, through)
    if split is None:
        return None
    test = split[2]
    checks, folds = [], []
    for day in sorted({row['signalDate'] for row in test}):
        cutoff = f'{day}T{decision_time}:00'
        past = fit_before(rows, cutoff)
        if past is None:
            return None
        fold_rows = [row for row in test if row['signalDate'] == day]
        model = past['model']
        p, estimate, _ = predict_many(model, [row['features'] for row in fold_rows])
        for row, probability, expected in zip(fold_rows, p, estimate):
            checks.append({**row, 'probability': float(probability), 'expected': float(expected),
                'lower': float(expected + model['residualLow']), 'upper': float(expected + model['residualHigh']),
                'baseline': model['baselineProbability']})
        folds.append({'signalDate': day, 'cutoff': cutoff, **past['audit']})
    # Refit for the next live decision using ALL already-matured labels. Historical
    # diagnostic rows can enter later training, never an earlier day's prediction.
    current = fit_before(rows, through)
    if current is None:
        return None
    p = [row['probability'] for row in checks]
    actual = np.asarray([row['actualNetReturn'] for row in checks])
    estimate = np.asarray([row['expected'] for row in checks])
    dates = [row['signalDate'] for row in checks]
    diagnostics = evaluate_direction(p, actual > 0, dates, {'HISTORICAL_PRIOR': [row['baseline'] for row in checks]})
    # Fixed daily walk-forward policy. No parameters are selected from these results.
    diagnostics['eligible'] = False
    diagnostics['reason'] = '历史样本诊断；启用需要另行积累固定协议下的真实前瞻对照'
    diagnostics['expectedReturnMae'] = float(np.average(np.abs(actual - estimate), weights=date_weights(checks)))
    diagnostics['intervalCoverage'] = float(np.average((actual >= [row['lower'] for row in checks]) &
        (actual <= [row['upper'] for row in checks]), weights=date_weights(checks)))
    audit = {**current['audit'],
             'testStart': min(dates), 'testThrough': max(r['exitAt'] for r in test),
             'historical': diagnostics, 'folds': folds, 'evaluation': 'DAILY_WALK_FORWARD'}
    return {'model': current['model'], 'audit': audit}
