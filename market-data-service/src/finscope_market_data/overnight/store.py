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

    def history(self, limit=50):
        with self.connect() as db:
            rows = db.execute('SELECT payload FROM overnight_prediction ORDER BY generated_at DESC LIMIT ?', (limit,)).fetchall()
            result = []
            for row in rows:
                report = json.loads(row[0])
                outcome = db.execute('SELECT payload FROM overnight_outcome WHERE prediction_id=? ORDER BY observed_at DESC LIMIT 1', (report['id'],)).fetchone()
                result.append({**report, 'outcome': json.loads(outcome[0]) if outcome else None})
        return result

    def outcome(self, identifier, now, result):
        with self.connect() as db:
            db.execute('INSERT OR IGNORE INTO overnight_outcome VALUES(?,?,?)',
                       (identifier, now.isoformat(), json.dumps(result)))
