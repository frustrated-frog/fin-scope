"""Point-in-time source lists; the same publication-time lookup drives replay and forward accounts."""
from bisect import bisect_right
from datetime import datetime, time
from functools import lru_cache
import json
import re

from finscope_market_data.overnight.automation_store import AutomationStore
from finscope_market_data.overnight.universe import POOL_KEY
from .engine import SHANGHAI


def local(at):
    return at.replace(tzinfo=SHANGHAI) if at.tzinfo is None else at.astimezone(SHANGHAI)


@lru_cache(maxsize=96)
def discovery_entry(path, modified):
    report = json.loads(path.read_bytes())
    known = local(datetime.fromisoformat(report["retrieved_at"]))
    close = local(datetime.combine(datetime.fromisoformat(report["as_of_date"]).date(), time(15)))
    codes = []
    for row in report.get("candidates", []):
        code = row["code"]
        if (re.fullmatch(r"(00|30|60|68)\d{4}", code) and "ST" not in row.get("name", "").upper()
                and code not in codes):
            codes.append(code)
        if len(codes) == 24:
            break
    return dict(at=max(known, close).isoformat(), date=report["as_of_date"], codes=codes)


def schedule(lab, base, cutoff, start=None):
    if base == "DISCOVERY":
        directory = lab.path.parent / "discovery-research"
        entries = [discovery_entry(p, p.stat().st_mtime_ns) for p in directory.glob("????-??-??.json")
                   if p.stem <= str(cutoff.date())]
        # A late backfill of an older report must never replace a newer business date.
        timeline = []
        for entry in sorted(entries, key=lambda r: (r["at"], r["date"])):
            if not timeline or entry["date"] > timeline[-1]["date"]:
                timeline.append(entry)
    else:
        roster = AutomationStore(lab.jobs.archive).get(POOL_KEY) if lab.jobs.archive else {}
        members = sorted((roster or {}).get("members", []), key=lambda m: m["joinedAt"])
        timeline, known = [], []
        for m in members:
            if base != "COHORT" and m["board"] != base:
                continue
            code = m["instrumentCode"].split(".")[0]
            if code in known:
                continue
            known.append(code)
            at = local(datetime.fromisoformat(m["joinedAt"])).isoformat()
            entry = dict(at=at, date=at[:10], codes=known.copy())
            if timeline and timeline[-1]["at"] == at:
                timeline[-1] = entry
            else:
                timeline.append(entry)
            if len(known) == 24:
                break
    timeline = [r for r in timeline if r["at"] <= cutoff.isoformat()]
    if not timeline:
        return []
    if start is None:
        return timeline[-1:]
    prior = [r for r in timeline if r["at"] <= start.isoformat()]
    return prior[-1:] + [r for r in timeline if r["at"] > start.isoformat()]


def as_of(timeline, at):
    i = bisect_right(timeline, at.isoformat(), key=lambda r: r["at"])
    return timeline[i - 1] if i else None


def active_codes(entry, book):
    return list(dict.fromkeys([*(entry or {}).get("codes", []), *book.get("classicBenchmark", []),
                              *book["positions"], *(o["code"] for o in book["pending"])]))


def polling_codes(timeline, book, earliest, now):
    # A report can be published between the close of a bar and its arrival.
    codes = active_codes(as_of(timeline, earliest), book)
    for entry in timeline:
        if earliest.isoformat() < entry["at"] <= now.isoformat():
            codes.extend(entry["codes"])
    return list(dict.fromkeys(codes))
