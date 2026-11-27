"""Latest observed net performance, with risk statistics calculated only at market close."""
from datetime import date
from math import sqrt
from statistics import fmean, stdev

from finscope_market_data.forecast.trading_calendar import consecutive_observed_sessions


def report(points, capital, fees, costs=None):
    observed = {}
    for point in points:
        observed[point["at"][:10]] = point
    if not observed:
        return None
    closes = {day:p for day,p in observed.items() if p["at"][11:16] == "15:00"}
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
    last = observed[max(observed)]
    volatility = stdev(returns) if len(returns) >= 2 else None
    mean_equity = fmean(point["equity"] for point in observed.values())
    turnover = costs["notional"]/mean_equity if costs and mean_equity > 0 else None
    return dict(days=len(observed), closeDays=len(closes), asOf=last["at"], returnDays=len(returns), netReturn=last["equity"]/capital-1, turnover=turnover,
                excess=(last["equity"]-last["benchmark"])/capital, maxDrawdown=max_drawdown if closes else None,
                feeRate=fees/capital, dailyVol=volatility,
                sharpe=fmean(returns)*sqrt(252)/volatility if len(returns) >= 60 and volatility else None)
