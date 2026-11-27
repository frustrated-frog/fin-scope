"""Completed Shanghai trading intervals, shared by model fitting, replay and live inference."""

import asyncio
from collections import deque
from copy import deepcopy
from datetime import timedelta

from finscope_market_data.forecast.trading_calendar import consecutive_observed_sessions
from finscope_market_data.overnight.engine import expected_times
from .engine import SHANGHAI


SLOTS = {int(t[:2]) * 60 + int(t[3:]): i for i, t in enumerate(expected_times("15:00"))}
FEATURES = ("return5m", "return15m", "return60m", "return1d", "trend20d", "trend60d")


def completed_bar(rows, at):
    def total(field):
        values = [getattr(bar, field, None) for bar in rows]
        if field == "volume" and any(getattr(bar, "estimated_volume", False) for bar in rows):
            return None
        return sum(values) if all(v is not None for v in values) else None

    return dict(at=at.replace(tzinfo=SHANGHAI).isoformat(), open=rows[0].open, close=rows[-1].close,
                high=max(b.high for b in rows), low=min(b.low for b in rows),
                volume=total("volume"), amount=total("amount"))


class Timeframes:
    def __init__(self, *, candles=False):
        self.candles = {} if candles else None
        self.day = None
        self.session_bars = {}
        self.closes = deque(maxlen=61)
        self.features = {}

    def update(self, at, bar):
        slot = SLOTS.get(at.hour * 60 + at.minute)
        if slot is None:
            return {}
        day = at.date()
        if self.day != day or getattr(bar, "actions", None) and not getattr(bar, "windowClipped", False):
            if self.day is not None and (not consecutive_observed_sessions(self.day, day)
                                         or len(self.session_bars) != 48 or getattr(bar, "actions", None)):
                self.closes.clear()
                self.features.clear()
            if self.candles is not None:
                previous = self.candles.get("1d") if "return1d" in self.features else None
                self.candles = {"1d": previous} if previous else {}
            self.features = {k: v for k, v in self.features.items() if k.endswith("d")}
            self.day = day
            self.session_bars = {}
        self.session_bars[slot] = bar
        if self.candles is not None:
            self.candles["5m"] = completed_bar([bar], at)
        self.features["return5m"] = bar.close / bar.open - 1
        for length, key in ((3, "return15m"), (12, "return60m"), (48, "return1d")):
            if (slot + 1) % length == 0:
                start = slot + 1 - length
                if all(i in self.session_bars for i in range(start, slot + 1)):
                    self.features[key] = bar.close / self.session_bars[start].open - 1
                    if self.candles is not None:
                        self.candles[key[6:]] = completed_bar([self.session_bars[i] for i in range(start, slot + 1)], at)
                else:
                    self.features.pop(key, None)
                    if self.candles is not None:
                        self.candles.pop(key[6:], None)
        if slot == 47 and len(self.session_bars) == 48:
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
        self.read_slots = asyncio.Semaphore(3)
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
            result.append(bar.model_copy(update={"multiTimeframe": features, "completedBars": state.candles.copy()}))
        return result[-49:]

    async def seed(self, code, bars, end):
        cutoff = end.replace(hour=0, minute=0, second=0, microsecond=0, tzinfo=None) - timedelta(microseconds=1)
        state = Timeframes(candles=True)
        prior = []
        # forward_actions already clipped the live input at the last known ex-date.
        if getattr(bars[-1], "windowClipped", False):
            return state, prior
        async with self.read_slots:
            archived = await asyncio.to_thread(self.lab.datasets.bars, code, cutoff)
        rows = {b.ended_at: b for b in archived[-(63 * 48):]}
        for b in bars:
            if b.observed_at.date() < end.date():
                rows[b.observed_at.replace(tzinfo=None)] = b
        if not rows:
            return state, prior
        events = await self.lab.action_events(code, min(rows).date(), end.date())
        if events is None:
            raise ValueError("等待核验多周期历史窗口")
        boundary = max((event["ex_date"] for event in events), default="")
        for at, bar in sorted(rows.items()):
            if str(at.date()) >= boundary:
                features = state.update(at, bar)
                if hasattr(bar, "observed_at"):
                    prior.append(bar.model_copy(update={"multiTimeframe": features, "completedBars": state.candles.copy()}))
        return state, prior[-49:]
