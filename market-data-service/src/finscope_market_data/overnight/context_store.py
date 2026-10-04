"""Immutable intraday context, separate from replaceable end-of-day snapshots."""
from datetime import datetime
import hashlib
import json


class OvernightContextStore:
    def __init__(self, store):
        self.store = store
        with store.connect() as db:
            db.execute('''CREATE TABLE IF NOT EXISTS overnight_context_snapshot (
                signal_date TEXT NOT NULL, cutoff TEXT NOT NULL, received_at TEXT NOT NULL,
                payload TEXT NOT NULL, PRIMARY KEY(signal_date, cutoff))''')

    def freeze(self, value):
        decision = f"{value['signalDate']}T{value['cutoff']}:00"
        if value['receivedAt'] > decision or value['observedAt'] >= decision:
            raise ValueError('环境快照未在预测截止前收到')
        for row in list(value['pool'].values()) + list(value.get('indices', {}).values()):
            if not value['observedAt'] <= row['observedAt'] <= row['receivedAt'] <= value['receivedAt']:
                raise ValueError('环境数据的行情或接收时间无效')
        digest = hashlib.sha256(json.dumps(value, sort_keys=True, allow_nan=False).encode()).hexdigest()
        payload = json.dumps({**value, 'fingerprint': digest}, allow_nan=False)
        with self.store.connect() as db:
            db.execute('INSERT OR IGNORE INTO overnight_context_snapshot VALUES(?,?,?,?)',
                       (value['signalDate'], value['cutoff'], value['receivedAt'], payload))
        return self.at(value['signalDate'], value['cutoff'], datetime.fromisoformat(decision))

    def at(self, day, cutoff, through):
        with self.store.connect() as db:
            row = db.execute('''SELECT payload FROM overnight_context_snapshot
                WHERE signal_date=? AND cutoff=? AND received_at<=?''', (str(day), cutoff, through.isoformat())).fetchone()
        return json.loads(row[0]) if row else None

    def recent(self, limit=6):
        with self.store.connect() as db:
            rows = db.execute('SELECT payload FROM overnight_context_snapshot ORDER BY signal_date DESC, cutoff DESC LIMIT ?',
                              (limit,)).fetchall()
        return [{key: value for key, value in json.loads(row[0]).items() if key not in ('pool', 'indices', 'memberships')}
                for row in rows]
