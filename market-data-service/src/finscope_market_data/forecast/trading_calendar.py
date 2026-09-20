"""Exchange closures, not the government's make-up working-day calendar.

Source: https://www.sse.com.cn/disclosure/announcement/general/c/c_20251222_10802507.shtml
Unknown years fail closed until the exchange publishes and we verify their calendar.
"""
from datetime import date, timedelta


_CLOSURES = {
    2026: (("01-01", "01-03"), ("02-15", "02-23"), ("04-04", "04-06"),
           ("05-01", "05-05"), ("06-19", "06-21"), ("09-25", "09-27"), ("10-01", "10-07"))
}


def next_session(after: date) -> date | None:
    current = after + timedelta(days=1)
    while current.year in _CLOSURES:
        closed = any(start <= current.strftime("%m-%d") <= end for start, end in _CLOSURES[current.year])
        if current.weekday() < 5 and not closed:
            return current
        current += timedelta(days=1)
    return None


def previous_session(before: date) -> date | None:
    """Last exchange session strictly before the supplied date; unknown years fail closed."""
    current = before - timedelta(days=1)
    while current.year in _CLOSURES:
        closed = any(start <= current.strftime("%m-%d") <= end for start, end in _CLOSURES[current.year])
        if current.weekday() < 5 and not closed:
            return current
        current -= timedelta(days=1)
    return None


def consecutive_observed_sessions(signal: date, outcome: date) -> bool:
    """Certify a one-session label without treating a missing bar as a holiday.

    For uncovered historical years only pairs with no intervening weekday are
    retained. Both endpoints are observed prices; long holiday gaps remain
    unverified and are excluded until an authoritative calendar covers them.
    """
    if outcome <= signal or signal.weekday() >= 5 or outcome.weekday() >= 5:
        return False
    for value in (signal, outcome):
        if value.year in _CLOSURES and any(
                start <= value.strftime('%m-%d') <= end for start, end in _CLOSURES[value.year]):
            return False
    expected = next_session(signal)
    if expected is not None:
        return expected == outcome
    candidate = signal + timedelta(days=1)
    while candidate.weekday() >= 5:
        candidate += timedelta(days=1)
    return candidate == outcome


def event_window(on_or_after: date) -> list[date] | None:
    """Six closes before the first reaction session, then five exchange sessions.

    Offsets are -5..0 (0 is the baseline close), followed by 1..5.
    Unknown calendar coverage fails closed, including across year boundaries.
    """
    first = next_session(on_or_after - timedelta(days=1))
    if first is None:
        return None
    earlier = []
    current = first
    for _ in range(6):
        current = previous_session(current)
        if current is None:
            return None
        earlier.append(current)
    later = [first]
    for _ in range(4):
        current = next_session(later[-1])
        if current is None:
            return None
        later.append(current)
    return list(reversed(earlier)) + later
