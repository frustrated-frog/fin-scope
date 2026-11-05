"""Actual predecision cohorts; historical quotes are never reconstructed today."""
import hashlib
import json


def frozen_cohorts(meta, through, cutoff=None):
    result = {}
    for job in meta.iter_jobs('DISCOVER'):
        day, stamp = job.get('signalDate'), job.get('cutoff')
        if not day or stamp not in ('14:30', '14:45') or (cutoff and stamp != cutoff):
            continue
        decision = f'{day}T{stamp}:00'
        if (job.get('key') != f'{day}|SCAN|{stamp}' or job.get('status') != 'COMPLETED'
                or not job.get('snapshotAt') or job['snapshotAt'] >= decision or decision >= through):
            continue
        candidates = {r['instrumentCode']: r for r in job.get('candidates', [])}
        if not candidates:
            continue
        evidence = {key: job.get(key) for key in ('snapshotAt', 'sourceCode', 'scope', 'candidates', 'observations')}
        result[(day, stamp)] = {'candidates': candidates, 'snapshotAt': job['snapshotAt'],
            'source': job.get('sourceCode', 'UNKNOWN'),
            'fingerprint': hashlib.sha256(json.dumps(evidence, sort_keys=True, allow_nan=False).encode()).hexdigest()}
    return result


def mark_cohort(rows, cohorts, cutoff):
    """Mark only membership known at the decision; missing is not non-membership."""
    return [{**row, 'candidateMember': row['instrumentCode'] in cohorts[(row['signalDate'], cutoff)]['candidates']
             if (row['signalDate'], cutoff) in cohorts else None,
             'candidateExpectedCount': len(cohorts[(row['signalDate'], cutoff)]['candidates'])
             if (row['signalDate'], cutoff) in cohorts else 0} for row in rows]
