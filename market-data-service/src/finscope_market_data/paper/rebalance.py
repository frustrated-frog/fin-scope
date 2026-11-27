"""Execute a frozen classic target in board lots until filled, replaced or expired."""
from datetime import datetime, time

from finscope_market_data.forecast.trading_calendar import next_session
from .engine import SHANGHAI, COMMISSION_RATE, MIN_COMMISSION, SELL_TAX, money, sellable_shares


def expires_at(at, frequency):
    day = at.date()
    while (following := next_session(day)) is not None:
        if (frequency == "DAILY" or frequency == "WEEKLY" and following.isocalendar()[:2] != at.date().isocalendar()[:2]
                or frequency == "MONTHLY" and following.month != at.month):
            return datetime.combine(following, time(14, 45), SHANGHAI)
        day = following
    return datetime.combine(day, time(15), SHANGHAI)


def expire(book, at, histories):
    plan = book.get("rebalancePlan")
    if not plan or plan["status"] != "进行中":
        return
    reason = ""
    if at.isoformat() >= plan["expires"]:
        reason = "已到下一调仓周期"
    elif any(getattr(bars[-1], "actions", []) for c, bars in histories.items() if c in plan["legs"]):
        reason = "除权日结束原股数目标"
    if reason:
        plan.update(status="已到期", reason=reason, updated=at.isoformat())
        book["pending"] = [o for o in book["pending"] if o.get("planAt") != plan["at"]]


def create(book, selection, scores, at, frequency):
    legs = {}
    for code in sorted(selection["targets"].keys() | book["positions"].keys()):
        target = selection["targets"].get(code, 0.)
        price = book["marks"].get(code)
        lot = 200 if code.startswith(("688", "689")) else 100
        shares = int(book["equity"] * target / price) // lot * lot if target else 0
        legs[code] = dict(weight=target, target=shares, score=scores.get(code, 0.))
    return dict(at=at.isoformat(), expires=expires_at(at, frequency).isoformat(), legs=legs,
                status="进行中", fills=0, fees=0., updated=at.isoformat())


def reconcile(book, plan, histories, base, at, fee_limit):
    pending = {o["code"] for o in book["pending"]}
    orders = []
    remaining = False
    for code, leg in sorted(plan["legs"].items(), key=lambda item: (-item[1]["score"], item[0])):
        pos = book["positions"].get(code, {})
        held = sum(q for _, q in pos.get("lots", []))
        gap = leg["target"] - held
        leg.update(actual=held, remaining=gap)
        if not gap:
            leg["reason"] = "已达目标股数"
            continue
        remaining = True
        side = "BUY" if gap > 0 else "SELL"
        bars = histories.get(code)
        sellable = sellable_shares(pos, str(at.date()))
        if code in pending:
            reason = "等待已有委托撮合"
        elif not bars:
            reason = "等待该股票的有效行情"
        elif side == "BUY" and code not in base:
            reason = "已离开当前股票池，暂停加仓"
        elif side == "SELL" and not sellable:
            reason = "T+1 待解冻"
        elif at.time() >= time(14, 55):
            reason = "等待下一交易日的可成交窗口"
        else:
            reason = "按剩余股数提交委托"
            quantity = gap if gap > 0 else min(-gap, sellable)
            notional = quantity * bars[-1].close
            estimate = max(MIN_COMMISSION, notional * COMMISSION_RATE) + (notional * SELL_TAX if side == "SELL" else 0)
            if fee_limit and estimate * 10000 > notional * fee_limit:
                leg["reason"] = "预计佣金与印花税超过费率上限"
                continue
            orders.append(dict(code=code, side=side, reason=reason, score=leg["score"],
                               rank=len(orders)+1, maxShares=quantity, budget=money(notional),
                               planAt=plan["at"], maxFeeBps=fee_limit))
        leg["reason"] = reason
    if not remaining:
        plan.update(status="已完成", updated=at.isoformat())
    # A sell may fund a buy on the same executable bar. Otherwise do not send an
    # order that cannot afford even one lot at the signal price.
    if not any(o["side"] == "SELL" for o in orders + book["pending"]):
        for order in orders.copy():
            if order["side"] == "BUY":
                code = order["code"]
                lot = 200 if code.startswith(("688", "689")) else 100
                if book["cash"] < histories[code][-1].close * lot + MIN_COMMISSION:
                    plan["legs"][code]["reason"] = "现金不足一手，等待可用资金"
                    orders.remove(order)
    return orders


def record_fills(book, events, histories):
    costs = book.setdefault("executionCosts", dict(commission=0., stampTax=0., slippage=0., notional=0.))
    plan = book.get("rebalancePlan")
    for event in events:
        if event.get("planAt") and plan and event["planAt"] == plan["at"]:
            leg = plan["legs"][event["code"]]
            leg["lastOutcome"] = event["reason"] if event["action"] != "成交" else "成交"
        if event["action"] != "成交":
            continue
        gross = money(event["price"] * event["quantity"])
        tax = event["stampTax"]
        direction = 1 if event["side"] == "BUY" else -1
        impact = money(direction * (event["price"] - histories[event["code"]][-1].open) * event["quantity"])
        event.update(commission=money(event["fee"] - tax), slippage=impact, notional=gross)
        for field in costs:
            costs[field] = money(costs[field] + event[field])
        if plan and event.get("planAt") == plan["at"]:
            plan.update(fills=plan["fills"] + 1, fees=money(plan["fees"]+event["fee"]))


def advance_plan(book, point, histories, entry, at, available, runtime, frequency, fee_limit, clock):
    record_fills(book, point["events"], histories)
    plan = book.get("rebalancePlan")
    if runtime and entry and at.time() == time(14, 45):
        book["selectionBase"] = entry["codes"]
        _, scores = runtime.decide(histories, book, at, book["equity"])
        selection = book.get("strategyState", {}).get("selection", {})
        if selection.get("at") == at.isoformat():
            point["selection"] = dict(**selection, base=entry["codes"], knownAt=entry["at"])
            point["scores"] = scores
            if not selection.get("skipped"):
                if plan and plan["status"] == "进行中":
                    plan.update(status="已替换", updated=at.isoformat())
                    book["pending"] = [o for o in book["pending"] if o.get("planAt") != plan["at"]]
                if plan:
                    point["previousPlan"] = plan.copy()
                plan = book["rebalancePlan"] = create(book, selection, scores, at, frequency)
        book.pop("signalReasons", None)
    if plan and plan["status"] == "进行中":
        orders = reconcile(book, plan, histories, (entry or {}).get("codes", []), at, fee_limit)
        seen = (clock() if clock else available).isoformat()
        for order in orders:
            order["available"] = seen
            book["pending"].append(order)
            point["events"].append(dict(**order, action="信号"))
        plan["updated"] = at.isoformat()
    if plan and plan["updated"] == at.isoformat():
        point["rebalance"] = plan
        point["reason"] = "组合调仓 · " + plan["status"]
    else:
        point["reason"] = "等待下一调仓时点"
    return point
