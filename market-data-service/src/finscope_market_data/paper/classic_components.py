"""Portable, deterministic operators and decisions. Their source is frozen into each strategy version."""
from math import sqrt


def mean(values):
    return sum(values) / len(values)


def deviation(values):
    center = mean(values)
    return sqrt(mean([(v - center) ** 2 for v in values]))


def rate_of_change(values):
    return values[-1] / values[0] - 1


def standard_score(values):
    scale = deviation(values)
    return (values[-1] - mean(values)) / scale if scale else 0.


def percentiles(values):
    """Midranks; ties receive the same score. All outputs lie strictly above zero."""
    ordered = sorted(values, key=values.get)
    result = {}
    i = 0
    while i < len(ordered):
        j = i + 1
        while j < len(ordered) and values[ordered[j]] == values[ordered[i]]:
            j += 1
        rank = (i + j + 1) / (2 * len(ordered))
        result.update({code: rank for code in ordered[i:j]})
        i = j
    return result


def observations(rows, period):
    if period == '1d':
        return rows[-1].get('dailyWindow', [])
    # Keep only newly completed, consecutive candles. Clock gaps at lunch / overnight
    # are expected; gaps within a session invalidate the rolling window.
    from datetime import datetime
    window = []
    for row in rows:
        candle = row.get('completedBars', {}).get(period)
        if not candle or candle['at'] != row['at']:
            continue
        at = datetime.fromisoformat(row['at'])
        if window:
            prior = datetime.fromisoformat(window[-1]['at'])
            minutes = int(period[:-1])
            lunch = prior.hour == 11 and prior.minute == 30 and at.hour == 13 and at.minute == minutes
            if at.date() != prior.date() or ((at - prior).total_seconds() != minutes * 60 and not lunch):
                window = []
        window.append(candle)
    return window


def ema(values, window):
    weight = 2 / (window + 1)
    result = values[0]
    for value in values[1:]:
        result += weight * (value - result)
    return result


def average_true_range(bars):
    ranges = [max(b['high'] - b['low'], abs(b['high'] - a['close']), abs(b['low'] - a['close']))
              for a, b in zip(bars, bars[1:])]
    return mean(ranges)


def window_rsi(closes):
    changes = [b - a for a, b in zip(closes, closes[1:])]
    movement = sum(abs(v) for v in changes)
    return 100 * sum(max(0., v) for v in changes) / movement if movement else 50.


def indicators(rows, recipe):
    n = recipe['window']
    bars = observations(rows, recipe['period'])[-(n+1):]
    if len(bars) < n + 1:
        return None
    closes = [b['close'] for b in bars]
    returns = [b / a - 1 for a, b in zip(closes, closes[1:])]
    latest = bars[-1]
    # Use measured volume and price for the same liquidity proxy in both environments.
    if any(b.get('volume') is None for b in bars[-n:]) or latest['volume'] <= 0:
        return None
    liquidity = mean([b['close'] * b['volume'] for b in bars[-n:]])
    return dict(momentum=rate_of_change(closes), volatility=deviation(returns),
                trend=mean(closes[-max(2, n//3):]) / mean(closes[-n:]) - 1,
                reversal=-standard_score(closes[-n:]), liquidity=liquidity,
                sma=mean(closes[-n:]), ema=ema(closes, n), atr=average_true_range(bars),
                rsi=window_rsi(closes))


def selection(rows, recipe, base):
    data = {}
    for code in base:
        if code in rows and rows[code][-1]['volume'] and rows[code][-1]['high'] > rows[code][-1]['low']:
            values = indicators(rows[code], recipe)
            if values and values['liquidity'] >= recipe['minTurnover']:
                data[code] = values
    liquid = sorted(data, key=lambda c: (-data[c]['liquidity'], c))[:recipe['poolSize']]
    family = recipe['family']
    if family == 'MULTI':
        momentum = percentiles({c: data[c]['momentum'] for c in liquid})
        stability = percentiles({c: -data[c]['volatility'] for c in liquid})
        scores = {c: (momentum[c] + stability[c]) / 2 for c in liquid}
    else:
        field = {'TREND': 'trend', 'MOMENTUM': 'momentum', 'REVERSION': 'reversal'}[family]
        scores = {c: data[c][field] for c in liquid}
    return liquid, data, scores


def portfolio(selected, data, scores, recipe):
    if not selected:
        return {}
    method = recipe['allocation']
    if method == 'INVERSE_VOL':
        raw = {c: 1 / data[c]['volatility'] for c in selected if data[c]['volatility'] > 0}
    elif method == 'SCORE':
        raw = percentiles({c: scores[c] for c in selected})
    else:
        raw = dict.fromkeys(selected, 1.)
    total = sum(raw.values())
    return {c: min(recipe['maxWeight'], recipe['exposure'] * v / total) for c, v in raw.items()}


def decide(ctx):
    cfg = RECIPE
    state = ctx['state']
    from datetime import date
    day = date.fromisoformat(ctx['at'][:10])
    period = {'DAILY': str(day), 'WEEKLY': str(day.isocalendar()[:2]), 'MONTHLY': str(day)[:7]}[cfg['rebalance']]
    if ctx['at'][11:16] != '14:45' or state.get('cycle') == period:
        return dict(weights={}, scores={}, state=state)
    base = ctx.get('selectionBase', list(ctx['bars']))
    liquid, data, scores = selection(ctx['bars'], cfg, base)
    if base and not data:
        return dict(weights={}, scores={}, state=dict(state, selection=dict(at=ctx['at'], candidates=[],
                    targets={}, skipped='本期没有符合条件的行情样本，保留持仓')))
    ordered = sorted(scores, key=lambda c: (-scores[c], c))
    if cfg['family'] in ('TREND', 'REVERSION'):
        ordered = [c for c in ordered if scores[c] > 0]
    if cfg['allocation'] == 'INVERSE_VOL':
        ordered = [c for c in ordered if data[c]['volatility'] > 0]
    chosen = ordered[:cfg['holdings']]
    targets = portfolio(chosen, data, scores, cfg)
    weights, reasons = {}, {}
    for code in set(targets) | set(ctx['positions']):
        if code not in ctx['bars']:
            continue
        target = targets.get(code, 0.)
        if target == 0 and not ctx['positionDetails'].get(code, {}).get('sellable'):
            continue
        weights[code] = target
        scores.setdefault(code, 0.)
        reasons[code] = ('排名 %d，目标仓位 %.1f%%' % (ordered.index(code) + 1, target * 100)
                         if code in targets else '已离开本期持仓名单，调仓目标为零')
    # Persist the rebalance decision separately from the subsequent fills.
    return dict(weights=weights, scores=scores, reasons=reasons, state=dict(cycle=period,
                selection=dict(at=ctx['at'], candidates=liquid, targets=targets)))


def factors(bars):
    result = {}
    for code, rows in bars.items():
        item = indicators(rows, RECIPE)
        if item:
            for field in ('momentum', 'volatility', 'trend', 'reversal'):
                result.setdefault(field, {})[code] = item[field]
    return {field: result.get(field, {}) for field in ('momentum', 'volatility', 'trend', 'reversal')}
