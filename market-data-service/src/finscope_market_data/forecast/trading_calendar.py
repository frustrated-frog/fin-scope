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
