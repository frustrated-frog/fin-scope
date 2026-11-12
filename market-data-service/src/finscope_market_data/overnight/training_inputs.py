"""Data revision and bounded refresh policy for shared overnight training."""
from datetime import datetime, timedelta
import hashlib
import json

from finscope_market_data.overnight.candidate_cohort import frozen_cohorts

REVISION = 'action-aware-data-refresh-v1'
RETRAIN_INTERVAL = timedelta(minutes=30)
GAP_KEY = 'candidateSampleGaps'


def input_revision(store, meta, universe, index_history, through):
    evidence = {'policy': REVISION, 'minutesAndActions': store.data_revision(),
                'universe': universe.get('fingerprint'), 'indices': index_history,
                'cohorts': sorted(value['fingerprint'] for value in frozen_cohorts(meta, through).values())}
    return hashlib.sha256(json.dumps(evidence, sort_keys=True, allow_nan=False).encode()).hexdigest()


def refresh_allowed(jobs, profile, revision, now):
    previous = [job for job in jobs if job.get('key', '').endswith(profile.key) and job.get('inputRevision')]
    if not previous:
        return True
    latest = max(previous, key=lambda job: job['startedAt'])
    if latest['inputRevision'] == revision:
        return True
    return now - datetime.fromisoformat(latest.get('finishedAt') or latest['startedAt']) >= RETRAIN_INTERVAL


def gap_codes(meta):
    result = []
    for cutoff in ('14:30', '14:45'):
        audit = meta.get(f'{GAP_KEY}|{cutoff}') or {}
        result.extend(r['instrumentCode'] for r in audit.get('records', [])
                      if r['reason'] in ('MISSING_OUTCOME', 'MISSING_DECISION_BARS', 'MISSING_PAST_FEATURES'))
    return list(dict.fromkeys(result))
