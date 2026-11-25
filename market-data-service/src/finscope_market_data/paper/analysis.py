"""Point-in-time development replay and independent cross-sectional factor diagnostics."""

from collections import Counter, defaultdict, deque
from datetime import datetime, timedelta
from time import monotonic

import numpy as np

from finscope_market_data.forecast.trading_calendar import consecutive_observed_sessions
from finscope_market_data.overnight.engine import expected_times
from .engine import advance, new_book
from .python_runtime import PythonStrategy


def frames(panel):
    series = {code: deque(maxlen=49) for code in panel}
    timeline = defaultdict(dict)
    for code, bars in panel.items():
        for bar in bars:
            timeline[bar.observed_at][code] = bar
    for end, fresh in sorted(timeline.items()):
        for code, bar in fresh.items():
            series[code].append(bar)
        yield end, {code: list(series[code]) for code in fresh}


def coverage(panel):
    sessions = {bar.observed_at.date() for rows in panel.values() for bar in rows}
    clocks = set(expected_times("15:00"))
    report = {}
    for code, rows in panel.items():
        days = defaultdict(set)
        for bar in rows:
            days[bar.observed_at.date()].add(bar.observed_at.strftime("%H:%M"))
        report[code] = dict(
            bars=len(rows),
            days=len(days),
            completeDays=sum(clocks <= times for times in days.values()),
            poolDays=len(sessions),
            first=str(min(days)) if days else None,
            last=str(max(days)) if days else None,
            measuredVolumeBars=sum(
                not getattr(b, "estimated_volume", False) and b.volume is not None
                for b in rows
            ),
        )
    return report


def ranks(values):
    """Average ranks including ties; NaN represents a missing instrument."""
    result = np.empty_like(values)
    for col in range(values.shape[1]):
        x = values[:, col : col + 1]
        result[:, col] = np.where(
            np.isfinite(x[:, 0]),
            0.5 + (values < x).sum(axis=1) + 0.5 * (values == x).sum(axis=1),
            np.nan,
        )
    return result


def correlations(x, y):
    mask = np.isfinite(x) & np.isfinite(y)
    n = mask.sum(axis=1)
    x = np.where(mask, x, 0.0)
    y = np.where(mask, y, 0.0)
    a = np.where(mask, x - (x.sum(axis=1) / np.maximum(n, 1))[:, None], 0.0)
    b = np.where(mask, y - (y.sum(axis=1) / np.maximum(n, 1))[:, None], 0.0)
    denom = np.sqrt((a * a).sum(axis=1) * (b * b).sum(axis=1))
    return np.divide(
        (a * b).sum(axis=1),
        denom,
        out=np.full(len(n), np.nan),
        where=(n >= 3) & (denom > 0),
    )


def finite_mean(x):
    sample = x[np.isfinite(x)]
    return float(sample.mean()) if len(sample) else None


def factor_report(panel, observations):
    if not observations:
        return {}
    codes = list(panel)
    times = sorted({b.observed_at for rows in panel.values() for b in rows})
    dates = sorted({t.date() for t in times})
    day_index = {day: i for i, day in enumerate(dates)}
    clock = expected_times("15:00")
    clock_index = {v: i for i, v in enumerate(clock)}
    index = {t: i for i, t in enumerate(times)}
    prices = np.full((len(times), len(codes)), np.nan)
    matrices = {}
    col = {code: i for i, code in enumerate(codes)}
    for c, rows in panel.items():
        for b in rows:
            prices[index[b.observed_at], col[c]] = b.close
    for end, factors in observations:
        for name, scores in factors.items():
            if name not in matrices:
                matrices[name] = np.full_like(prices, np.nan)
            for c, value in scores.items():
                matrices[name][index[end], col[c]] = value
    reports = {name: {"horizons": {}, "correlations": {}} for name in matrices}
    for name, x in matrices.items():
        for other, y in matrices.items():
            if other != name:
                reports[name]["correlations"][other] = finite_mean(correlations(x, y))
    # Horizon means exact trading slots, never the next available quote after a gap.
    for label, slots in (("5m", 1), ("30m", 6), ("1d", 48), ("3d", 144), ("5d", 240)):
        targets = np.full_like(prices, np.nan)
        for i, t in enumerate(times):
            pos = clock_index.get(t.strftime("%H:%M"))
            if pos is None:
                continue
            shift, end_slot = divmod(pos + slots, len(clock))
            d = day_index[t.date()]
            if d + shift >= len(dates) or not all(
                consecutive_observed_sessions(dates[j], dates[j + 1])
                for j in range(d, d + shift)
            ):
                continue
            at = datetime.combine(
                dates[d + shift],
                datetime.strptime(clock[end_slot], "%H:%M").time(),
                tzinfo=t.tzinfo,
            )
            target = index.get(at)
            if target is not None:
                targets[i] = prices[target] / prices[i] - 1
        for name, x in matrices.items():
            mask = np.isfinite(x) & np.isfinite(targets)
            rx, ry = (
                ranks(np.where(mask, x, np.nan)),
                ranks(np.where(mask, targets, np.nan)),
            )
            ic = correlations(x, targets)
            ric = correlations(rx, ry)
            valid = np.isfinite(ric)
            n = mask.sum(axis=1)
            group = np.floor((rx - 1) * 5 / np.maximum(n, 1)[:, None])
            groups = []
            for g in range(5):
                select = (group == g) & (n >= 5)[:, None]
                count = select.sum(axis=1)
                daily = np.divide(
                    np.where(select, targets, 0.0).sum(axis=1),
                    count,
                    out=np.full(len(n), np.nan),
                    where=count > 0,
                )
                groups.append(finite_mean(daily))
            by_day = defaultdict(list)
            for idx in np.flatnonzero(valid):
                by_day[times[idx].date()].append(float(ric[idx]))
            daily_ic = [float(np.mean(vals)) for vals in by_day.values()]
            std = np.std(daily_ic, ddof=1) if len(daily_ic) > 1 else 0.0
            reports[name]["horizons"][label] = dict(
                observations=int(valid.sum()),
                pairs=int(mask.sum()),
                ic=finite_mean(ic),
                rankIC=finite_mean(ric),
                dailyRankICIR=float(np.mean(daily_ic) / std) if std > 1e-12 else None,
                positiveFraction=float((ric[valid] > 0).mean())
                if valid.any()
                else None,
                recent20DayRankIC=float(np.mean(daily_ic[-20:])) if daily_ic else None,
                groups=groups,
                spread=groups[-1] - groups[0]
                if groups[-1] is not None and groups[0] is not None
                else None,
            )
    return reports


def evaluate(panel, code, image, capital, *, factors=True):
    """Only callers holding a development dataset may expose this result to the agent."""
    book = new_book(capital)
    peak = capital
    drawdown = gross = 0.0
    reasons = Counter()
    observations = []
    deadline = monotonic() + 480
    with PythonStrategy(code, image, collect_factors=factors) as runtime:
        for end, history in frames(panel):
            if monotonic() > deadline:
                raise ValueError("开发段试跑超时")
            point = advance(
                book,
                None,
                history,
                end,
                end + timedelta(seconds=3),
                capital,
                universe=panel,
                decide=runtime.decide,
            )
            peak = max(peak, book["equity"])
            drawdown = max(drawdown, 1 - book["equity"] / peak)
            for event in point["events"]:
                if event["action"] == "成交":
                    gross += event["quantity"] * event["price"]
                elif event["action"] == "未成交":
                    reasons[event["reason"]] += 1
            if runtime.factor_values:
                observations.append((end, runtime.factor_values))
    return dict(
        netReturn=book["equity"] / capital - 1,
        benchmarkReturn=point["benchmark"] / capital - 1,
        maxDrawdown=drawdown,
        fills=book["fills"],
        fees=book["fees"],
        feeRate=book["fees"] / capital,
        tradedNotionalRate=gross / capital,
        unfilled=dict(reasons),
        factors=factor_report(panel, observations),
    )
