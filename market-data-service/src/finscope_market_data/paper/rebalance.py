"""Execute a frozen classic target in board lots until filled, replaced or expired."""
from datetime import time

from .classic_recipe import expires_at
from .portfolio_risk import protect
from .engine import COMMISSION_RATE, INTERVAL, MIN_COMMISSION, SELL_TAX, money, sellable_shares


def expire(book, at, histories, *, before_fill=False):
    plan = book.get("rebalancePlan")
    if not plan or plan["status"] != "进行中":
        return
    reason = ""
    effective = at-INTERVAL if before_fill else at
    if effective.isoformat() >= plan["expires"]:
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
    mark_filled_targets(book, plan, at)
    if plan["status"] == "已完成":
        return []
    pending = {o["code"] for o in book["pending"]}
    orders = []
    for code, leg in sorted(plan["legs"].items(), key=lambda item: (-item[1]["score"], item[0])):
        pos = book["positions"].get(code, {})
        gap = leg["remaining"]
        if not gap:
            continue
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


def mark_filled_targets(book, plan, at):
    for code, leg in plan["legs"].items():
        shares = sum(q for _,q in book["positions"].get(code, {}).get("lots", []))
        leg.update(actual=shares, remaining=leg["target"]-shares)
        if not leg["remaining"]:
            leg["reason"] = "已达目标股数"
    if all(not leg["remaining"] for leg in plan["legs"].values()):
        plan.update(status="已完成", updated=at.isoformat())


def advance_plan(book, point, histories, entry, at, available, runtime, recipe, capital, clock):
    record_fills(book, point["events"], histories)
    current = book.get("rebalancePlan")
    if current and current["status"] == "进行中" and at.isoformat() >= current["expires"]:
        mark_filled_targets(book, current, at)
    expire(book, at, {})
    plan = book.get("rebalancePlan")
    new_selection = False
    if runtime and entry and at.time() == time(14, 45):
        book["selectionBase"] = entry["codes"]
        _, scores = runtime.decide(histories, book, at, book["equity"])
        selection = book.get("strategyState", {}).get("selection", {})
        if selection.get("at") == at.isoformat():
            point["selection"] = dict(**selection, base=entry["codes"], knownAt=entry["at"])
            point["scores"] = scores
            if not selection.get("skipped"):
                new_selection = True
                if plan and plan["status"] == "进行中":
                    plan.update(status="已替换", updated=at.isoformat())
                    book["pending"] = [o for o in book["pending"] if o.get("planAt") != plan["at"]]
                if plan:
                    point["previousPlan"] = plan.copy()
                plan = book["rebalancePlan"] = create(book, selection, scores, at, recipe.rebalance)
        book.pop("signalReasons", None)
    plan = protect(book, plan, histories, at, recipe, new_selection, capital)
    if plan and plan["status"] == "进行中":
        orders = reconcile(book, plan, histories, (entry or {}).get("codes", []), at, recipe.maxFeeBps)
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
