"""Index-only timing baseline beside equal-date stock selection experiments."""
from finscope_market_data.overnight.direction_candidates import INDEX_ONLY_RECIPE
from finscope_market_data.overnight.direction_learning import direction_prediction
from finscope_market_data.overnight.fresh_direction import fit_fresh_direction
from finscope_market_data.overnight.index_history import FEATURES
from finscope_market_data.overnight.selection_evaluation import PROTOCOL, paired_selection


def train_index_only(enhanced, through, cutoff, baseline_checks, index_checks):
    if len(baseline_checks) != len(index_checks):
        raise ValueError('仅指数基准与选股样本数量不一致')
    rows = [{**r, 'features': r['features'][-len(FEATURES):]} for r in enhanced]
    fitted = fit_fresh_direction(rows, through, cutoff, recipe=INDEX_ONLY_RECIPE,
                                 fixed_candidate='SHAPE_ENSEMBLE', include_checks=True)
    if fitted is None:
        return None, {'protocol': PROTOCOL, 'status': 'ACCUMULATING'}
    fitted['features'] = list(FEATURES)
    index_only = {(r['signalDate'], r['instrumentCode']): r for r in fitted.pop('checks')}
    paired = []
    for base, stock in zip(baseline_checks, index_checks):
        key = (stock['signalDate'], stock['instrumentCode'])
        own = index_only.get(key)
        if own is None:
            raise ValueError('仅指数基准缺少配对样本')
        if ((base['signalDate'], base['instrumentCode']) != key
                or any(base[k] != stock[k] or own[k] != stock[k] for k in ('actualReturn', 'exitAt'))):
            raise ValueError('仅指数基准与选股样本不一致')
        paired.append({'signalDate': key[0], 'instrumentCode': key[1], 'actualUp': stock['actualReturn'] > 0,
            'scores': {'INDEX': stock['raw'], 'STOCK_ONLY': base['raw'], 'INDEX_ONLY': own['raw']}})
    return fitted, {'protocol': PROTOCOL, 'status': 'SHADOW', 'historical': paired_selection(paired)}


def attach_selection(parent, research, index_values):
    parent['stockSelectionModels'] = []
    original = next((r for r in parent['freshModels'] if r['group'] == 'INDEX'), None)
    fitted = (research.get('models') or {}).get('INDEX_ONLY')
    if ((research.get('stockSelection') or {}).get('protocol') != PROTOCOL
            or not original or original['status'] != 'SHADOW' or not fitted
            or fitted.get('features') != list(FEATURES) or index_values is None):
        return
    prediction = direction_prediction(fitted, index_values, {})
    parent['stockSelectionModels'] = [{**original, 'protocol': PROTOCOL,
        'indexOnlyProbability': prediction['rawUpProbability'],
        'indexOnlyTrainingThrough': fitted['audit']['trainingThrough'],
        'reason': '同日、同名单、相同名额对照；仅指数基准不能区分当日股票，不替换主判断'}]
