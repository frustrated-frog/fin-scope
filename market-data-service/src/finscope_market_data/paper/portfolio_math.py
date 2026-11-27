"""Portable long-only portfolio math frozen into the classic strategy source."""
from math import sqrt


def aligned_covariance(chosen, histories, window):
    returns = []
    for code in chosen:
        bars = histories[code][-1].get("dailyWindow", [])
        returns.append({(a["at"][:10], b["at"][:10]): b["close"]/a["close"]-1 for a,b in zip(bars,bars[1:])})
    common = sorted(set.intersection(*(set(series) for series in returns)))[-window:]
    if len(common) < window:
        return None, common
    centered = []
    for series in returns:
        values = [series[d] for d in common]
        avg = sum(values)/len(values)
        centered.append([v-avg for v in values])
    sample = [[sum(a*b for a,b in zip(x,y))/(window-1) for y in centered] for x in centered]
    if any(sample[i][i] <= 1e-14 for i in range(len(sample))):
        return None, common
    # Shrink the sample covariance toward its diagonal before optimization.
    return [[v if i == j else .9*v for j,v in enumerate(row)] for i,row in enumerate(sample)], common


def capped_simplex(values, total, cap):
    """Euclidean projection to positive weights with a fixed sum and per-stock cap."""
    lo, hi = min(values)-cap, max(values)
    for _ in range(48):
        mid = (lo+hi)/2
        if sum(min(cap,max(0.,v-mid)) for v in values) > total:
            lo = mid
        else:
            hi = mid
    return [min(cap,max(0.,v-hi)) for v in values]


def optimized_weights(cov, method, total, cap):
    n = len(cov)
    total = min(total,n*cap)
    x = [total/n]*n
    if method == "MIN_VARIANCE":
        step = 1/max(sum(abs(v) for v in row) for row in cov)
        for _ in range(400):
            gradient = [sum(v*w for v,w in zip(row,x)) for row in cov]
            nxt = capped_simplex([w-step*g for w,g in zip(x,gradient)],total,cap)
            if max(abs(a-b) for a,b in zip(x,nxt)) < 1e-9:
                return nxt
            x = nxt
        return x
    # Coordinate descent solves x_i (Sigma x)_i = 1. Then scale the risky
    # portfolio and redistribute overweight names up to the configured caps.
    x = [1/sqrt(cov[i][i]) for i in range(n)]
    for _ in range(200):
        previous = x.copy()
        for i,row in enumerate(cov):
            other = sum(v*w for j,(v,w) in enumerate(zip(row,x)) if j != i)
            x[i] = 2/(sqrt(other*other+4*row[i])+other)
        if max(abs(a/b-1) for a,b in zip(x,previous)) < 1e-9:
            break
    lo, hi = 0., cap/min(x)
    for _ in range(48):
        mid = (lo+hi)/2
        if sum(min(cap,mid*v) for v in x) > total:
            hi = mid
        else:
            lo = mid
    return [min(cap,lo*v) for v in x]


def portfolio_risk(chosen, histories, cfg, targets):
    cov, dates = aligned_covariance(chosen,histories,cfg.get("riskWindow",10))
    if cov is None:
        return targets, {"days":len(dates), "unavailable":True}
    w = [targets.get(c,0.) for c in chosen]
    if cfg["allocation"] in ("MIN_VARIANCE","RISK_PARITY"):
        w = optimized_weights(cov,cfg["allocation"],cfg["exposure"],cfg["maxWeight"])
    marginal = [sum(v*b for v,b in zip(row,w)) for row in cov]
    variance = sum(a*b for a,b in zip(w,marginal))
    annual = sqrt(max(0.,252*variance))
    target = cfg.get("targetVol",0.)
    scale = min(1.,target/annual) if target and annual else 1.
    weights = {c:v*scale for c,v in zip(chosen,w)}
    risk = dict(days=len(dates), through=dates[-1][1], annualVol=annual*scale, scale=scale,
                contributions={c:a*b/variance for c,a,b in zip(chosen,w,marginal)} if variance else {})
    pairs = [(i,j,cov[i][j]/sqrt(cov[i][i]*cov[j][j])) for i in range(len(w)) for j in range(i)]
    if pairs:
        i,j,rho = max(pairs,key=lambda item:abs(item[2]))
        risk["strongestPair"] = dict(left=chosen[i], right=chosen[j], correlation=rho)
    return weights,risk
