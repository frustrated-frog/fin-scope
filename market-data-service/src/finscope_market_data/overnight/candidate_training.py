"""Small, regularized learner fitted only on past complete automatic candidates."""
import hashlib
import json

import numpy as np
from sklearn.linear_model import LogisticRegression
from sklearn.preprocessing import StandardScaler

from finscope_market_data.overnight.direction_dataset import FEATURES
from finscope_market_data.overnight.joint_dataset import date_weights
from finscope_market_data.overnight.joint_learning import sigmoid

RECIPE = 'ACTUAL_CANDIDATE_LOGISTIC'
PROTOCOL = 'overnight-actual-candidate-training-v1'
FEATURE_CODES = ('INTRADAY_RETURN', 'LAST_30M_RETURN', 'RANGE_POSITION',
                 'RELATIVE_AMOUNT_5', 'PREVIOUS_5D_RETURN', 'PREVIOUS_5D_VOLATILITY')
MIN_DAYS, MIN_ROWS, MAX_DAYS = 5, 30, 60


def fit_candidate_training(rows, cohorts, readiness, through, cutoff):
    identities = {(r['signalDate'], r['instrumentCode']): r for r in rows}
    if len(identities) != len(rows):
        raise ValueError('候选训练不允许同日同股重复')
    days = [d for d in sorted(readiness['days'], key=lambda d: d['signalDate'])
            if d.get('targetAt') and d['targetAt'] < through][-MAX_DAYS:]
    available = []
    for day in days:
        if not day['complete']:
            continue
        decision = f"{day['signalDate']}T{cutoff}:00"
        cohort = cohorts.get((day['signalDate'], cutoff), {})
        if not cohort.get('snapshotAt') or cohort['snapshotAt'] >= decision or decision >= day['targetAt']:
            raise ValueError('候选训练必须使用判断前冻结且标签时序正确的名单')
        batch = [identities.get((day['signalDate'], code)) for code in cohort.get('candidates', {})]
        if len(batch) != day['expectedCount'] or any(r is None or r['exitAt'] != day['targetAt'] for r in batch):
            raise ValueError('完整候选训练日与名单或标签不一致')
        available.extend(batch)
    training = [r for r in available if r.get('trainingEligible', True)]
    dates = {r['signalDate'] for r in training}
    audit = {'protocol': PROTOCOL, 'recipe': RECIPE, 'status': 'INSUFFICIENT_DATA',
        'trainingScope': 'ACTUAL_COMPLETE_CANDIDATES', 'trainingRows': len(training), 'trainingDays': len(dates),
        'availableRows': len(available), 'excludedRows': len(available)-len(training),
        'outsidePoolRows': sum(bool(r.get('candidateOnly')) for r in training),
        'unverifiedActionRows': sum(not r.get('actionsVerified', False) for r in training),
        'plannedDays': len(days), 'incompleteDays': sum(not d['complete'] for d in days),
        'trainingThrough': max((r['exitAt'] for r in training), default=None),
        'minimumDays': MIN_DAYS, 'minimumRows': MIN_ROWS, 'features': list(FEATURE_CODES),
        'weighting': 'UNIT_WEIGHT_PER_DATE', 'regularizationC': 1.,
        'fingerprint': hashlib.sha256(json.dumps(training, sort_keys=True, allow_nan=False).encode()).hexdigest()}
    if len(dates) < MIN_DAYS or len(training) < MIN_ROWS:
        return None, {**audit, 'reason': 'INSUFFICIENT_CANDIDATE_HISTORY'}
    label_days = [len({r['signalDate'] for r in training if (r['actualReturn'] > 0) == label}) for label in (False, True)]
    if min(label_days) < 2:
        return None, {**audit, 'reason': 'INSUFFICIENT_CLASS_DAYS'}
    indices = [FEATURES.index(code) for code in FEATURE_CODES]
    raw = np.asarray([r['features'] for r in training], dtype=float)[:, indices]
    y = np.asarray([r['actualReturn'] > 0 for r in training])
    if not np.isfinite(raw).all() or not all(np.isfinite(r['actualReturn']) for r in training):
        raise ValueError('候选训练特征或标签无效')
    weights = date_weights(training)
    scaler = StandardScaler().fit(raw, sample_weight=weights)
    x = np.clip(scaler.transform(raw), -5., 5.)
    fitted = LogisticRegression(C=1., max_iter=400, random_state=42).fit(x, y, sample_weight=weights)
    if fitted.n_iter_[0] >= 400:
        raise ValueError('候选专训模型未收敛')
    model = {'protocol': PROTOCOL, 'indices': indices, 'mean': scaler.mean_.tolist(), 'scale': scaler.scale_.tolist(),
        'coef': fitted.coef_[0].tolist(), 'intercept': float(fitted.intercept_[0]),
        'prior': float(np.average(y, weights=weights))}
    return model, {**audit, 'status': 'READY', 'reason': 'CANDIDATE_TRAINED'}


def candidate_training_probability(model, features):
    raw = np.asarray(features, dtype=float)[:, model['indices']]
    if not np.isfinite(raw).all():
        raise ValueError('候选预测特征无效')
    x = np.clip((raw-model['mean']) / model['scale'], -5., 5.)
    return sigmoid(x @ model['coef'] + model['intercept'])
