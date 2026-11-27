"""Completed Shanghai trading intervals, shared by model fitting, replay and live inference."""

import asyncio
from collections import deque
from copy import deepcopy
from datetime import timedelta

from finscope_market_data.forecast.trading_calendar import consecutive_observed_sessions
from finscope_market_data.overnight.engine import expected_times

from .actions import read_actions

SLOTS = {int(t[:2]) * 60 + int(t[3:]): i for i, t in enumerate(expected_times("15:00"))}
FEATURES = ("return5m", "return15m", "return60m", "return1d", "trend20d", "trend60d")


class Timeframes:
    def __init__(self):
        self.day = None
        self.opens = {}
        self.closes = deque(maxlen=61)
        self.features = {}

    def update(self, at, bar):
        slot = SLOTS.get(at.hour * 60 + at.minute)
        if slot is None:
            return {}
        day = at.date()
        if self.day != day or getattr(bar, "actions", None) and not getattr(bar, "windowClipped", False):
            if self.day is not None and (not consecutive_observed_sessions(self.day, day)
                                         or len(self.opens) != 48 or getattr(bar, "actions", None)):
                self.closes.clear()
                self.features.clear()
            self.features = {k: v for k, v in self.features.items() if k.endswith("d")}
            self.day = day
            self.opens = {}
        self.opens[slot] = bar.open
        self.features["return5m"] = bar.close / bar.open - 1
        for length, key in ((3, "return15m"), (12, "return60m"), (48, "return1d")):
            if (slot + 1) % length == 0:
                start = slot + 1 - length
                if all(i in self.opens for i in range(start, slot + 1)):
                    self.features[key] = bar.close / self.opens[start] - 1
                else:
                    self.features.pop(key, None)
        if slot == 47 and len(self.opens) == 48:
            self.closes.append(bar.close)
            for days in (20, 60):
                if len(self.closes) > days:
                    self.features[f"trend{days}d"] = bar.close / self.closes[-days-1] - 1
        return self.features.copy()


class ForwardFeatures:
    """Cache immutable history before the oldest live bar. Today's quotes are evaluated as observed."""
    def __init__(self, lab):
        self.lab = lab
        self.cache = {}
        self.day = None

    async def attach(self, code, bars, end):
        if self.day != end.date():
            self.cache.clear()
            self.day = end.date()
        # Warm from the previous completed session, then append today's observed bars.
        today = [b for b in bars if b.observed_at.date() == end.date()]
        key = (code, end.date(), bars[0].observed_at.date())
        if key not in self.cache:
            task = asyncio.create_task(self.seed(code, bars, end))
            self.cache[key] = task
        try:
            state, prior = await self.cache[key]
        except Exception:
            self.cache.pop(key, None)
            raise
        state = deepcopy(state)
        result = prior.copy()
        for bar in today:
            features = state.update(bar.observed_at, bar)
            result.append(bar.model_copy(update={"multiTimeframe": features}))
        return result[-49:]

    async def seed(self, code, bars, end):
        cutoff = end.replace(hour=0, minute=0, second=0, microsecond=0, tzinfo=None) - timedelta(microseconds=1)
        state = Timeframes()
        prior = []
        # forward_actions already clipped the live input at the last known ex-date.
        if getattr(bars[-1], "windowClipped", False):
            return state, prior
        archived = await asyncio.to_thread(self.lab.datasets.bars, code, cutoff)
        rows = {b.ended_at: b for b in archived[-(63 * 48):]}
        for b in bars:
            if b.observed_at.date() < end.date():
                rows[b.observed_at.replace(tzinfo=None)] = b
        if not rows:
            return state, prior
        events = await read_actions(self.lab.router(), self.lab.limit, code, min(rows).date(), end.date())
        if events is None:
            raise ValueError("等待核验多周期历史窗口")
        boundary = max((event["ex_date"] for event in events), default="")
        for at, bar in sorted(rows.items()):
            if str(at.date()) >= boundary:
                features = state.update(at, bar)
                if hasattr(bar, "observed_at"):
                    prior.append(bar.model_copy(update={"multiTimeframe": features}))
        return state, prior[-49:]
