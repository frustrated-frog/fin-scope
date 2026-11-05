"""Portable, predeclared direction recipes. No selection on the test window."""
import lightgbm as lgb
import numpy as np
from sklearn.linear_model import LogisticRegression
from sklearn.preprocessing import StandardScaler

from finscope_market_data.overnight.joint_dataset import date_weights
from finscope_market_data.overnight.joint_learning import TREE_PARAMETERS, booster, sigmoid
from finscope_market_data.overnight.multiscale_features import FEATURES as HISTORY_FEATURES

BASE_RECIPE = 'BASE'
MULTISCALE_RECIPE = 'MULTISCALE_RECENCY_V1'
RECENCY_HALF_LIFE = 40
CANDIDATES = ('PRIOR', 'SESSION_LOGISTIC', 'SHAPE_LOGISTIC', 'SHAPE_TREE', 'SHAPE_ENSEMBLE')
HISTORY_CANDIDATES = ('MULTISCALE_ENSEMBLE', 'RECENT_ENSEMBLE')


def training_weights(rows, half_life=None):
    weights = date_weights(rows)
    if half_life is not None:
        days = sorted({row['signalDate'] for row in rows})
        ages = {day: len(days) - 1 - i for i, day in enumerate(days)}
        weights *= np.array([2 ** (-ages[row['signalDate']] / half_life) for row in rows])
    # Preserve the sample scale of regularization, including weighted recipes.
    return weights / weights.mean()


def fit_logistic(x, y, weights):
    fit = LogisticRegression(C=.1, max_iter=400, random_state=42).fit(x, y, sample_weight=weights)
    return {'coef': fit.coef_[0].tolist(), 'intercept': float(fit.intercept_[0])}


def fit_tree(x, y, weights):
    return lgb.LGBMClassifier(**TREE_PARAMETERS).fit(x, y, sample_weight=weights).booster_.model_to_string()


def fit_candidates(rows, recipe=BASE_RECIPE):
    if recipe not in (BASE_RECIPE, MULTISCALE_RECIPE):
        raise ValueError('未知方向学习方案')
    rows = [row for row in rows if row.get('trainingEligible', True)]
    raw = np.array([r['features'] for r in rows])
    width = raw.shape[1] - len(HISTORY_FEATURES) if recipe == MULTISCALE_RECIPE else raw.shape[1]
    if width < 14 or not np.all(np.isfinite(raw)):
        raise ValueError('方向模型特征宽度或数值无效')
    weights = training_weights(rows)
    low, high = np.quantile(raw, [.01, .99], axis=0)
    clipped = np.clip(raw, low, high)
    scaler = StandardScaler().fit(clipped, sample_weight=weights)
    x, y = scaler.transform(clipped), np.array([r['actualReturn'] > 0 for r in rows])
    model = {'mean': scaler.mean_.tolist(), 'scale': scaler.scale_.tolist(),
             'clipLow': low.tolist(), 'clipHigh': high.tolist(), 'prior': float(np.average(y, weights=weights)),
             'recipe': recipe, 'shapeWidth': width}
    if len(set(y)) < 2:
        return model
    model['session'] = fit_logistic(x[:, :14], y, weights)
    model['shape'] = fit_logistic(x[:, :width], y, weights)
    model['tree'] = fit_tree(x[:, :width], y, weights)
    if recipe == MULTISCALE_RECIPE:
        recent = training_weights(rows, RECENCY_HALF_LIFE)
        model['multiscale'] = {'linear': fit_logistic(x, y, weights), 'tree': fit_tree(x, y, weights)}
        model['recent'] = {'linear': fit_logistic(x, y, recent), 'tree': fit_tree(x, y, recent),
                           'halfLifeSessions': RECENCY_HALF_LIFE}
    return model


def candidate_probabilities(model, features):
    x = (np.clip(np.asarray(features), model['clipLow'], model['clipHigh']) - model['mean']) / model['scale']
    prior = np.full(len(x), model['prior'])
    extra = HISTORY_CANDIDATES if model.get('recipe') == MULTISCALE_RECIPE else ()
    if 'tree' not in model:
        return {name: prior.copy() for name in CANDIDATES + extra}
    width = model.get('shapeWidth', x.shape[1])
    session = sigmoid(x[:, :14] @ model['session']['coef'] + model['session']['intercept'])
    shape = sigmoid(x[:, :width] @ model['shape']['coef'] + model['shape']['intercept'])
    tree = booster(model['tree']).predict(x[:, :width], num_threads=2)
    result = dict(zip(CANDIDATES, (prior, session, shape, tree, .5 * (shape + tree))))
    for name, key in zip(extra, ('multiscale', 'recent')):
        fitted = model[key]
        linear = sigmoid(x @ fitted['linear']['coef'] + fitted['linear']['intercept'])
        result[name] = .5 * (linear + booster(fitted['tree']).predict(x, num_threads=2))
    return result
