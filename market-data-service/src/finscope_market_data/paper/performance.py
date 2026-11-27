"""Comparable net performance from the last observed NAV on each trading date."""
from datetime import date
from math import sqrt
from statistics import fmean, stdev

from finscope_market_data.forecast.trading_calendar import consecutive_observed_sessions


def report(points, capital, fees):
    closes = {}
    for point in points:
        closes[point["at"][:10]] = point
    if not closes:
        return None
    returns = []
    previous = peak = capital
    prior_day = None
    max_drawdown = 0.
    for day, point in sorted(closes.items()):
        equity = point["equity"]
        current = date.fromisoformat(day)
        if prior_day and previous > 0 and consecutive_observed_sessions(prior_day, current):
            returns.append(equity / previous - 1)
        peak = max(peak, equity)
        max_drawdown = max(max_drawdown, 1 - equity / peak)
        previous, prior_day = equity, current
    last = closes[max(closes)]
    volatility = stdev(returns) if len(returns) >= 2 else None
    return dict(days=len(closes), returnDays=len(returns), netReturn=last["equity"]/capital-1,
                excess=(last["equity"]-last["benchmark"])/capital, maxDrawdown=max_drawdown,
                feeRate=fees/capital, dailyVol=volatility,
                sharpe=fmean(returns)*sqrt(252)/volatility if len(returns) >= 60 and volatility else None)
