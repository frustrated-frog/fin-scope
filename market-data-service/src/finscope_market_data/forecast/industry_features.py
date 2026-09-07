"""Industry information is usable only after its recorded observation date."""
from collections import defaultdict
from dataclasses import dataclass, asdict
from datetime import datetime, timedelta
import json
from pathlib import Path
from typing import Mapping, Sequence

INDUSTRY_FEATURE_CODES = ('PEER_MOMENTUM_20', 'PEER_UP_BREADTH', 'PEER_RELATIVE_MOMENTUM_20', 'PEER_COVERAGE')


@dataclass(frozen=True)
class IndustryMembership:
    industry: str
    available_on: str
    codes: tuple[str, ...]


def load_industry_memberships(path: Path, history_path: Path | None = None) -> tuple[IndustryMembership, ...]:
    if not path.exists():
        return ()
    payload = json.loads(path.read_text())
    records = []
    for code, item in payload.get('sectors', {}).items():
        # 881 is the provider's industry namespace; never mix concept baskets here.
        if not code.startswith('881') or item.get('quality_status') != 'COMPLETE':
            continue
        observed = max(datetime.fromisoformat(item[key]).date() for key in ('retrieved_at', 'snapshot_at'))
        # Conservative daily availability: never assume an evening observation existed at the close.
        available = (observed + timedelta(days=1)).isoformat()
        records.append(IndustryMembership(code, available, tuple(sorted(v[0] for v in item['values']))))
    if history_path is not None:
        history = [IndustryMembership(item['industry'], item['available_on'], tuple(item['codes']))
                   for item in json.loads(history_path.read_text())] if history_path.exists() else []
        for record in sorted(records, key=lambda item: (item.available_on, item.industry)):
            previous = [item for item in history if item.industry == record.industry]
            latest = max(previous, key=lambda item: item.available_on) if previous else None
            if latest is None or (record.available_on > latest.available_on and record.codes != latest.codes):
                history.append(record)
        history_path.parent.mkdir(parents=True, exist_ok=True)
        temporary = history_path.with_suffix('.tmp')
        temporary.write_text(json.dumps([asdict(record) for record in history], ensure_ascii=False))
        temporary.replace(history_path)
        records = history
    return tuple(sorted(records, key=lambda item: (item.available_on, item.industry)))


def industry_features(features: Mapping[str, Sequence[float]], memberships: Sequence[IndustryMembership],
                      signal_date: str) -> dict[str, tuple[float, ...]]:
    active = {}
    for record in sorted(memberships, key=lambda item: (item.available_on, item.industry)):
        if record.available_on <= signal_date:
            active[record.industry] = record.codes
    peers = defaultdict(set)
    for codes in active.values():
        available = set(codes) & features.keys()
        for code in available:
            peers[code].update(available - {code})
    result = {}
    for code, values in features.items():
        available = peers[code]
        if len(available) < 2:
            result[code] = (0., 0., 0., 0.)
            continue
        mean = sum(features[peer][1] for peer in sorted(available)) / len(available)
        breadth = sum(features[peer][0] > 0 for peer in available) / len(available)
        result[code] = (mean, breadth, values[1] - mean, 1.)
    return result
