"""Deterministic five-minute rules shared by historical replay and forward accounts."""
from datetime import datetime, timedelta
from decimal import Decimal, ROUND_HALF_UP
from math import isfinite
from statistics import fmean, pstdev
from typing import Literal
from zoneinfo import ZoneInfo

from pydantic import BaseModel, ConfigDict, Field, model_validator

SHANGHAI = ZoneInfo("Asia/Shanghai")
INTERVAL = timedelta(minutes=5)
ENGINE = "cn-paper-5m-v1"


class Term(BaseModel):
    model_config = ConfigDict(extra="forbid", allow_inf_nan=False)
    feature: Literal["momentum", "volatility", "volumeSurprise", "closeLocation"]
    window: int = Field(ge=2, le=48)
    weight: float = Field(ge=-10, le=10)


class Rule(BaseModel):
    model_config = ConfigDict(extra="forbid", allow_inf_nan=False)
    name: str = Field(min_length=1, max_length=80)
    hypothesis: str = Field(min_length=1, max_length=600)
    terms: list[Term] = Field(min_length=1, max_length=4)
    entry: float = Field(ge=-20, le=20)
    exit: float = Field(ge=-20, le=20)
    slots: int = Field(ge=1, le=10)

    @model_validator(mode="after")
    def check(self):
        if self.exit >= self.entry or not any(t.weight for t in self.terms):
            raise ValueError("卖出阈值须低于买入阈值，因子权重不能全为零")
        return self


def score(rule, history):
    parts = []
    for term in rule.terms:
        series = history[-(term.window + 1):]
        if len(series) <= term.window:
            return None
        last = series[-1]
        if term.feature == "momentum":
            value = last.close / series[0].close - 1
        elif term.feature == "volatility":
            value = pstdev(b.close / a.close - 1 for a, b in zip(series, series[1:]))
        elif term.feature == "volumeSurprise":
            base = fmean(b.volume for b in series[:-1])
            if base <= 0:
                return None
            value = last.volume / base - 1
        else:
            high = max(b.high for b in series[1:])
            low = min(b.low for b in series[1:])
            value = (last.close - low) / (high - low) - 0.5 if high > low else 0
        parts.append(term.weight * value)
    return round(sum(parts), 8)


def money(x):
    return float(Decimal(str(x)).quantize(Decimal("0.01"), rounding=ROUND_HALF_UP))


def new_book(capital):
    return dict(cash=capital, positions={}, pending=[], anchors={}, equity=capital,
                fills=0, fees=0.0, realized=0.0, last=None)


def executable(order, bar, end):
    available = datetime.fromisoformat(order["available"])
    return (available.date() == end.date() and available <= end - INTERVAL
            and end - available <= 2 * INTERVAL and bar.volume > 0
            and bar.high > bar.low)


def execute(book, order, bar, end):
    code, side = order["code"], order["side"]
    price = round(bar.open * (1.0005 if side == "BUY" else 0.9995), 4)
    lot = 200 if code.startswith(("688", "689")) else 100
    pos = book["positions"].get(code, {})
    capacity = int(bar.volume * 0.05) // lot * lot
    if side == "BUY":
        budget = min(book["cash"], order["budget"])
        quantity = min(capacity, int(budget / price) // lot * lot)
        while quantity and money(quantity * price + max(5, quantity * price * 0.0003)) > budget:
            quantity -= lot
    else:
        quantity = min(capacity, sum(q for day, q in pos.get("lots", []) if day < end.date().isoformat()))
    if not executable(order, bar, end) or quantity <= 0:
        return {**order, "action": "未成交", "reason": "委托已过期、不可卖、成交量或资金不足，或行情为一字线"}
    gross = money(quantity * price)
    fee = money(max(5, gross * 0.0003) + (gross * 0.0005 if side == "SELL" else 0))
    if side == "BUY":
        book["cash"] = money(book["cash"] - gross - fee)
        book["positions"][code] = dict(lots=[[end.date().isoformat(), quantity]], cost=money(gross + fee))
    else:
        total = sum(q for _, q in pos["lots"])
        cost = money(pos["cost"] * quantity / total)
        book["cash"] = money(book["cash"] + gross - fee)
        book["realized"] = money(book["realized"] + gross - fee - cost)
        if quantity == total:
            del book["positions"][code]
        else:
            # Positions are opened once, with no averaging into existing holdings.
            pos["lots"][0][1] -= quantity
            pos["cost"] = money(pos["cost"] - cost)
    book["fills"] += 1
    book["fees"] = money(book["fees"] + fee)
    return dict(**order, action="成交", price=price, quantity=quantity, fee=fee)


def advance(book, rule, histories, end, available, capital):
    """All members have a closed bar at end. Never fill a new signal on that bar."""
    waiting = []
    events = []
    for order in book["pending"]:
        if end - INTERVAL < datetime.fromisoformat(order["available"]):
            waiting.append(order)
        else:
            events.append(execute(book, order, histories[order["code"]][-1], end))
    book["pending"] = waiting
    values = {}
    for code, bars in histories.items():
        price = bars[-1].close
        book["anchors"].setdefault(code, price)
        if code in book["positions"]:
            pos = book["positions"][code]
            pos["price"] = price
            values[code] = sum(q for _, q in pos["lots"]) * price
    equity = money(book["cash"] + sum(values.values()))
    benchmark = fmean(bars[-1].close / book["anchors"][code] for code, bars in histories.items())
    scores = {c: score(rule, bars) for c, bars in histories.items()}
    ranking = sorted((c for c in scores if scores[c] is not None), key=lambda c: (-scores[c], c))
    pending_codes = {o["code"] for o in waiting}
    free = rule.slots - len(values) - sum(o["side"] == "BUY" for o in waiting)
    for rank, code in enumerate(ranking, 1):
        if code in pending_codes:
            continue
        value = scores[code]
        if code in values and value <= rule.exit:
            side, reason = "SELL", "得分降至退出阈值"
        elif code not in values and free > 0 and value >= rule.entry and rank <= rule.slots:
            side, reason = "BUY", "排名进入目标范围并达到买入阈值"
            free -= 1
        else:
            continue
        order = dict(code=code, side=side, reason=reason, score=value, rank=rank,
                     available=available.isoformat(), budget=money(equity / rule.slots))
        book["pending"].append(order)
        events.append(dict(**order, action="信号"))
    book.update(equity=equity, last=end.isoformat())
    return dict(at=end.isoformat(), equity=equity, benchmark=money(benchmark * capital),
                cash=book["cash"], scores=scores, events=events,
                reason="已检查持仓和入场条件" if ranking else "因子窗口数据不足")


def valid_bar(bar):
    return (bar.frequency_minutes == 5 and bar.observed_at.tzinfo is not None
            and all(isfinite(x) for x in (bar.open, bar.close, bar.low, bar.high, bar.volume))
            and 0 < bar.low <= min(bar.open, bar.close) <= max(bar.open, bar.close) <= bar.high
            and bar.volume >= 0)
