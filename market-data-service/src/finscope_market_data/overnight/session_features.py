"""Research candidate: distinguish past overnight moves from intraday moves.

Inspired by Lou, Polk & Skouras (2019). This is not enabled in production
until a paired chronological evaluation supports replacing the incumbent.
"""
import math

from finscope_market_data.forecast.trading_calendar import previous_session
from finscope_market_data.overnight.engine import expected_times, features

FEATURE_CODES = ('OPEN_GAP', 'PREVIOUS_OVERNIGHT', 'PREVIOUS_INTRADAY',
                 'MEAN_OVERNIGHT_5', 'MEAN_INTRADAY_5', 'RELATIVE_AMOUNT_5')


def session_features(grouped, day, cutoff, *, complete_dates=None):
    current = features(grouped.get(day, {}), cutoff)
    if current is None:
        return None
    previous = []
    cursor = day
    for _ in range(6):
        cursor = previous_session(cursor)
        complete = cursor in complete_dates if complete_dates is not None else features(grouped.get(cursor, {}), '15:00') is not None
        if cursor is None or not complete:
            return None
        previous.append(grouped[cursor])
    overnight = [previous[i]['09:35'].open / previous[i + 1]['15:00'].close - 1 for i in range(5)]
    intraday = [bars['15:00'].close / bars['09:35'].open - 1 for bars in previous[:5]]
    times = expected_times(cutoff)
    historical_amount = sum(sum(bars[stamp].amount for stamp in times) for bars in previous[:5]) / 5
    if historical_amount <= 0:
        return None
    amount_ratio = sum(grouped[day][stamp].amount for stamp in times) / historical_amount
    return current + [grouped[day]['09:35'].open / previous[0]['15:00'].close - 1,
        overnight[0], intraday[0], sum(overnight) / 5, sum(intraday) / 5,
        math.log(max(.01, min(100, amount_ratio)))]
