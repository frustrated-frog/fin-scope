"""Paired binary-up ranking experiment. Scores never become up probabilities."""
from collections import Counter
import hashlib
import json

import lightgbm as lgb
import numpy as np

from finscope_market_data.overnight.candidate_evaluation import evaluate_ranking
from finscope_market_data.overnight.context_research import MODEL_FEATURES
from finscope_market_data.overnight.direction_dataset import TARGET
from finscope_market_data.overnight.direction_learning import covered_matured_rows, temporal_parts, TEST_DAYS
from finscope_market_data.overnight.direction_candidates import training_weights
from finscope_market_data.overnight.joint_dataset import TRAIN_DAYS, CALIBRATION_DAYS
from finscope_market_data.overnight.joint_learning import TREE_PARAMETERS, booster

PROTOCOL = 'overnight-binary-up-ranking-v1'
TOP_K = 3
MIN_MIXED_DAYS = 20


def validate_panel(rows):
    seen = set()
    for row in rows:
        key = (row['signalDate'], row['instrumentCode'])
        if key in seen:
            raise ValueError('排序输入存在重复股票日期')
        seen.add(key)
        x = np.asarray(row['features'])
        if x.shape != (len(MODEL_FEATURES),) or not np.all(np.isfinite(x)) or not np.isfinite(row['actualReturn']):
            raise ValueError('排序输入特征或标签无效')
        if row['exitAt'][:10] <= row['signalDate']:
            raise ValueError('排序标签必须晚于信号日')


def fit_pair_before(rows, through):
    parts = temporal_parts(rows, through)
    if parts is None:
        return None
    training, selection, calibration = parts
    # No winner selection or calibration. Reserve the same calibration interval
    # as the direction pipeline; both objectives see exactly the same past rows.
    train = sorted([r for r in training + selection if r.get('trainingEligible', True)],
                   key=lambda r: (r['signalDate'], r['instrumentCode']))
    labels = np.array([r['actualReturn'] > 0 for r in train], dtype=int)
    groups = Counter(r['signalDate'] for r in train)
    offset, mixed = 0, 0
    for count in groups.values():
        mixed += int(len(set(labels[offset:offset + count])) == 2)
        offset += count
    if mixed < MIN_MIXED_DAYS:
        return None
    x = np.array([r['features'] for r in train])
    low, high = np.quantile(x, [.01, .99], axis=0)
    x = np.clip(x, low, high)
    weights = training_weights(train)
    classifier = lgb.LGBMClassifier(**TREE_PARAMETERS).fit(x, labels, sample_weight=weights)
    ranker = lgb.LGBMRanker(**TREE_PARAMETERS, objective='lambdarank', label_gain=[0, 1],
        lambdarank_truncation_level=TOP_K).fit(x, labels, group=list(groups.values()), sample_weight=weights)
    return {'model': {'classifier': classifier.booster_.model_to_string(), 'ranker': ranker.booster_.model_to_string(),
                     'clipLow': low.tolist(), 'clipHigh': high.tolist()},
            'audit': {'trainingThrough': max(r['exitAt'] for r in train),
                'calibrationThrough': max(r['exitAt'] for r in calibration), 'calibrationUsed': False,
                'trainingRows': len(train), 'trainingDays': len(groups), 'mixedLabelDays': mixed,
                'trainingFingerprint': hashlib.sha256(json.dumps(train, sort_keys=True, allow_nan=False).encode()).hexdigest()}}


def predict_pair(fitted, features):
    model = fitted['model']
    x = np.asarray(features)
    if x.ndim != 2 or x.shape[1] != len(MODEL_FEATURES) or not np.all(np.isfinite(x)):
        raise ValueError('排序推断特征无效')
    x = np.clip(x, model['clipLow'], model['clipHigh'])
    return {name: booster(model[key]).predict(x, num_threads=2)
            for name, key in (('CLASSIFIER', 'classifier'), ('RANKER', 'ranker'))}


def ranking_comparison(rows):
    results = {}
    for name, key in (('CLASSIFIER', 'classifierProbability'), ('RANKER', 'rankScore'), ('MOMENTUM', 'momentumScore')):
        results[name] = evaluate_ranking([{**r, 'score': r[key]} for r in rows], top_k=TOP_K)
    results['UNIFORM'] = evaluate_ranking([{**r, 'score': 0.} for r in rows], top_k=TOP_K)
    return {'status': 'SHADOW', 'eligible': False, 'pairedDays': len({r['signalDate'] for r in rows}),
            'pairedRows': len(rows), 'topK': TOP_K, 'results': results}


def fit_tail_ranking(rows, through, cutoff, *, include_checks=False):
    if cutoff not in ('14:30', '14:45'):
        raise ValueError('尾盘排序仅支持 14:30 / 14:45')
    validate_panel(rows)
    matured, coverage = covered_matured_rows(rows, through)
    days = sorted({r['signalDate'] for r in matured})
    if len(days) < TRAIN_DAYS + 20 + CALIBRATION_DAYS + TEST_DAYS + 3:
        return None
    checks, folds = [], []
    for day in days[-TEST_DAYS:]:
        fitted = fit_pair_before(rows, f'{day}T{cutoff}:00')
        if fitted is None:
            return None
        test = [r for r in matured if r['signalDate'] == day]
        scores = predict_pair(fitted, [r['features'] for r in test])
        checks.extend({'signalDate': r['signalDate'], 'instrumentCode': r['instrumentCode'],
            'actualUp': r['actualReturn'] > 0, 'actualReturn': r['actualReturn'], 'exitAt': r['exitAt'],
            'classifierProbability': float(p), 'rankScore': float(s), 'momentumScore': r['features'][0]}
            for r, p, s in zip(test, scores['CLASSIFIER'], scores['RANKER']))
        folds.append({'signalDate': day, **fitted['audit']})
    current = fit_pair_before(rows, through)
    if current is None:
        return None
    result = {'protocol': PROTOCOL, 'target': TARGET, 'features': list(MODEL_FEATURES),
        'trainingScope': 'FIXED_RESEARCH_POOL', 'status': 'SHADOW', 'eligible': False,
        'model': current['model'], 'audit': {**current['audit'], 'folds': folds,
            'testThrough': max(r['exitAt'] for r in checks), 'dateCoverage': coverage,
            'historical': ranking_comparison(checks), 'evidenceKind': 'RETROSPECTIVE',
            'scope': '公共研究池开发对照，不代表真实发现候选成绩'},
        'data': {'fingerprint': hashlib.sha256(json.dumps(rows, sort_keys=True, allow_nan=False).encode()).hexdigest()}}
    if include_checks:
        result['checks'] = checks
    return result
