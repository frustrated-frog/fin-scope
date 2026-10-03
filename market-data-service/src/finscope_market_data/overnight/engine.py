"""Fixed local models, strictly past-only features and next-session executable proxies."""
from datetime import datetime, timedelta
import hashlib
import json

import numpy as np

from finscope_market_data.forecast.trading_calendar import next_session
from finscope_market_data.overnight.learning import forecast_target

VERSION = 'overnight-local-v3-calibrated'
TARGETS = ('OPEN', '10:00', '14:30', 'CLOSE')


def group_bars(bars):
    grouped = {}
    for bar in sorted(bars, key=lambda x: x.ended_at):
        grouped.setdefault(bar.ended_at.date(), {})[bar.ended_at.strftime('%H:%M')] = bar
    return grouped


def expected_times(cutoff):
    # Vendor 5-minute bars are labelled by their interval end; auction is not used.
    start = datetime(2026, 1, 1, 9, 35)
    result = []
    for offset in range(66):
        stamp = (start + timedelta(minutes=5 * offset)).strftime('%H:%M')
        if ('09:35' <= stamp <= '11:30' or '13:05' <= stamp <= '15:00') and stamp <= cutoff:
            result.append(stamp)
    return result


def features(day, cutoff):
    times = expected_times(cutoff)
    if not times or any(stamp not in day for stamp in times):
        return None
    bars = [day[t] for t in times]
    prices = np.array([x.close for x in bars])
    amounts = np.array([x.amount for x in bars])
    if amounts.sum() <= 0:
        return None
    first, last = bars[0].open, bars[-1].close
    changes = np.diff(prices) / prices[:-1]
    return [last / first - 1, last / prices[-4] - 1,
            max(x.high for x in bars) / first - 1, min(x.low for x in bars) / first - 1,
            float(np.std(changes)), float(amounts[-3:].sum() / amounts.sum()),
            last / float(np.average(prices, weights=amounts)) - 1,
            float(np.mean(changes > 0))]


def entry_bar(day, request):
    if request.mode == 'AFTER_CLOSE_HOLDING':
        return day.get('15:00'), 'close'
    decision = datetime.strptime(request.cutoff, '%H:%M')
    # e.g. 14:40-ended bar opens at 14:35, after the 14:30 decision window.
    label = (decision + timedelta(minutes=10)).strftime('%H:%M')
    return day.get(label), 'open'


def target_bar(day, target):
    return day.get('09:35' if target == 'OPEN' else '15:00' if target == 'CLOSE' else target), ('open' if target == 'OPEN' else 'close')


def build_samples(grouped, request, through):
    result = {target: [] for target in TARGETS}
    for day in sorted(grouped):
        following = next_session(day)
        if not following or following not in grouped:
            continue
        x = features(grouped[day], request.cutoff)
        entry, field = entry_bar(grouped[day], request)
        if x is None or entry is None or entry.amount <= 0:
            continue
        price = getattr(entry, field)
        for target in TARGETS:
            bar, field = target_bar(grouped[following], target)
            if bar is None or bar.ended_at >= through or bar.amount <= 0 or bar.high == bar.low:
                continue
            # Raw prices do not prove fills; exclude one-price proxy entry intervals.
            if request.mode == 'TAIL_ENTRY' and entry.high == entry.low:
                continue
            actual = getattr(bar, field) / price - 1 - request.cost_bps / 10000
            result[target].append({'signalDate': day.isoformat(), 'exitAt': bar.ended_at.isoformat(),
                                   'features': x, 'actualNetReturn': actual})
    return result


def predict(request, bars, now):
    cutoff = datetime.fromisoformat(f'{request.signal_date}T{request.cutoff}:00')
    usable = [bar for bar in bars if bar.ended_at <= cutoff]
    fingerprint = hashlib.sha256(json.dumps([b.model_dump(mode='json') for b in usable], sort_keys=True).encode()).hexdigest()
    grouped = group_bars(usable)
    current = features(grouped.get(request.signal_date, {}), request.cutoff)
    following = next_session(request.signal_date)
    base = {'modelVersion': VERSION, 'mode': request.mode, 'instrumentCode': request.instrument_code,
        'signalDate': request.signal_date.isoformat(), 'cutoff': request.cutoff,
        'generatedAt': now.isoformat(), 'dataThrough': cutoff.isoformat(), 'inputFingerprint': fingerprint,
        'targetDate': following.isoformat() if following else None,
        'referencePrice': grouped.get(request.signal_date, {}).get(request.cutoff).close if current else None,
        'costBps': request.cost_bps, 'costBasis': request.cost_basis, 'quantity': request.quantity,
        'evidenceKind': 'FORWARD' if cutoff <= now < cutoff + timedelta(minutes=5) or
            request.mode == 'AFTER_CLOSE_HOLDING' and now.date() == request.signal_date and now >= cutoff else 'RETROSPECTIVE',
        'entryRule': 'NEXT_5MIN_OPEN' if request.mode == 'TAIL_ENTRY' else 'EXISTING_POSITION_CLOSE_REFERENCE',
        'executionStatus': 'UNVERIFIED', 'status': 'DATA_UNAVAILABLE', 'targets': [],
        'warnings': ['价格代理不保证成交；未验证停牌、涨跌停队列与公司行为，暂不生成买入或卖出指令。']}
    if now < cutoff:
        return {**base, 'status': 'BEFORE_CUTOFF'}
    if following is None or next_session(request.signal_date - timedelta(days=1)) != request.signal_date:
        return {**base, 'status': 'CALENDAR_UNAVAILABLE'}
    if current is None:
        return {**base, 'warnings': base['warnings'] + ['决策时点前的完整 5 分钟行情不足；禁止用收盘日线替代。']}
    samples = build_samples(grouped, request, cutoff)
    for target, values in samples.items():
        result = forecast_target(values, current, cutoff.isoformat())
        if result['status'] == 'WATCH':
            result['costBasisReturn'] = (base['referencePrice'] * (1 + result['expectedNetReturn'])
                                         / request.cost_basis - 1) if request.cost_basis else None
        base['targets'].append({'target': target, **result})
    base['status'] = 'WATCH' if any(s['status'] == 'WATCH' for s in base['targets']) else 'INSUFFICIENT_DATA'
    return base


def settle(report, bars, now):
    grouped = group_bars([b for b in bars if b.ended_at <= now])
    from finscope_market_data.overnight.models import OvernightRequest
    request = OvernightRequest.model_validate(report['request'])
    target_date = next_session(request.signal_date)
    entry, field = entry_bar(grouped.get(request.signal_date, {}), request)
    result = {'status': 'PENDING', 'targets': [], 'executionStatus': 'UNVERIFIED', 'missingReasons': []}
    if entry is None or not target_date:
        result['missingReasons'].append('ENTRY_DATA_MISSING' if target_date else 'CALENDAR_UNAVAILABLE')
        return result
    price = getattr(entry, field)
    if request.mode == 'TAIL_ENTRY' and (entry.amount <= 0 or entry.high == entry.low):
        return {**result, 'status': 'ENTRY_UNVERIFIED', 'missingReasons': ['ENTRY_NOT_EXECUTABLE']}
    for target in TARGETS:
        bar, field = target_bar(grouped.get(target_date, {}), target)
        if bar is None or bar.amount <= 0:
            result['missingReasons'].append(f'{target}:DATA_MISSING_OR_NOT_DUE')
            continue
        if report.get('modelVersion') in ('overnight-local-v2', VERSION) and bar.high == bar.low:
            result['missingReasons'].append(f'{target}:EXIT_UNVERIFIED')
            continue
        net = getattr(bar, field) / price - 1 - request.cost_bps / 10000
        prediction = next((x for x in report['targets'] if x['target'] == target), {})
        probability = prediction.get('upProbability')
        result['targets'].append({'target': target, 'actualNetReturn': net, 'proxyEntryPrice': price,
            'brierScore': (probability - (net > 0)) ** 2 if probability is not None else None,
            'baselineBrier': (prediction['baselineProbability'] - (net > 0)) ** 2
                if prediction.get('baselineProbability') is not None else None})
    result['status'] = 'SETTLED' if len(result['targets']) == 4 else 'PARTIAL' if result['targets'] else 'PENDING'
    return result
