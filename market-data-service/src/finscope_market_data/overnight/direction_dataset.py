"""Next-session close/close direction, independent of trade costs and fill proxies."""
from collections import Counter
import hashlib
import json

import numpy as np

from finscope_market_data.forecast.trading_calendar import next_session, previous_session
from finscope_market_data.overnight.engine import expected_times, features, group_bars
from finscope_market_data.overnight.session_features import FEATURE_CODES, session_features
from finscope_market_data.overnight.joint_dataset import FEATURES as JOINT_FEATURES

PROTOCOL = 'overnight-close-direction-v1'
TARGET = 'NEXT_SESSION_CLOSE_VS_SIGNAL_CLOSE'
FEATURES = JOINT_FEATURES[:8] + FEATURE_CODES + (
    'LAST_5M_RETURN', 'LAST_30M_RETURN', 'LAST_60M_RETURN', 'AFTERNOON_RETURN',
    'RANGE_POSITION', 'DRAWDOWN_FROM_HIGH', 'REBOUND_FROM_LOW', 'DOWNSIDE_VOLATILITY',
    'LAST_30M_RELATIVE_AMOUNT', 'PREVIOUS_5D_RETURN', 'PREVIOUS_5D_VOLATILITY', 'GEM')


def direction_features(grouped, day, cutoff, code, *, complete_dates=None):
    local = session_features(grouped, day, cutoff, complete_dates=complete_dates)
    if local is None:
        return None
    times = expected_times(cutoff)
    bars = [grouped[day][stamp] for stamp in times]
    closes = np.array([bar.close for bar in bars])
    changes = np.diff(closes) / closes[:-1]
    high, low, last = max(bar.high for bar in bars), min(bar.low for bar in bars), closes[-1]
    previous, cursor = [], day
    for _ in range(6):
        cursor = previous_session(cursor)
        previous.append(grouped[cursor])
    historical_amount = np.mean([sum(bars[stamp].amount for stamp in times[-6:]) for bars in previous[:5]])
    if historical_amount <= 0:
        return None
    previous_returns = [previous[i]['15:00'].close / previous[i + 1]['15:00'].close - 1 for i in range(5)]
    return local + [float(last / closes[-offset - 1] - 1) for offset in (1, 6, 12)] + [
        float(last / grouped[day]['11:30'].close - 1), float((last - low) / (high - low)) if high > low else .5,
        float(last / high - 1), float(last / low - 1), float(np.sqrt(np.mean(np.minimum(changes, 0) ** 2))),
        float(np.log(np.clip(sum(bar.amount for bar in bars[-6:]) / historical_amount, .01, 100))),
        previous[0]['15:00'].close / previous[5]['15:00'].close - 1, float(np.std(previous_returns)),
        float(code.startswith('3'))]


def close_outcome(grouped, signal_day, through):
    following = next_session(signal_day)
    current = grouped.get(signal_day, {}).get('15:00')
    future = grouped.get(following, {}).get('15:00')
    if current is None or future is None or future.ended_at >= through:
        return {'status': 'PENDING', 'reason': 'CLOSE_DATA_MISSING_OR_NOT_DUE'}
    if current.amount <= 0 or future.amount <= 0:
        return {'status': 'UNVERIFIED', 'reason': 'CLOSE_WITHOUT_TRADING_AMOUNT'}
    # One-price bars remain valid price observations. They do not prove execution.
    actual = future.close / current.close - 1
    return {'status': 'SETTLED', 'actualReturn': actual, 'actualUp': actual > 0,
            'signalClose': current.close, 'targetClose': future.close, 'exitAt': future.ended_at.isoformat()}


def build_direction_panel(store, members, cutoff, through):
    rows, quality = [], Counter()
    for code in sorted(set(members)):
        grouped = group_bars(store.bars(code, through))
        complete = {day for day, bars in grouped.items() if features(bars, '15:00') is not None}
        for day in sorted(grouped):
            actual = close_outcome(grouped, day, through)
            if actual['status'] != 'SETTLED':
                quality[actual['reason']] += 1
                continue
            x = direction_features(grouped, day, cutoff, code, complete_dates=complete)
            if x is None:
                quality['MISSING_PAST_FEATURES'] += 1
                continue
            rows.append({'instrumentCode': code, 'signalDate': str(day), 'features': x,
                         'actualReturn': actual['actualReturn'], 'exitAt': actual['exitAt']})
    rows.sort(key=lambda row: (row['signalDate'], row['instrumentCode']))
    digest = hashlib.sha256(json.dumps(rows, sort_keys=True, separators=(',', ':'), allow_nan=False).encode()).hexdigest()
    return rows, {'fingerprint': digest, 'rows': len(rows), 'quality': dict(quality),
                  'symbolCount': len({row['instrumentCode'] for row in rows}),
                  'dayCount': len({row['signalDate'] for row in rows}), 'target': TARGET,
                  'priceBasis': 'RAW_CLOSE', 'corporateActionsVerified': False}
