"""Observed P&L bridge for a paper account, with optional archived position marks."""
from collections import defaultdict
from .engine import money


def record_marks(book, point):
    point["positionValues"] = {code: money(pos["price"] * sum(q for _, q in pos["lots"]))
                               for code, pos in book["positions"].items()}
    point["receivable"] = book.get("dividendReceivable", 0)


def summarize(points, prior, capital, net, fees):
    close = points[-1]
    opening = prior["equity"] if prior else capital
    result = dict(fromAt=prior["at"] if prior else None, toAt=close["at"], opening=opening, closing=close["equity"],
                  net=net, fees=fees, beforeFees=money(net + fees), stocks=None, receivableChange=None, residual=None,
                  missedBars=sum(p.get("missedBars", 0) for p in points))
    if "positionValues" not in close or prior and "positionValues" not in prior:
        return result
    start = prior["positionValues"] if prior else {}
    end = close["positionValues"]
    if "receivable" not in close or prior and "receivable" not in prior:
        return result
    trading = defaultdict(float)
    costs = defaultdict(float)
    for point in points:
        for event in point["events"]:
            if event["action"] != "成交":
                continue
            code = event["code"]
            gross = money(event["quantity"] * event["price"])
            trading[code] += gross if event["side"] == "SELL" else -gross
            costs[code] += event["fee"]
    stocks = []
    for code in sorted(start.keys() | end.keys() | trading.keys()):
        stocks.append(dict(code=code, net=money(end.get(code, 0) - start.get(code, 0) + trading[code] - costs[code]),
                           fees=money(costs[code])))
    rights = money(close["receivable"] - (prior["receivable"] if prior else 0))
    residual = money(net - sum(v["net"] for v in stocks) - rights)
    return dict(result, stocks=sorted(stocks, key=lambda row: -abs(row["net"])), receivableChange=rights, residual=residual)
