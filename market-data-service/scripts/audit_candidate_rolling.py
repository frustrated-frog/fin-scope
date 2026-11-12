"""Read-only candidate replay using a frozen training panel and archived minute prices."""
import argparse
from datetime import datetime
import gzip
import json
from pathlib import Path
import sqlite3
from types import SimpleNamespace

from finscope_market_data.overnight.candidate_readiness import build_actual_candidate_panel
from finscope_market_data.overnight.candidate_rolling import fit_rolling_candidates
from finscope_market_data.overnight.models import MinuteBar


def audit(database, through, cutoff):
    with sqlite3.connect(database.resolve().as_uri() + '?mode=ro', uri=True) as db:
        db.execute('PRAGMA query_only=ON')
        db.execute('BEGIN')
        source = db.execute('''SELECT payload,panel FROM overnight_joint_artifact
            WHERE json_extract(payload,'$.profile.cutoff')=? AND created_at<?
            ORDER BY created_at DESC,id LIMIT 1''', (cutoff, through.isoformat())).fetchone()
        if not source:
            raise ValueError('没有可用的原始训练档案')
        artifact, panel = json.loads(source[0]), json.loads(gzip.decompress(source[1]))
        cohorts = {(r['signalDate'], r['cutoff']): r for r in panel['selectionCohorts']}
        def bars(code, at):
            return [MinuteBar.model_validate_json(row[0]) for row in db.execute(
                'SELECT payload FROM overnight_effective_minute WHERE code=? AND ended_at<=? ORDER BY ended_at',
                (code, at.isoformat()))]
        rows, readiness = build_actual_candidate_panel(panel['directionRows'], SimpleNamespace(bars=bars),
                                                       cohorts, cutoff, through)
    fitted = fit_rolling_candidates(rows, cohorts, readiness, through.isoformat(), cutoff)
    return {'sourceArtifact': artifact['id'], 'sourceCreatedAt': artifact['createdAt'],
        'through': through.isoformat(), 'readiness': readiness,
        'previousSelection': (artifact.get('upSelection') or {}).get('audit', {}).get('selection'),
        'newAudit': fitted['audit'] if fitted else None,
        'scope': '只读历史开发诊断；原训练池固定，补读历史价格不代表当时已有新模型预测'}


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--database', type=Path, required=True)
    parser.add_argument('--through', type=datetime.fromisoformat, required=True)
    parser.add_argument('--cutoff', choices=['14:30', '14:45'], required=True)
    parser.add_argument('--output', type=Path, required=True)
    args = parser.parse_args()
    if args.through.tzinfo is not None:
        parser.error('--through 使用上海本地时间，不附加时区')
    result = audit(args.database, args.through, args.cutoff)
    with args.output.open('x') as out:
        json.dump(result, out, ensure_ascii=False, allow_nan=False)
    print(json.dumps({'completeDays': result['readiness']['completeDays'],
        'reasons': result['readiness']['reasonCounts'],
        'selection': (result['newAudit'] or {}).get('selection', {}).get('reason')}))
