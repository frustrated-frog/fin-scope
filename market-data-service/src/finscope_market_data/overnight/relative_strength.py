"""Aligned, pre-decision stock/index strength; gaps are never synthetic zeros."""
import math

from finscope_market_data.overnight.context_features import INDEX_CODES, context_time, stock_context
from finscope_market_data.overnight.engine import expected_times

PROTOCOL = 'overnight-relative-strength-v1'
WINDOWS = ('return', 'last30', 'last60', 'acceleration', 'drawdown')
FEATURES = tuple(f'RELATIVE_{index}_{window.upper()}' for index in ('CSI300', 'CSI1000', 'GEM') for window in WINDOWS)


def minute_state(grouped, day, cutoff):
    value = stock_context(grouped, day, cutoff)
    if value is None:
        return None
    times = expected_times(context_time(day, cutoff).strftime('%H:%M'))
    bars = grouped[day]
    if len(times) < 13:
        return None
    close = bars[times[-1]].close
    previous30 = bars[times[-7]].close / bars[times[-13]].close - 1
    value.update(last60=close / bars[times[-13]].close - 1,
                 acceleration=value['last30'] - previous30,
                 drawdown=close / max(bars[t].high for t in times) - 1)
    return value if all(math.isfinite(value[key]) for key in WINDOWS) else None


def relative_features(code, snapshot):
    if (snapshot.get('relativeStrength') or {}).get('protocol') != PROTOCOL:
        return None
    own = snapshot['pool'].get(code)
    indices = snapshot.get('indexMinutes', {})
    rows = [own, *(indices.get(code) for code in INDEX_CODES)]
    observed = context_time(snapshot['signalDate'], snapshot['cutoff']).isoformat()
    decision = f"{snapshot['signalDate']}T{snapshot['cutoff']}:00"
    for row in rows:
        if (not row or row.get('observedAt') != observed
                or not observed <= row.get('receivedAt', '') <= snapshot['receivedAt'] <= decision
                or any(isinstance(row.get(key), bool) or not isinstance(row.get(key), (float, int))
                       or not math.isfinite(row[key]) for key in WINDOWS)):
            return None
    return [own[key] - indices[index][key] for index in INDEX_CODES for key in WINDOWS]


def coverage(snapshot):
    indices = snapshot.get('indexMinutes', {})
    return {'protocol': PROTOCOL, 'expectedIndices': len(INDEX_CODES), 'capturedIndices': len(indices),
            'pairedSymbols': sum(relative_features(code, snapshot) is not None for code in snapshot['pool']),
            'scope': '仅使用判断前收到、结束时点一致的五分钟数据；缺失时不生成相对强弱输入'}
