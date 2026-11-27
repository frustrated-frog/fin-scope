"""Five-minute risk checks independent of the strategy's selection frequency."""
from .classic_recipe import expires_at


def protect(book, plan, histories, at, recipe, new_selection, capital):
    if not recipe.stopLoss and not recipe.drawdownStop:
        return plan
    equity = book["equity"]
    risk = book.setdefault("riskState", dict(peak=capital))
    if new_selection and risk.pop("halted", False):
        risk["peak"] = equity
    risk["peak"] = max(risk["peak"], equity)
    if recipe.drawdownStop and equity <= risk["peak"]*(1-recipe.drawdownStop):
        risk["halted"] = True
    exits = {}
    if risk.get("halted"):
        for code in set(book["positions"]) | set((plan or {}).get("legs", {})):
            exits[code] = "组合达到回撤阈值，目标降至现金"
    elif recipe.stopLoss:
        for code, position in book["positions"].items():
            shares = sum(q for _,q in position["lots"])
            if code in histories and shares*position["price"] <= position["cost"]*(1-recipe.stopLoss):
                exits[code] = "个股相对持仓成本触及止损线"
    if not exits:
        return plan
    if not plan or plan["status"] != "进行中":
        if not any(code in book["positions"] for code in exits):
            return plan
        plan = book["rebalancePlan"] = dict(at=at.isoformat(), expires=expires_at(at,recipe.rebalance).isoformat(),
                updated=at.isoformat(), status="进行中", source="盘中风控", fills=0, fees=0., legs={})
    for code, reason in sorted(exits.items()):
        leg = plan["legs"].setdefault(code, dict(target=0, weight=0., score=0.))
        if "riskReason" not in leg:
            leg.update(originalTarget=leg["target"], target=0, riskReason=reason)
    book["pending"] = [o for o in book["pending"] if o["side"] != "BUY" or o["code"] not in exits]
    return plan
