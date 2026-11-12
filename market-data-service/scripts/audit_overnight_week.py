"""Read-only weekly audit of original archives; --through is Shanghai local time."""
import argparse
from datetime import datetime, timedelta
import json
from pathlib import Path
import sqlite3
from types import SimpleNamespace

from finscope_market_data.overnight.weekly_diagnostics import build_weekly_diagnostics


def audit(path, through):
    start = str(through.date() - timedelta(days=through.weekday()))
    db = sqlite3.connect(path.resolve().as_uri() + '?mode=ro', uri=True)
    try:
        db.execute('PRAGMA query_only=ON')
        db.execute('BEGIN')
        rows = db.execute('''SELECT p.payload,o.payload FROM overnight_prediction p
            LEFT JOIN overnight_outcome o ON o.prediction_id=p.id AND o.observed_at=(
                SELECT MAX(observed_at) FROM overnight_outcome WHERE prediction_id=p.id AND observed_at<=?)
            WHERE json_extract(p.payload,'$.signalDate')>=? AND p.generated_at<?''',
            (through.isoformat(), start, through.isoformat())).fetchall()
        reports = [{**json.loads(p), 'outcome': json.loads(o) if o else None} for p, o in rows]
        jobs = {r['key']: r for p, in db.execute('''SELECT payload FROM overnight_automation_job
            WHERE json_extract(payload,'$.signalDate')>=?''', (start,)) if (r := json.loads(p))}
        snapshots = {(r['signalDate'], r['cutoff']): r for p, in db.execute(
            'SELECT payload FROM overnight_context_snapshot WHERE signal_date>=?', (start,)) if (r := json.loads(p))}
    finally:
        db.close()
    def snapshot(day, cutoff, at):
        value = snapshots.get((str(day), cutoff))
        return value if value and value['receivedAt'] <= at.isoformat() else None
    meta = SimpleNamespace(iter_jobs=lambda phase: (r for r in jobs.values() if r.get('phase') == phase), job=jobs.get)
    return build_weekly_diagnostics(SimpleNamespace(iter_history=lambda: iter(reports)), meta,
                                   SimpleNamespace(at=snapshot), through)


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--database', type=Path, required=True)
    parser.add_argument('--through', type=datetime.fromisoformat, required=True)
    parser.add_argument('--output', type=Path, required=True)
    args = parser.parse_args()
    if args.through.tzinfo is not None:
        parser.error('--through 使用无时区后缀的上海本地时间')
    result = audit(args.database, args.through)
    with args.output.open('x') as output:
        json.dump(result, output, ensure_ascii=False, allow_nan=False)
    print(json.dumps({'recordCount': result['recordCount'], 'pendingCount': result['pendingCount'],
                      'periodStart': result['periodStart'], 'periodEnd': result['periodEnd']}))
