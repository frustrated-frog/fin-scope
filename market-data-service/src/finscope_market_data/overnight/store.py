import hashlib
import json
from datetime import timedelta
from contextlib import closing, contextmanager
from pathlib import Path
import sqlite3

from finscope_market_data.overnight.models import MinuteBar
from finscope_market_data.overnight.history_quality import compare_history


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
                CREATE TABLE IF NOT EXISTS overnight_history_import (
                    code TEXT PRIMARY KEY, payload TEXT NOT NULL);
                CREATE TABLE IF NOT EXISTS overnight_historical_minute (
                    code TEXT NOT NULL, ended_at TEXT NOT NULL, payload TEXT NOT NULL,
                    PRIMARY KEY(code, ended_at));
                CREATE VIEW IF NOT EXISTS overnight_effective_minute AS
                    SELECT code, ended_at, payload FROM overnight_minute
                    UNION ALL
                    SELECT h.code, h.ended_at, h.payload FROM overnight_historical_minute h
                    WHERE NOT EXISTS (SELECT 1 FROM overnight_minute live WHERE live.code=h.code
                        AND live.ended_at>=substr(h.ended_at,1,10)||'T00:00:00'
                        AND live.ended_at<=substr(h.ended_at,1,10)||'T23:59:59');
            ''')

    @contextmanager
    def connect(self):
        with closing(sqlite3.connect(self.path, timeout=15)) as db:
            with db:
                yield db

    def save_bars(self, code, bars):
        with self.connect() as db:
            db.executemany('INSERT INTO overnight_minute VALUES(?,?,?) ON CONFLICT(code,ended_at) DO UPDATE SET payload=excluded.payload',
                [(code, b.ended_at.isoformat(), b.model_dump_json()) for b in bars])

    def bars(self, code, through):
        with self.connect() as db:
            rows = db.execute('SELECT payload FROM overnight_effective_minute WHERE code=? AND ended_at<=? ORDER BY ended_at',
                              (code, through.isoformat())).fetchall()
        return [MinuteBar.model_validate_json(row[0]) for row in rows]

    def coverage(self, through):
        with self.connect() as db:
            rows = db.execute('''SELECT code, MIN(day), MAX(day), SUM(bars),
                SUM(CASE WHEN bars=48 THEN 1 ELSE 0 END) FROM (
                    SELECT code, substr(ended_at,1,10) day, COUNT(*) bars FROM overnight_effective_minute
                    WHERE ended_at BETWEEN ? AND ? GROUP BY code, day) GROUP BY code ORDER BY MAX(day) DESC, code LIMIT 200''',
                ((through - timedelta(days=365)).isoformat(), through.isoformat())).fetchall()
        return [{'instrumentCode': row[0], 'firstDate': row[1], 'lastDate': row[2],
                 'barCount': row[3], 'completeDays': row[4]} for row in rows]

    def import_history(self, code, bars, source, now):
        """Stage history separately; never replace captured days or frozen reports."""
        with self.connect() as db:
            db.execute('BEGIN IMMEDIATE')
            existing = {row[0]: MinuteBar.model_validate_json(row[1]) for row in db.execute(
                'SELECT ended_at,payload FROM overnight_minute WHERE code=?', (code,))}
            days = {stamp[:10] for stamp in existing}
            quality = compare_history(existing, bars)
            previous_stamps = {row[0] for row in db.execute(
                'SELECT ended_at FROM overnight_historical_minute WHERE code=?', (code,))}
            additions = [bar for bar in bars if str(bar.ended_at.date()) not in days
                         and bar.ended_at.isoformat() not in previous_stamps]
            db.executemany('INSERT OR IGNORE INTO overnight_historical_minute VALUES(?,?,?)',
                [(code, bar.ended_at.isoformat(), bar.model_dump_json()) for bar in bars])
            result = {'sourceCode': source, 'importedAt': now.isoformat(), 'addedBars': len(additions),
                      'addedDays': len({bar.ended_at.date() for bar in additions}), **quality,
                      'firstDate': str(bars[0].ended_at.date()) if bars else None,
                      'lastDate': str(bars[-1].ended_at.date()) if bars else None}
            db.execute('INSERT INTO overnight_history_import VALUES(?,?) ON CONFLICT(code) DO UPDATE SET payload=excluded.payload',
                       (code, json.dumps(result)))
        return result

    def history_import(self, code):
        with self.connect() as db:
            row = db.execute('SELECT payload FROM overnight_history_import WHERE code=?', (code,)).fetchone()
        return json.loads(row[0]) if row else None

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
