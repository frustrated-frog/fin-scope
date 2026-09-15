"""Calendar-aligned OPEN_5D_V1 price labels; account costs belong to Java's ledger."""
from __future__ import annotations

from dataclasses import dataclass
from datetime import date
from typing import Mapping, Sequence
import math

from finscope_market_data.models import DailyBar
from finscope_market_data.forecast.features import current_features


@dataclass(frozen=True)
class ExecutableLabel:
    protocol_version: str
    signal_date: str
    entry_date: str
    exit_date: str
    features: tuple[float, ...]
    price_return: float


def build_executable_labels(
    bars: Sequence[DailyBar], trading_dates: Sequence[str],
    protocol: Mapping[str, object], *, as_of: str,
) -> list[ExecutableLabel]:
    """Require exact scheduled dates; missing bars never extend the holding horizon.

    Uses consistently adjusted research prices, not executable quantities or net P&L.
    An unavailable future bar removes its label only, not the observable universe.
    """
    if (protocol.get('version') != 'OPEN_5D_V1'
            or protocol.get('holdingTradingDays') != 5
            or protocol.get('rebalanceTradingDays') != 5):
        raise ValueError('Unsupported executable trading protocol')
    dates = list(trading_dates)
    if not dates or dates != sorted(set(dates)):
        raise ValueError('Trading calendar must be nonempty, unique and increasing')
    for day in [*dates, as_of]:
        date.fromisoformat(day)
    ordered = sorted(bars, key=lambda bar: bar.trade_date)
    by_day = {bar.trade_date: bar for bar in ordered}
    if len(by_day) != len(ordered) or any(day not in dates for day in by_day):
        raise ValueError('Duplicate or off-calendar bar')
    if len({bar.adjustment for bar in ordered}) > 1:
        raise ValueError('Mixed adjustment basis')
    result = []
    for index, signal_date in enumerate(dates[:-6]):
        entry_date, exit_date = dates[index + 1], dates[index + 6]
        if exit_date > as_of:
            continue
        if any(day not in by_day for day in (signal_date, entry_date, exit_date)):
            continue
        past = [bar for bar in ordered if bar.trade_date <= signal_date]
        if len(past) < 61:
            continue
        entry, exit_bar = by_day[entry_date], by_day[exit_date]
        if not all(math.isfinite(value) and value > 0 for value in (entry.open, exit_bar.open)):
            raise ValueError('Invalid label price')
        result.append(ExecutableLabel(
            str(protocol['version']), signal_date, entry_date, exit_date,
            current_features(past), exit_bar.open / entry.open - 1.0,
        ))
    return result
