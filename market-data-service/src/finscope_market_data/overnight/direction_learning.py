"""Cost-independent direction with purged selection and order-preserving calibration."""
from dataclasses import asdict

import lightgbm as lgb
import numpy as np
from sklearn.linear_model import LogisticRegression
from sklearn.preprocessing import StandardScaler

from finscope_market_data.forecast.direction_calibration import fit_direction_calibration
from finscope_market_data.forecast.calibration_gate import select_calibration_outputs
from finscope_market_data.forecast.direction_evaluation import evaluate_direction
from finscope_market_data.overnight.direction_dataset import PROTOCOL, TARGET
from finscope_market_data.overnight.joint_dataset import CALIBRATION_DAYS, MAX_DAYS, MIN_SYMBOLS, TRAIN_DAYS, date_weights
from finscope_market_data.overnight.joint_learning import TREE_PARAMETERS, booster, sigmoid

SELECTION_DAYS = 20
TEST_DAYS = 20
CANDIDATES = ('PRIOR', 'SESSION_LOGISTIC', 'SHAPE_LOGISTIC', 'SHAPE_TREE', 'SHAPE_ENSEMBLE')


def temporal_parts(rows, through):
    matured = [row for row in rows if row['exitAt'] < through]
    days = sorted({row['signalDate'] for row in matured})[-MAX_DAYS:]
    if len(days) < TRAIN_DAYS + SELECTION_DAYS + CALIBRATION_DAYS + 2:
        return None
    selection_start, calibration_start = days[-40], days[-20]
    training = [r for r in matured if days[0] <= r['signalDate'] < selection_start and r['exitAt'][:10] < selection_start]
    selection = [r for r in matured if selection_start <= r['signalDate'] < calibration_start and r['exitAt'][:10] < calibration_start]
    calibration = [r for r in matured if r['signalDate'] >= calibration_start]
    if (len({r['signalDate'] for r in training}) < TRAIN_DAYS
            or min(len({r['instrumentCode'] for r in part}) for part in (training, selection, calibration)) < MIN_SYMBOLS):
        return None
    return training, selection, calibration


def fit_candidates(rows):
    raw = np.array([r['features'] for r in rows])
    # Equal dates, mean-one row weights: regularization has the declared sample scale.
    weights = date_weights(rows)
    weights = weights / weights.mean()
    low, high = np.quantile(raw, [.01, .99], axis=0)
    clipped = np.clip(raw, low, high)
    scaler = StandardScaler().fit(clipped, sample_weight=weights)
    x, y = scaler.transform(clipped), np.array([r['actualReturn'] > 0 for r in rows])
    model = {'mean': scaler.mean_.tolist(), 'scale': scaler.scale_.tolist(),
             'clipLow': low.tolist(), 'clipHigh': high.tolist(), 'prior': float(np.average(y, weights=weights))}
    if len(set(y)) < 2:
        return model
    for name, width in (('session', 14), ('shape', x.shape[1])):
        fit = LogisticRegression(C=.1, max_iter=400, random_state=42).fit(x[:, :width], y, sample_weight=weights)
        model[name] = {'coef': fit.coef_[0].tolist(), 'intercept': float(fit.intercept_[0])}
    tree = lgb.LGBMClassifier(**TREE_PARAMETERS).fit(x, y, sample_weight=weights)
    model['tree'] = tree.booster_.model_to_string()
    return model


def candidate_probabilities(model, features):
    x = (np.clip(np.asarray(features), model['clipLow'], model['clipHigh']) - model['mean']) / model['scale']
    prior = np.full(len(x), model['prior'])
    if 'tree' not in model:
        return {name: prior.copy() for name in CANDIDATES}
    session = sigmoid(x[:, :14] @ model['session']['coef'] + model['session']['intercept'])
    shape = sigmoid(x @ model['shape']['coef'] + model['shape']['intercept'])
    tree = booster(model['tree']).predict(x, num_threads=2)
    return dict(zip(CANDIDATES, (prior, session, shape, tree, .5 * (shape + tree))))


def apply_calibration(p, calibration):
    p = np.clip(p, 1e-6, 1 - 1e-6)
    return sigmoid(calibration['slope'] * np.log(p / (1 - p)) + calibration['intercept'])


def predict_direction(model, features):
    raw = candidate_probabilities(model, features)[model['selected']]
    return raw, apply_calibration(raw, model['calibration'])


def fit_direction_before(rows, through):
    parts = temporal_parts(rows, through)
    if parts is None:
        return None
    training, selection, calibration = parts
    candidates = fit_candidates(training)
    sy = np.array([r['actualReturn'] > 0 for r in selection])
    weights = date_weights(selection)
    scores = {name: float(np.average((p - sy) ** 2, weights=weights)) for name, p in
              candidate_probabilities(candidates, [r['features'] for r in selection]).items()}
    selected = min(CANDIDATES, key=lambda name: (scores[name], CANDIDATES.index(name)))
    # Selection labels may now train the chosen recipe; calibration remains separate.
    model = fit_candidates(training + selection)
    model['selected'] = selected
    cy = np.array([r['actualReturn'] > 0 for r in calibration])
    raw = candidate_probabilities(model, [r['features'] for r in calibration])[selected]
    enough_days = min(len({r['signalDate'] for r, label in zip(calibration, cy) if label == value}) for value in (False, True)) >= 5
    mode = 'INTERCEPT' if enough_days else 'RAW'
    model['calibration'] = asdict(fit_direction_calibration(raw, cy, date_weights(calibration), mode))
    model['baselineProbability'] = float(np.average([r['actualReturn'] > 0 for r in training + selection + calibration],
                                                   weights=date_weights(training + selection + calibration)))
    return {'model': model, 'audit': {'selected': selected, 'selectionBrier': scores,
        'trainingThrough': max(r['exitAt'] for r in training), 'selectionStart': min(r['signalDate'] for r in selection),
        'selectionThrough': max(r['exitAt'] for r in selection), 'calibrationStart': min(r['signalDate'] for r in calibration),
        'calibrationThrough': max(r['exitAt'] for r in calibration),
        'trainingDays': len({r['signalDate'] for r in training}),
        'selectionDays': len({r['signalDate'] for r in selection}), 'calibrationDays': len({r['signalDate'] for r in calibration})}}


def fit_direction(rows, through, cutoff):
    matured = [r for r in rows if r['exitAt'] < through]
    days = sorted({r['signalDate'] for r in matured})
    if len(days) < TRAIN_DAYS + SELECTION_DAYS + CALIBRATION_DAYS + TEST_DAYS + 3:
        return None
    checks, folds = [], []
    for day in days[-TEST_DAYS:]:
        decision_at = f'{day}T{cutoff}:00'
        fitted = fit_direction_before(matured, decision_at)
        if fitted is None:
            return None
        test = [r for r in matured if r['signalDate'] == day]
        raw, calibrated = predict_direction(fitted['model'], [r['features'] for r in test])
        gate = calibration_gate(checks, decision_at)
        p = calibrated if calibration_source(gate) == 'INTERCEPT' else raw
        checks.extend({**r, 'probability': float(value), 'raw': float(unadjusted), 'calibrated': float(adjusted),
                       'baseline': fitted['model']['baselineProbability']} for r, value, unadjusted, adjusted in zip(test, p, raw, calibrated))
        folds.append({'signalDate': day, 'cutoff': decision_at, **fitted['audit']})
    current = fit_direction_before(matured, through)
    if current is None:
        return None
    labels, dates = [r['actualReturn'] > 0 for r in checks], [r['signalDate'] for r in checks]
    baselines = {'HISTORICAL_PRIOR': [r['baseline'] for r in checks]}
    historical = evaluate_direction([r['probability'] for r in checks], labels, dates, baselines)
    raw = evaluate_direction([r['raw'] for r in checks], labels, dates, baselines)
    calibrated_audit = evaluate_direction([r['calibrated'] for r in checks], labels, dates, baselines)
    current['model']['calibrationGate'] = calibration_gate(checks, through)
    historical.update(task=TARGET, eligible=False, reason='历史开发诊断；次日涨跌需单独积累真实前瞻记录',
                      raw={key: raw[key] for key in ('accuracy', 'balancedAccuracy', 'brierScore', 'auc', 'predictedUpRate')},
                      calibrated={key: calibrated_audit[key] for key in ('accuracy', 'balancedAccuracy', 'brierScore', 'auc', 'predictedUpRate')})
    return {'protocol': PROTOCOL, 'target': TARGET, 'model': current['model'], 'audit': {**current['audit'],
        'testStart': min(dates), 'testThrough': max(r['exitAt'] for r in checks),
        'historical': historical, 'folds': folds, 'evaluation': 'DAILY_WALK_FORWARD'}}


def direction_prediction(fitted, features, live_gate=None):
    raw, adjusted = predict_direction(fitted['model'], [features])
    gate = live_gate if live_gate is not None else fitted['model'].get('calibrationGate', {})
    source = calibration_source(gate)
    p = float(adjusted[0] if source == 'INTERCEPT' else raw[0])
    return {'protocol': PROTOCOL, 'target': TARGET, 'status': 'SHADOW', 'upProbability': p,
            'notUpProbability': 1 - p, 'rawUpProbability': float(raw[0]),
            'calibratedUpProbability': float(adjusted[0]), 'probabilitySource': source,
            'direction': 'UP' if p >= .5 else 'NOT_UP', 'selectedModel': fitted['model']['selected'],
            'baselineProbability': fitted['model']['baselineProbability'],
            'calibrationStatus': fitted['model']['calibration']['status'],
            'historical': fitted['audit']['historical'], 'corporateActionsVerified': False}


def calibration_gate(checks, through):
    matured = [row for row in checks if row['exitAt'] < through]
    return select_calibration_outputs([r['raw'] for r in matured], [r['calibrated'] for r in matured],
        [r['actualReturn'] > 0 for r in matured], [r['signalDate'] for r in matured])


def calibration_source(gate):
    return 'INTERCEPT' if gate.get('directionSource') == gate.get('probabilitySource') == 'INTERCEPT' else 'RAW'
