"""Paired, date-balanced development diagnostics for candidate-only learning."""
from finscope_market_data.forecast.direction_evaluation import evaluate_direction
from finscope_market_data.overnight.candidate_evaluation import evaluate_candidates, evaluate_ranking
from finscope_market_data.overnight.detection_metrics import detection

ARMS = {'CANDIDATE': 'probability', 'POOL_ROLLING': 'poolProbability',
        'POOL_LOGISTIC': 'poolShapeProbability', 'POOL_PRIOR': 'poolPrior', 'CANDIDATE_PRIOR': 'candidatePrior'}


def paired_metrics(rows):
    if not rows:
        return {'dayCount': 0, 'sampleCount': 0, 'arms': {}, 'comparisons': {}, 'momentum': None}
    dates = [r['signalDate'] for r in rows]
    labels = [r['actualUp'] for r in rows]
    result = evaluate_direction([r['probability'] for r in rows], labels, dates,
        {name: [r[key] for r in rows] for name, key in ARMS.items() if name != 'CANDIDATE'})
    arms = {}
    for name, key in ARMS.items():
        values = [{**r, 'probability': r[key], 'score': r[key]} for r in rows]
        direction = evaluate_direction([r[key] for r in rows], labels, dates,
                                       {'POOL_PRIOR': [r['poolPrior'] for r in rows]})
        arms[name] = {'accuracy': direction['accuracy'], 'balancedAccuracy': direction['balancedAccuracy'],
            'brierScore': direction['brierScore'], 'detection': detection(values),
            'topK': evaluate_ranking(values), 'calls': evaluate_candidates(values)}
    return {'dayCount': len(set(dates)), 'sampleCount': len(rows), 'arms': arms,
        'comparisons': result['comparisons'],
        'momentum': evaluate_ranking([{**r, 'score': r['momentum']} for r in rows])}


def evaluate_candidate_training(checks, readiness, through):
    paired = [r for r in checks if r['candidateReady']]
    dates = sorted({r['signalDate'] for r in paired})
    last = set(dates[-5:])
    return {**paired_metrics(paired),
        'plannedDays': sum(bool(d.get('targetAt') and d['targetAt'] < through) for d in readiness['days']),
        'warmupDays': len({r['signalDate'] for r in checks if not r['candidateReady']}),
        'firstDate': dates[0] if dates else None, 'lastDate': dates[-1] if dates else None,
        'lastFiveDates': paired_metrics([r for r in paired if r['signalDate'] in last]),
        'evidenceKind': 'RETROSPECTIVE', 'eligible': False, 'weighting': 'EQUAL_DATE_THEN_STOCK',
        'scope': '专训已具备条件的同日完整名单；末五日仍为历史开发诊断，不是独立前瞻验收'}
