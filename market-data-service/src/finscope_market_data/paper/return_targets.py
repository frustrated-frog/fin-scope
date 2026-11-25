"""Observable A-share T+1 gross price targets, aligned to the engine's earliest executable bar."""

from collections import defaultdict
from datetime import timedelta

import numpy as np

from finscope_market_data.forecast.trading_calendar import consecutive_observed_sessions
from finscope_market_data.overnight.engine import expected_times

FIXED_SLOTS = {"5m": 1, "30m": 6, "1d": 48, "3d": 144, "5d": 240}
TRADE_TARGETS = {
    "tail_next_open": "14:45 信号入场至次日开盘",
    "tail_next_close": "14:45 信号入场至次日收盘",
    "intraday_first_sell": "盘中入场至次日首个可卖区间",
    "next_day_mfe": "盘中入场至次日最高价",
    "next_day_mae": "盘中入场至次日最低价",
}


def selected_targets(names):
    registry = {
        **{
            name: f"信号收盘起 {slots} 个五分钟交易区间"
            for name, slots in FIXED_SLOTS.items()
        },
        **TRADE_TARGETS,
    }
    if names is None:
        return registry
    if not names or any(name not in registry for name in names):
        raise ValueError("请选择已登记的收益目标：" + "、".join(registry))
    return {name: registry[name] for name in names}


def has_capacity(bar, lot):
    return (
        bar is not None
        and not getattr(bar, "estimated_volume", False)
        and bar.volume is not None
        and bar.volume * 0.05 >= lot
        and bar.high > bar.low
    )


def trading_targets(panel, times, codes):
    clock = expected_times("15:00")
    idx = {t: i for i, t in enumerate(times)}
    output = {k: np.full((len(times), len(codes)), np.nan) for k in TRADE_TARGETS}
    for col, code in enumerate(codes):
        lot = 200 if code.startswith(("688", "689")) else 100
        days = defaultdict(dict)
        for b in panel[code]:
            days[b.observed_at.date()][b.observed_at.strftime("%H:%M")] = b
        ordered = sorted(days)
        for day, following in zip(ordered, ordered[1:]):
            if not consecutive_observed_sessions(day, following):
                continue
            today, next_bars = days[day], days[following]
            if any(getattr(b, "actions", []) for b in next_bars.values()):
                continue
            first = None
            for hhmm in clock:
                bar = next_bars.get(hhmm)
                if (
                    bar is None
                    or getattr(bar, "estimated_volume", False)
                    or bar.volume is None
                ):
                    break
                if has_capacity(bar, lot):
                    first = bar
                    break
            open_bar, close_bar = next_bars.get(clock[0]), next_bars.get(clock[-1])
            complete = all(t in next_bars for t in clock)
            high = max(b.high for b in next_bars.values()) if complete else None
            low = min(b.low for b in next_bars.values()) if complete else None
            for hhmm, signal in today.items():
                # Decision at bar end + 3s misses the next bar's open; the following open is eligible.
                when = signal.observed_at + timedelta(minutes=10)
                entry = today.get(when.strftime("%H:%M"))
                if not has_capacity(entry, lot) or getattr(entry, "actions", []):
                    continue
                i = idx[signal.observed_at]
                if first:
                    output["intraday_first_sell"][i, col] = first.open / entry.open - 1
                if hhmm == "14:45":
                    if has_capacity(open_bar, lot):
                        output["tail_next_open"][i, col] = (
                            open_bar.open / entry.open - 1
                        )
                    if (
                        close_bar
                        and close_bar.volume
                        and not getattr(close_bar, "estimated_volume", False)
                    ):
                        output["tail_next_close"][i, col] = (
                            close_bar.close / entry.open - 1
                        )
                if complete:
                    output["next_day_mfe"][i, col] = high / entry.open - 1
                    output["next_day_mae"][i, col] = low / entry.open - 1
    return output.items()
