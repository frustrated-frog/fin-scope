"""Durable leases and audit records for the automatic research workflow."""
import json
from datetime import datetime, timedelta
from uuid import uuid4


class AutomationStore:
    def __init__(self, overnight_store):
        self.store = overnight_store
        with self.store.connect() as db:
            db.executescript('''
                CREATE TABLE IF NOT EXISTS overnight_automation_meta (
                    key TEXT PRIMARY KEY, payload TEXT NOT NULL);
                CREATE TABLE IF NOT EXISTS overnight_automation_job (
                    key TEXT PRIMARY KEY, payload TEXT NOT NULL);
            ''')

    def get(self, key):
        with self.store.connect() as db:
            row = db.execute('SELECT payload FROM overnight_automation_meta WHERE key=?', (key,)).fetchone()
        return json.loads(row[0]) if row else None

    def put(self, key, value):
        with self.store.connect() as db:
            db.execute('INSERT INTO overnight_automation_meta VALUES(?,?) ON CONFLICT(key) DO UPDATE SET payload=excluded.payload',
                       (key, json.dumps(value)))

    def jobs(self, limit=100):
        with self.store.connect() as db:
            rows = db.execute('SELECT payload FROM overnight_automation_job ORDER BY key DESC LIMIT ?', (limit,)).fetchall()
        return [json.loads(row[0]) for row in rows]

    def job(self, key):
        with self.store.connect() as db:
            row = db.execute('SELECT payload FROM overnight_automation_job WHERE key=?', (key,)).fetchone()
        return json.loads(row[0]) if row else None

    def claim(self, key, now, metadata):
        with self.store.connect() as db:
            db.execute('BEGIN IMMEDIATE')
            row = db.execute('SELECT payload FROM overnight_automation_job WHERE key=?', (key,)).fetchone()
            old = json.loads(row[0]) if row else {}
            if old:
                age = now - datetime.fromisoformat(old['startedAt'])
                if old['status'] not in {'FAILED', 'RUNNING'} or old['attempts'] >= 3:
                    return None
                if age < timedelta(seconds=60) or (old['status'] == 'RUNNING' and age < timedelta(minutes=10)):
                    return None
            value = {**metadata, 'key': key, 'status': 'RUNNING', 'startedAt': now.isoformat(),
                     'attempts': old.get('attempts', 0) + 1, 'token': uuid4().hex}
            db.execute('INSERT INTO overnight_automation_job VALUES(?,?) ON CONFLICT(key) DO UPDATE SET payload=excluded.payload',
                       (key, json.dumps(value)))
            return value

    def finish(self, job, now, **result):
        with self.store.connect() as db:
            db.execute('BEGIN IMMEDIATE')
            row = db.execute('SELECT payload FROM overnight_automation_job WHERE key=?', (job['key'],)).fetchone()
            current = json.loads(row[0]) if row else {}
            if current.get('token') == job['token'] and current.get('status') == 'RUNNING':
                db.execute('UPDATE overnight_automation_job SET payload=? WHERE key=?',
                           (json.dumps({**job, **result, 'finishedAt': now.isoformat()}), job['key']))

    def miss(self, key, now, metadata):
        value = {**metadata, 'key': key, 'status': 'MISSED', 'startedAt': now.isoformat(),
                 'finishedAt': now.isoformat(), 'attempts': 0, 'reason': '服务未在有效窗口内完成；不回填盘后数据'}
        with self.store.connect() as db:
            db.execute('INSERT OR IGNORE INTO overnight_automation_job VALUES(?,?)', (key, json.dumps(value)))
