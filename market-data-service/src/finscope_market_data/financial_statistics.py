"""Descriptive research on completed daily closes. Rates and portfolio weights are fractions."""
from datetime import date, datetime, timedelta
from hashlib import sha256
from math import expm1, isfinite, log1p, sqrt
from zoneinfo import ZoneInfo

import numpy as np
from pydantic import BaseModel, ConfigDict, Field, model_validator

from .forecast.trading_calendar import consecutive_observed_sessions
from .models import DataCapability, StockSymbol

BENCHMARK = "SH:000300"
TRADING_YEAR = 252
SHANGHAI = ZoneInfo("Asia/Shanghai")


class StatisticsQuery(BaseModel):
    model_config = ConfigDict(extra="forbid", allow_inf_nan=False)
    asset: str = Field(pattern=r"^(SH|SZ):[0-9]{6}$")
    peer: str = Field(pattern=r"^(SH|SZ):[0-9]{6}$")
    asOf: date
    days: int = Field(ge=30, le=1095)
    window: int = Field(ge=5, le=120)
    annualRiskFree: float = Field(default=0., ge=0., le=.25)

    @model_validator(mode="after")
    def check_design(self):
        if self.asset == self.peer:
            raise ValueError("请为组合选择两种不同资产")
        if self.asOf > datetime.now(SHANGHAI).date():
            raise ValueError("截止日不能晚于今天")
        return self


def daily_sample(bars, start, end):
    # Do not bridge a suspension, missing price, duplicate date or adjustment change.
    observed = {}
    for bar in bars:
        day = date.fromisoformat(bar.trade_date)
        if day <= end:
            observed[day] = None if day in observed else bar
    dates = sorted(observed)
    series, adjustments = {}, set()
    for prior, day in zip(dates, dates[1:]):
        if day < start or not consecutive_observed_sessions(prior, day):
            continue
        left, right = observed[prior], observed[day]
        if not all(b and b.is_complete and isfinite(b.close) and b.close > 0 for b in (left, right)):
            continue
        if left.adjustment != right.adjustment:
            continue
        series[day] = (prior, right.close / left.close - 1)
        adjustments.add(right.adjustment)
    return series, sorted(adjustments)


def pearson(a, b):
    if len(a) < 3 or np.ptp(a) < 1e-14 or np.ptp(b) < 1e-14:
        return None
    return float(np.corrcoef(a, b)[0, 1])


def mean_interval(series, mean):
    """Approximate HAC interval. Products spanning an unobserved session are omitted."""
    n = len(series)
    if n < 30:
        return None
    lags = max(1, int(4*(n/100)**(2/9)))
    centered = {day: value-mean for day, (_,value) in series.items()}
    variance = sum(value**2 for value in centered.values())
    for day,value in centered.items():
        previous = day
        for lag in range(1,lags+1):
            previous = series[previous][0]
            if previous not in series:
                break
            variance += 2*(1-lag/(lags+1))*value*centered[previous]
    margin = 1.96*sqrt(max(variance,0.))/n
    return dict(low=mean-margin, high=mean+margin, lags=lags)


def distribution(series):
    r = np.array([v for _, v in series.values()])
    if not len(r):
        return None
    center = r - r.mean()
    scale = float(r.std())
    adjacent = [(series[prior][1], value) for prior, value in series.values() if prior in series]
    a, b = np.asarray(adjacent).T if adjacent else ([], [])
    counts = np.histogram(r, bins=[-np.inf, -.03, -.01, 0, .01, .03, np.inf])[0]
    return dict(n=len(r), mean=float(r.mean()), logMean=float(np.log1p(r).mean()),
                compound=expm1(float(np.log1p(r).sum())), volatility=float(r.std(ddof=1)*sqrt(TRADING_YEAR)) if len(r)>1 else None,
                skew=float(np.mean((center/scale)**3)) if scale>1e-14 and len(r)>=3 else None,
                excessKurtosis=float(np.mean((center/scale)**4)-3) if scale>1e-14 and len(r)>=4 else None,
                percentiles=np.quantile(r,[.05,.5,.95]).tolist(), bins=counts.tolist(),
                meanInterval=mean_interval(series,float(r.mean())), lagOne=pearson(a,b), absoluteLagOne=pearson(np.abs(a),np.abs(b)), lagPairs=len(adjacent))


def regression(asset, market, annual_rf):
    common = sorted(asset.keys() & market.keys())
    result = dict(n=len(common), beta=None, annualAlpha=None, rSquared=None)
    if len(common) < 3:
        return result
    rf = expm1(log1p(annual_rf)/TRADING_YEAR)
    x = np.array([market[d][1]-rf for d in common])
    y = np.array([asset[d][1]-rf for d in common])
    if np.ptp(x) < 1e-14:
        return result
    beta = float(np.cov(x,y)[0,1]/np.var(x,ddof=1))
    rho = pearson(x,y)
    return dict(n=len(common), beta=beta, annualAlpha=float((y.mean()-beta*x.mean())*TRADING_YEAR),
                rSquared=rho**2 if rho is not None else None)


def pair_analysis(left, right, window):
    dates = sorted(left.keys() & right.keys())
    a, b = [np.array([s[d][1] for d in dates]) for s in (left,right)]
    rolling, streak = [], 0
    for i,day in enumerate(dates):
        streak = streak+1 if i and dates[i-1] == left[day][0] else 1
        start = i+1-window
        value = pearson(a[start:i+1],b[start:i+1]) if streak>=window else None
        rolling.append(dict(day=str(day),value=value))
    result = dict(n=len(dates), correlation=pearson(a,b), rolling=rolling, frontier=None)
    if len(dates) < 3:
        return result
    cov = np.cov(a,b)
    spread = float(np.var(a-b,ddof=1))
    # Equal variances along the entire segment: the higher-mean endpoint dominates.
    low = float(np.clip((cov[1,1]-cov[0,1])/spread,0,1)) if spread>1e-18 else float(a.mean() >= b.mean())
    high = low if abs(a.mean()-b.mean())<1e-14 else float(a.mean() > b.mean())
    def portfolio(w):
        weights = np.array([w,1-w])
        return dict(weight=float(w), annualReturn=float((w*a.mean()+(1-w)*b.mean())*TRADING_YEAR),
                    annualVol=sqrt(max(0.,float(weights@cov@weights))*TRADING_YEAR))
    result["frontier"] = dict(minimum=portfolio(low), equal=portfolio(.5),
                              points=[portfolio(w) for w in np.linspace(low,high,21 if low!=high else 1)])
    return result


def analyze(store, query):
    end = query.asOf
    start = end-timedelta(days=query.days-1)
    samples, sources = {}, {}
    for key in dict.fromkeys((query.asset,query.peer,BENCHMARK)):
        exchange, code = key.split(":")
        symbol = StockSymbol(market=exchange,code=code)
        envelope = store.load(DataCapability.DAILY_BARS,symbol) if store else None
        bars = envelope.data if envelope and envelope.data else []
        sequence, basis = daily_sample(bars,start,end)
        samples[key] = sequence
        digest = sha256()
        for day,(prior,value) in sequence.items():
            digest.update(f"{prior}/{day}/{value.hex()};".encode())
        sources[key] = dict(n=len(sequence), first=str(min(sequence)) if sequence else None,
                            last=str(max(sequence)) if sequence else None, adjustment=basis,
                            provider=envelope.source_code if envelope else None, digest=digest.hexdigest())
    left, right, market = (samples[k] for k in (query.asset,query.peer,BENCHMARK))
    if not left:
        raise ValueError("所选资产在区间内没有可核验的连续收盘收益，请调整日期或标的")
    common = sorted(left.keys() & market.keys())
    regimes = []
    for name,sign in (("上涨",1),("下跌",-1),("持平",0)):
        values = [left[d][1] for d in common if np.sign(market[d][1]) == sign]
        regimes.append(dict(label=name,n=len(values),mean=float(np.mean(values)) if values else None))
    return dict(start=str(start), end=str(end), benchmark=BENCHMARK, sources=sources, distribution=distribution(left),
                pair=pair_analysis(left,right,query.window), pricing=regression(left,market,query.annualRiskFree),
                regimes=regimes, daily=[dict(day=str(d),prior=str(p),value=v) for d,(p,v) in left.items()])
