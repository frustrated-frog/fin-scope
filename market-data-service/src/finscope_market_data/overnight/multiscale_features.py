"""Compact Qlib-inspired history, aligned to the actual intraday decision price.

Daily prices come exclusively from completed preceding sessions. Amount compares
the same intraday window, never today's partial amount against full-day turnover.
"""
import numpy as np

from finscope_market_data.forecast.trading_calendar import previous_session
from finscope_market_data.overnight.engine import expected_times, features

WINDOWS = (5, 10, 20)
FEATURES = tuple(f'{name}_{window}' for window in WINDOWS for name in (
    'HISTORY_RETURN', 'HISTORY_MA_DISTANCE', 'HISTORY_RETURN_VOLATILITY',
    'HISTORY_UP_SHARE', 'HISTORY_RANGE_POSITION', 'HISTORY_RELATIVE_AMOUNT',
    'HISTORY_RETURN_AMOUNT_CORRELATION', 'HISTORY_TREND_STRENGTH'))


def multiscale_features(grouped, day, cutoff, *, complete_dates=None):
    current = grouped.get(day, {})
    if features(current, cutoff) is None:
        return None
    prior, cursor = [], day
    for _ in range(max(WINDOWS) + 1):
        cursor = previous_session(cursor)
        bars = grouped.get(cursor, {})
        complete = cursor in complete_dates if complete_dates is not None else features(bars, '15:00') is not None
        if not complete:
            return None
        prior.append(bars)
    times = expected_times(cutoff)
    prices = np.array([bars['15:00'].close for bars in prior])
    amounts = np.array([sum(bars[t].amount for t in times) for bars in prior])
    if (not np.all(np.isfinite(prices)) or not np.all(np.isfinite(amounts))
            or np.any(prices <= 0) or np.any(amounts <= 0)):
        return None
    returns = prices[:-1] / prices[1:] - 1
    amount_changes = np.log(amounts[:-1] / amounts[1:])
    decision_price = current[cutoff].close
    decision_amount = sum(current[t].amount for t in times)
    result = []
    for window in WINDOWS:
        changes = returns[:window]
        flow = amount_changes[:window]
        high = max(bar.high for bars in prior[:window] for bar in bars.values())
        low = min(bar.low for bars in prior[:window] for bar in bars.values())
        correlation = float(np.corrcoef(changes, flow)[0, 1]) if min(changes.std(), flow.std()) > 1e-12 else 0.
        total_move = float(np.abs(changes).sum())
        result.extend([
            prices[0] / prices[window] - 1,
            decision_price / prices[:window].mean() - 1,
            float(changes.std()), float(np.mean(changes > 0)),
            (decision_price - low) / (high - low) if high > low else .5,
            float(np.log(decision_amount / amounts[:window].mean())), correlation,
            float(changes.sum() / total_move) if total_move > 1e-12 else 0.])
    return [float(value) for value in result] if np.all(np.isfinite(result)) else None
