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


def unfilled_reason(order, bar, end):
    available = datetime.fromisoformat(order["available"])
    if available.date() != end.date() or end - available > 2 * INTERVAL:
        return "委托超过两个五分钟窗口或已跨日"
    if bar.volume <= 0:
        return "行情无成交量"
    if bar.high == bar.low:
        return "一字线，无法确认可成交"
    return None


def execute(book, order, bar, end):
    code, side = order["code"], order["side"]
    reason = unfilled_reason(order, bar, end)
    if reason:
        return {**order, "action": "未成交", "reason": reason}
    adjusted = bar.open * (1.0005 if side == "BUY" else 0.9995)
    price = round(min(bar.high, max(bar.low, adjusted)), 4)
    lot = 200 if code.startswith(("688", "689")) else 100
    pos = book["positions"].get(code, {})
    capacity = int(bar.volume * 0.05) // lot * lot
    if side == "BUY":
        budget = min(book["cash"], order["budget"])
        quantity = min(capacity, int(budget / price) // lot * lot)
        while quantity and money(quantity * price + max(5, quantity * price * 0.0003)) > budget:
            quantity -= lot
    else:
        quantity = min(capacity, order.get("maxShares", capacity), sum(q for day, q in pos.get("lots", []) if day < end.date().isoformat()))
    if quantity <= 0:
        reason = "可用资金不足一个交易单位" if side == "BUY" else "T+1 限制，可卖股数为零"
        if capacity <= 0:
            reason = "本根成交量的 5% 不足一个交易单位"
        return {**order, "action": "未成交", "reason": reason}
    gross = money(quantity * price)
    fee = money(max(5, gross * 0.0003) + (gross * 0.0005 if side == "SELL" else 0))
    if side == "BUY":
        book["cash"] = money(book["cash"] - gross - fee)
        position = book["positions"].setdefault(code, dict(lots=[], cost=0))
        position["lots"].append([end.date().isoformat(), quantity])
        position["cost"] = money(position["cost"] + gross + fee)
    else:
        total = sum(q for _, q in pos["lots"])
        cost = money(pos["cost"] * quantity / total)
        book["cash"] = money(book["cash"] + gross - fee)
        book["realized"] = money(book["realized"] + gross - fee - cost)
        if quantity == total:
            del book["positions"][code]
        else:
            remaining = quantity
            for entry in pos["lots"]:
                sold = min(remaining, entry[1])
                entry[1] -= sold
                remaining -= sold
            pos["lots"] = [entry for entry in pos["lots"] if entry[1]]
            pos["cost"] = money(pos["cost"] - cost)
    book["fills"] += 1
    book["fees"] = money(book["fees"] + fee)
    return dict(**order, action="成交", price=price, quantity=quantity, fee=fee)


def advance(book, rule, histories, end, available, capital, *, universe=None, decide=None):
    """Only histories with a fresh closed bar may signal or fill."""
    universe = universe or list(histories)
    waiting = []
    events = []
    for order in book["pending"]:
        if end - INTERVAL < datetime.fromisoformat(order["available"]):
            waiting.append(order)
        elif order["code"] not in histories:
            events.append(dict(order, action="未成交", reason="该股票行情缺失"))
        else:
            events.append(execute(book, order, histories[order["code"]][-1], end))
    book["pending"] = waiting
    marks = book.setdefault("marks", {})
    for code, bars in histories.items():
        price = bars[-1].close
        book["anchors"].setdefault(code, price)
        marks[code] = price
        if code in book["positions"]:
            book["positions"][code].update(price=price, pricedAt=end.isoformat())
    values = {code: pos["price"] * sum(q for _, q in pos["lots"]) for code,pos in book["positions"].items()}
    equity = money(book["cash"] + sum(values.values()))
    # Each original member keeps its benchmark weight during outages.
    benchmark = fmean(marks.get(c, book["anchors"].get(c, 1)) / book["anchors"].get(c, 1) for c in universe)
    book["stale"] = [code for code in universe if code not in histories]
    if decide is None:
        targets = None
        scores = {c: score(rule, histories[c]) if c in histories else None for c in universe}
    else:
        targets, scores = decide(histories, book, end, equity)
    ranking = sorted((c for c in scores if scores[c] is not None), key=lambda c: (-scores[c], c))
    pending_codes = {o["code"] for o in waiting}
    free = 0 if targets is not None else rule.slots - len(values) - sum(o["side"] == "BUY" for o in waiting)
    for rank, code in enumerate(ranking, 1):
        if code in pending_codes:
            continue
        value = scores[code]
        if targets is not None:
            if code not in targets:
                continue
            delta = equity * targets[code] - values.get(code, 0)
            price = histories[code][-1].close
            lot = 200 if code.startswith(("688","689")) else 100
            if abs(delta) < price * lot and not (targets[code] == 0 and code in values):
                continue
            side = "BUY" if delta > 0 else "SELL"
            reason = f"策略目标仓位 {targets[code]:.1%}"
        elif code in values and value <= rule.exit:
            side, reason = "SELL", "得分降至退出阈值"
        elif code not in values and free > 0 and value >= rule.entry and rank <= rule.slots:
            side, reason = "BUY", "排名进入目标范围并达到买入阈值"
            free -= 1
        else:
            continue
        order = dict(code=code, side=side, reason=reason, score=value, rank=rank,
                     available=available.isoformat(), budget=money(abs(delta) if targets is not None else equity / rule.slots))
        if targets is not None and side == "SELL" and targets[code] > 0:
            order["maxShares"] = int(abs(delta) / price) // lot * lot
        book["pending"].append(order)
        events.append(dict(**order, action="信号"))
    book.update(equity=equity, last=end.isoformat())
    return dict(at=end.isoformat(), equity=equity, benchmark=money(benchmark * capital),
                cash=book["cash"], scores=scores, events=events, stale=book["stale"],
                holdings={c:sum(q for _,q in pos["lots"]) for c,pos in book["positions"].items()},
                reason="已检查持仓和入场条件" if ranking else "因子窗口数据不足")


def valid_bar(bar):
    return (bar.frequency_minutes == 5 and bar.observed_at.tzinfo is not None
            and all(isfinite(x) for x in (bar.open, bar.close, bar.low, bar.high, bar.volume))
            and 0 < bar.low <= min(bar.open, bar.close) <= max(bar.open, bar.close) <= bar.high
            and bar.volume >= 0)
