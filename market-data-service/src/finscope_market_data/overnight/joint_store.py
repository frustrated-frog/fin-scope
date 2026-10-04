"""Immutable, compressed training evidence and atomic model publication."""
from datetime import timedelta
import gzip
import hashlib
import json


class OvernightJointStore:
    def __init__(self, store):
        self.store = store
        with store.connect() as db:
            db.executescript('''
                CREATE TABLE IF NOT EXISTS overnight_joint_artifact (
                    id TEXT PRIMARY KEY, profile_key TEXT NOT NULL, created_at TEXT NOT NULL,
                    labels_through TEXT NOT NULL, payload TEXT NOT NULL, panel BLOB NOT NULL);
                CREATE INDEX IF NOT EXISTS overnight_joint_profile
                    ON overnight_joint_artifact(profile_key, created_at DESC);
            ''')

    def publish(self, artifact, panel, claim=None):
        if artifact['labelsThrough'] >= artifact['createdAt']:
            raise ValueError('训练标签尚未到期')
        fitted_models = list(artifact['targets'].values())
        for name in ('closeDirection', 'contextDirection'):
            if artifact.get(name):
                fitted_models.append(artifact[name])
        for target in fitted_models:
            if any(target['audit'][key] > artifact['labelsThrough']
                   for key in ('trainingThrough', 'calibrationThrough', 'testThrough')):
                raise ValueError('模型标签时点与清单不一致')
        # Hash the actual coefficients, context and full data fingerprint, not a display version.
        payload = json.dumps(artifact, sort_keys=True, separators=(',', ':'), allow_nan=False)
        identifier = hashlib.sha256(payload.encode()).hexdigest()
        value = {**artifact, 'id': identifier}
        compressed = gzip.compress(json.dumps(panel, separators=(',', ':'), allow_nan=False).encode(), compresslevel=3)
        with self.store.connect() as db:
            db.execute('BEGIN IMMEDIATE')
            if claim:
                row = db.execute('SELECT payload FROM overnight_automation_job WHERE key=?', (claim['key'],)).fetchone()
                current = json.loads(row[0]) if row else {}
                if current.get('token') != claim['token'] or current.get('status') != 'RUNNING':
                    raise ValueError('联合训练任务租约已更新，旧任务不能发布模型')
            db.execute('INSERT OR IGNORE INTO overnight_joint_artifact VALUES(?,?,?,?,?,?)',
                (identifier, artifact['profile']['key'], artifact['createdAt'], artifact['labelsThrough'],
                 json.dumps(value, allow_nan=False), compressed))
        return value

    def for_job(self, key):
        with self.store.connect() as db:
            row = db.execute("SELECT id FROM overnight_joint_artifact WHERE json_extract(payload, '$.trainingJobKey')=? LIMIT 1",
                             (key,)).fetchone()
        return row[0] if row else None

    def latest(self, profile_key, as_of):
        with self.store.connect() as db:
            row = db.execute('''SELECT payload FROM overnight_joint_artifact
                WHERE profile_key=? AND created_at<=? AND labels_through<? AND created_at>=?
                ORDER BY created_at DESC, id LIMIT 1''',
                (profile_key, as_of.isoformat(), as_of.isoformat(), (as_of - timedelta(days=14)).isoformat())).fetchone()
        return json.loads(row[0]) if row else None

    def summaries(self, now):
        with self.store.connect() as db:
            keys = [row[0] for row in db.execute('SELECT DISTINCT profile_key FROM overnight_joint_artifact')]
        result = []
        for key in keys:
            artifact = self.latest(key, now)
            if artifact:
                directions = {name: {key: value for key, value in artifact[name].items() if key != 'model'}
                              if artifact.get(name) else None for name in ('closeDirection', 'contextDirection')}
                result.append({key: value for key, value in artifact.items() if key not in ('targets', 'context', *directions)} | {
                    'targets': [{'target': target, **value['audit']} for target, value in artifact['targets'].items()],
                    **directions})
        return result
