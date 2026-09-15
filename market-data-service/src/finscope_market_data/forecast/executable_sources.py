"""Read-only snapshot access. Existing adjustment events cannot prove query coverage."""
from __future__ import annotations

from collections import Counter
from contextlib import closing
import json
from pathlib import Path
import sqlite3


def audit_snapshots(path: Path) -> dict:
    with closing(sqlite3.connect(path.resolve().as_uri() + '?mode=ro', uri=True)) as connection:
        connection.execute('BEGIN')
        capabilities = dict(connection.execute('SELECT capability, COUNT(*) FROM market_data_snapshot GROUP BY capability'))
        adjustments = Counter()
        symbols = 0
        for (payload,) in connection.execute("SELECT payload_json FROM market_data_snapshot WHERE capability='DAILY_BARS'"):
            rows = json.loads(payload).get('data') or []
            adjustments[','.join(sorted({row.get('adjustment', 'UNKNOWN') for row in rows})) or 'EMPTY'] += 1
            symbols += 1
    return dict(status='BLOCKED', snapshotPath=str(path.resolve()), capabilityCounts=capabilities,
                dailySymbols=symbols, adjustmentCounts=dict(adjustments),
                blockers=[
                    'Raw execution prices require NONE histories; QFQ cannot be relabelled RAW',
                    'Snapshot daily bars do not provide verified historical open-time execution states',
                    'Corporate-action snapshots do not retain explicit requested coverage dates',
                    'A complete point-in-time candidate universe and eligibility evidence are required',
                ])


def load_research_histories(path: Path, codes: set[str], through: str) -> dict:
    result = {}
    with closing(sqlite3.connect(path.resolve().as_uri() + '?mode=ro', uri=True)) as connection:
        connection.execute('BEGIN')
        for code in sorted(codes):
            symbol, market = code.split('.')
            row = connection.execute(
                "SELECT payload_json FROM market_data_snapshot WHERE capability='DAILY_BARS' AND symbol_key=?",
                (market + ':' + symbol,),
            ).fetchone()
            result[code] = [] if row is None else [bar for bar in (json.loads(row[0]).get('data') or [])
                                                 if bar['trade_date'] <= through]
    return result
