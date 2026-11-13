"""Read-only candidate replay using a frozen training panel and archived minute prices."""
import argparse
from contextlib import contextmanager
from datetime import datetime
import gzip
import hashlib
import json
from pathlib import Path
import sqlite3
from types import SimpleNamespace

from finscope_market_data.overnight.candidate_readiness import build_actual_candidate_panel
from finscope_market_data.overnight.candidate_rolling import fit_rolling_candidates
from finscope_market_data.overnight.action_verification import ActionVerificationStore
from finscope_market_data.overnight.store import OvernightStore


SNAPSHOT_PROTOCOL = 'overnight-candidate-replay-input-v1'


def fingerprint(evidence):
    return hashlib.sha256(json.dumps(evidence, sort_keys=True, allow_nan=False).encode()).hexdigest()


def replay(evidence):
    value = {key: item for key, item in evidence.items() if key != 'inputFingerprint'}
    if evidence.get('protocol') != SNAPSHOT_PROTOCOL or evidence.get('inputFingerprint') != fingerprint(value):
        raise ValueError('候选验证输入快照版本或指纹无效')
    cohorts = {(r['signalDate'], r['cutoff']): r for r in value['cohorts']}
    fitted = fit_rolling_candidates(value['rows'], cohorts, value['readiness'], value['through'], value['cutoff'])
    return {key: value[key] for key in ('sourceArtifact', 'sourceCreatedAt', 'through', 'cutoff',
                                       'readiness', 'actionVerification', 'previousSelection')} | {
        'inputFingerprint': evidence['inputFingerprint'], 'newAudit': fitted['audit'] if fitted else None,
        'scope': '只读历史开发诊断；真实候选按时间参加拟合，补读历史价格不代表当时已有新模型预测'}


def audit(database, through, cutoff, *, snapshot_output=None):
    with sqlite3.connect(database.resolve().as_uri() + '?mode=ro', uri=True) as db:
        db.execute('PRAGMA query_only=ON')
        db.execute('BEGIN')
        source = db.execute('''SELECT payload,panel FROM overnight_joint_artifact
            WHERE json_extract(payload,'$.profile.cutoff')=? AND created_at<?
            AND COALESCE(json_extract(payload,'$.trainingStatus'),'COMPLETED')='COMPLETED'
            ORDER BY created_at DESC,id LIMIT 1''', (cutoff, through.isoformat())).fetchone()
        if not source:
            raise ValueError('没有可用的原始训练档案')
        artifact, panel = json.loads(source[0]), json.loads(gzip.decompress(source[1]))
        cohorts = {(r['signalDate'], r['cutoff']): r for r in panel['selectionCohorts']}
        @contextmanager
        def connect():
            # Share this read transaction; never invoke a store schema constructor.
            yield db

        source_store = SimpleNamespace(connect=connect)
        def bars(code, at):
            return OvernightStore.research_bars(source_store, code, at)

        def latest_action(code, at):
            row = db.execute('SELECT payload FROM overnight_action_verification WHERE code=? AND received_at<=? ORDER BY received_at DESC LIMIT 1',
                             (code, at.isoformat())).fetchone()
            return json.loads(row[0]) if row else None

        base_rows, actions = ActionVerificationStore.annotate(SimpleNamespace(latest=latest_action), panel['directionRows'], through)
        rows, readiness = build_actual_candidate_panel(base_rows, SimpleNamespace(bars=bars),
            cohorts, cutoff, through,
            annotate=lambda extra, at: ActionVerificationStore.annotate(SimpleNamespace(latest=latest_action), extra, at))
    evidence = {'protocol': SNAPSHOT_PROTOCOL, 'sourceArtifact': artifact['id'], 'sourceCreatedAt': artifact['createdAt'],
        'through': through.isoformat(), 'cutoff': cutoff, 'readiness': readiness, 'actionVerification': actions,
        'previousSelection': (artifact.get('upSelection') or {}).get('audit', {}).get('selection'),
        'rows': rows, 'cohorts': [{'signalDate': day, 'cutoff': stamp, **cohort} for (day, stamp), cohort in cohorts.items()]}
    evidence['inputFingerprint'] = fingerprint(evidence)
    if snapshot_output is not None:
        with snapshot_output.open('xb') as out:
            out.write(gzip.compress(json.dumps(evidence, allow_nan=False).encode(), mtime=0))
    return replay(evidence)


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    source = parser.add_mutually_exclusive_group(required=True)
    source.add_argument('--database', type=Path)
    source.add_argument('--input-snapshot', type=Path)
    parser.add_argument('--snapshot-output', type=Path)
    parser.add_argument('--through', type=datetime.fromisoformat)
    parser.add_argument('--cutoff', choices=['14:30', '14:45'])
    parser.add_argument('--output', type=Path, required=True)
    args = parser.parse_args()
    if args.database:
        if not args.through or not args.cutoff or args.through.tzinfo is not None:
            parser.error('数据库验证必须提供 --cutoff 和上海本地时间 --through，不附加时区')
        result = audit(args.database, args.through, args.cutoff, snapshot_output=args.snapshot_output)
    else:
        if args.through or args.cutoff or args.snapshot_output:
            parser.error('离线复算使用快照内的时间和时点，不接受重定义')
        result = replay(json.loads(gzip.decompress(args.input_snapshot.read_bytes())))
    with args.output.open('x') as out:
        json.dump(result, out, ensure_ascii=False, allow_nan=False)
    training = (result['newAudit'] or {}).get('candidateTraining') or {}
    comparison = (result['newAudit'] or {}).get('candidateComparison') or {}
    print(json.dumps({'completeDays': result['readiness']['completeDays'],
        'reasons': result['readiness']['reasonCounts'],
        'trainingStatus': training.get('status'), 'trainingRows': training.get('trainingRows'),
        'pairedDays': comparison.get('dayCount'), 'evidenceKind': 'RETROSPECTIVE',
        'publicPoolSelection': (result['newAudit'] or {}).get('selection', {}).get('reason')}))
