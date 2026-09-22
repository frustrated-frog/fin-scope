import hashlib
import json
from pathlib import Path
import sqlite3

from finscope_market_data.overnight.models import MinuteBar


class OvernightStore:
    def __init__(self, path: Path):
        self.path = path
        path.parent.mkdir(parents=True, exist_ok=True)
        with self.connect() as db:
            db.executescript('''
                CREATE TABLE IF NOT EXISTS overnight_capture_plan (
                    id INTEGER PRIMARY KEY CHECK(id=1), payload TEXT NOT NULL);
                CREATE TABLE IF NOT EXISTS overnight_capture_run (
                    signal_date TEXT NOT NULL, cutoff TEXT NOT NULL, payload TEXT NOT NULL,
                    PRIMARY KEY(signal_date, cutoff));
                CREATE TABLE IF NOT EXISTS overnight_minute (
                    code TEXT NOT NULL, ended_at TEXT NOT NULL, payload TEXT NOT NULL,
                    PRIMARY KEY(code, ended_at));
                CREATE TABLE IF NOT EXISTS overnight_prediction (
                    id TEXT PRIMARY KEY, request_key TEXT UNIQUE NOT NULL,
                    generated_at TEXT NOT NULL, payload TEXT NOT NULL, inputs TEXT NOT NULL);
                CREATE TABLE IF NOT EXISTS overnight_outcome (
                    prediction_id TEXT NOT NULL, observed_at TEXT NOT NULL,
                    payload TEXT NOT NULL, PRIMARY KEY(prediction_id, observed_at));
            ''')

    def connect(self):
        return sqlite3.connect(self.path, timeout=15)

    def save_bars(self, code, bars):
        with self.connect() as db:
            db.executemany('INSERT INTO overnight_minute VALUES(?,?,?) ON CONFLICT(code,ended_at) DO UPDATE SET payload=excluded.payload',
                [(code, b.ended_at.isoformat(), b.model_dump_json()) for b in bars])

    def bars(self, code, through):
        with self.connect() as db:
            rows = db.execute('SELECT payload FROM overnight_minute WHERE code=? AND ended_at<=? ORDER BY ended_at',
                              (code, through.isoformat())).fetchall()
        return [MinuteBar.model_validate_json(row[0]) for row in rows]

    def freeze(self, request, report, inputs):
        key = json.dumps({'request': request.model_dump(mode='json', by_alias=True),
                          'modelVersion': report['modelVersion']}, sort_keys=True)
        identifier = hashlib.sha256(key.encode()).hexdigest()
        report = {**report, 'id': identifier, 'request': request.model_dump(mode='json', by_alias=True)}
        with self.connect() as db:
            db.execute('INSERT OR IGNORE INTO overnight_prediction VALUES(?,?,?,?,?)',
                (identifier, key, report['generatedAt'], json.dumps(report), json.dumps(inputs)))
            return json.loads(db.execute('SELECT payload FROM overnight_prediction WHERE id=?', (identifier,)).fetchone()[0])

    def iter_history(self, limit=None):
        # Stream every archive, including records older than the display window.
        query = """SELECT p.payload, o.payload FROM overnight_prediction p
                   LEFT JOIN overnight_outcome o ON o.prediction_id=p.id AND o.observed_at=(
                       SELECT MAX(observed_at) FROM overnight_outcome WHERE prediction_id=p.id)
                   ORDER BY p.generated_at DESC, p.id"""
        with self.connect() as db:
            cursor = db.execute(query + (' LIMIT ?' if limit is not None else ''),
                                (limit,) if limit is not None else ())
            for row in cursor:
                yield {**json.loads(row[0]), 'outcome': json.loads(row[1]) if row[1] else None}

    def history(self, limit=50):
        return list(self.iter_history(limit))

    def plan(self):
        with self.connect() as db:
            row = db.execute('SELECT payload FROM overnight_capture_plan WHERE id=1').fetchone()
        return json.loads(row[0]) if row else {'enabled': False, 'instrumentCodes': [], 'costBps': 20}

    def save_plan(self, plan):
        with self.connect() as db:
            db.execute('INSERT INTO overnight_capture_plan VALUES(1,?) ON CONFLICT(id) DO UPDATE SET payload=excluded.payload',
                       (json.dumps(plan),))
        return plan

    def claim_run(self, run):
        with self.connect() as db:
            return db.execute('INSERT OR IGNORE INTO overnight_capture_run VALUES(?,?,?)',
                (run['signalDate'], run['cutoff'], json.dumps(run))).rowcount == 1

    def finish_run(self, run):
        with self.connect() as db:
            db.execute('UPDATE overnight_capture_run SET payload=? WHERE signal_date=? AND cutoff=?',
                       (json.dumps(run), run['signalDate'], run['cutoff']))

    def runs(self, limit=30):
        with self.connect() as db:
            rows = db.execute('SELECT payload FROM overnight_capture_run ORDER BY signal_date DESC, cutoff DESC LIMIT ?',
                              (limit,)).fetchall()
        return [json.loads(row[0]) for row in rows]

    def outcome(self, identifier, now, result):
        with self.connect() as db:
            db.execute('INSERT OR IGNORE INTO overnight_outcome VALUES(?,?,?)',
                       (identifier, now.isoformat(), json.dumps(result)))
