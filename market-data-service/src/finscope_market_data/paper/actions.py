"""Freeze reported ex-date entitlements and distinguish them from spendable cash."""

import asyncio
from bisect import bisect_left
from decimal import Decimal
import logging

from finscope_market_data.models import (
    CorporateActionsData,
    DataCapability,
    StockSymbol,
)
from finscope_market_data.providers.base import ProviderError

log = logging.getLogger(__name__)


async def read_actions(router, limit, code, start, end):
    if router is None:
        return None
    symbol = StockSymbol(market="SH" if code.startswith("6") else "SZ", code=code)
    try:
        async with limit:
            envelope = await router.fetch(
                DataCapability.CORPORATE_ACTIONS,
                symbol,
                from_date=str(start),
                to_date=str(end),
            )
        if (
            envelope.quality_status not in ("FRESH_PRIMARY", "FRESH_FALLBACK")
            or envelope.data is None
        ):
            return None
        data = CorporateActionsData.model_validate(envelope.data)
        if data.symbol != symbol:
            return None
        events = {}
        for action in data.items:
            if not str(start) <= action.ex_date <= str(end):
                continue
            if (
                action.currency != "CNY"
                or min(
                    action.dividend_per_share,
                    action.per_share_bonus,
                    action.allotment_ratio,
                )
                < 0
            ):
                return None
            event = action.model_dump(mode="json")
            if action.ex_date in events and events[action.ex_date] != event:
                return None
            events[action.ex_date] = event
        return list(events.values())
    except (ProviderError, ValueError, OSError, asyncio.TimeoutError):
        log.info("公司行为核验暂不可用 %s", code)
        return None


def annotate(rows, events):
    """Attach each dated event to the first available bar on or after the effective date."""
    dates = [row[0][:10] for row in rows]
    indexed = {}
    for event in events or []:
        i = bisect_left(dates, event["ex_date"])
        if i < len(rows):
            indexed.setdefault(i, []).append(event)
    for i, row in enumerate(rows):
        row.extend(
            [events is not None, sorted(indexed.get(i, []), key=lambda e: e["ex_date"])]
        )


def apply_entitlements(book, code, events):
    for action in events:
        date = action["ex_date"]
        if (
            code not in book["anchors"]
            or book.setdefault("lastAction", {}).get(code, "") >= date
        ):
            continue
        book["lastAction"][code] = date
        profile = book.setdefault("benchmarkDistributions", {}).setdefault(
            code, dict(shares=1.0, cash=0.0)
        )
        dividend = Decimal(str(action["dividend_per_share"]))
        bonus = Decimal(str(action["per_share_bonus"]))
        profile["cash"] += profile["shares"] * float(dividend)
        profile["shares"] *= 1 + float(bonus)
        position = book["positions"].get(code)
        if not position:
            continue
        eligible = sum(
            q for day, q in position["lots"] if day < date or day == "9999-12-31"
        )
        cash = float(round(eligible * dividend, 2))
        if cash:
            book["dividendReceivable"] = round(
                book.get("dividendReceivable", 0) + cash, 2
            )
            book["realized"] = round(book["realized"] + cash, 2)
        shares = int(eligible * bonus)
        if shares:
            # The feed has no listing date. Include the entitlement in NAV; never sell it early.
            position["lots"].append(["9999-12-31", shares])


async def verified_dates(archive, code, start, end, now, *, online=False):
    """Reuse the overnight verifier's dated coverage; ex-dates alone do not give entitlement amounts."""
    from datetime import date
    from finscope_market_data.overnight.action_verification import ActionVerificationStore
    from finscope_market_data.providers.baostock_actions import BaostockActionProvider

    if archive is None or not hasattr(archive, "connect"):
        return None
    symbol = code + (".SH" if code.startswith("6") else ".SZ")
    record = await asyncio.to_thread(lambda: ActionVerificationStore(archive).latest(symbol, now.replace(tzinfo=None)))
    if not (record and record.get("status") == "COVERED" and record["fromDate"] <= start and end <= record["throughDate"]):
        if not online:
            return None
        try:
            record = await asyncio.to_thread(BaostockActionProvider().fetch, symbol,
                                             date.fromisoformat(start), date.fromisoformat(end))
        except (ValueError, OSError, ProviderError):
            return None
    return [dict(ex_date=d, event_types=["UNKNOWN"], currency="CNY", dividend_per_share=0.,
                 per_share_bonus=0., allotment_ratio=0., termsKnown=False)
            for d in record["exDates"] if start <= d <= end]
