"""Merge frozen prediction/outcome evidence between local and Docker stores.

Dry run by default. Never import models, mutate original predictions or merge
minute bars from different installations. Source files are opened read-only.
"""
import argparse
from contextlib import closing
from datetime import datetime, timezone
import json
from pathlib import Path
import sqlite3

TABLES = {'overnight_prediction': ('id', 'request_key', 'generated_at', 'payload', 'inputs'),
          'overnight_outcome': ('prediction_id', 'observed_at', 'payload')}


def export_archive(source: Path, destination: Path):
    if destination.exists() or not source.is_file():
        raise ValueError('源库必须存在，导出文件不能覆盖已有文件')
    with closing(sqlite3.connect(source.resolve().as_uri() + '?mode=ro', uri=True)) as src:
        src.execute('BEGIN')
        with closing(sqlite3.connect(destination)) as dst:
            with dst:
                for table, columns in TABLES.items():
                    schema = src.execute("SELECT sql FROM sqlite_master WHERE type='table' AND name=?", (table,)).fetchone()
                    dst.execute(schema[0])
                    # Fixed table/column allowlist; no user-provided SQL identifiers.
                    query = f"SELECT {','.join(columns)} FROM {table}"
                    dst.executemany(f"INSERT INTO {table} VALUES({','.join('?' for _ in columns)})", src.execute(query))
    return {'exported': str(destination)}


def transfer(source: Path, target: Path, *, apply=False):
    source, target = source.resolve(), target.resolve()
    if source == target or not source.is_file() or not target.is_file():
        raise ValueError('源库和目标库必须是两个已存在的数据库')
    with closing(sqlite3.connect(source.as_uri() + '?mode=ro', uri=True)) as src:
        src.execute('BEGIN')
        predictions = src.execute('SELECT id, request_key, generated_at, payload, inputs FROM overnight_prediction').fetchall()
        outcomes = src.execute('SELECT prediction_id, observed_at, payload FROM overnight_outcome').fetchall()
    # Parse before obtaining the writer lock; invalid source evidence cannot partially import.
    for row in predictions:
        report = json.loads(row[3])
        if report.get('id') != row[0] or report.get('generatedAt') != row[2]:
            raise ValueError('预测标识或生成时间与原始记录不一致')
        json.loads(row[4])
    for row in outcomes:
        json.loads(row[2])
    mode = 'rw' if apply else 'ro'
    with closing(sqlite3.connect(target.as_uri() + f'?mode={mode}', uri=True, timeout=30)) as dst:
        backup = None
        if apply:
            backup = target.with_name(target.name + '.' + datetime.now(timezone.utc).strftime('%Y%m%dT%H%M%S%f') + '.bak')
            with closing(sqlite3.connect(backup)) as copy:
                dst.backup(copy)
            dst.execute('BEGIN IMMEDIATE')
        try:
            accepted = set(row[0] for row in dst.execute('SELECT id FROM overnight_prediction'))
            additions, new_outcomes = [], []
            for row in predictions:
                existing = dst.execute('SELECT id, request_key, generated_at, payload, inputs FROM overnight_prediction WHERE id=? OR request_key=?', row[:2]).fetchall()
                if existing:
                    if len(existing) != 1 or existing[0][:3] != row[:3] or any(json.loads(existing[0][i]) != json.loads(row[i]) for i in (3, 4)):
                        raise ValueError(f'预测冲突，已停止合并：{row[0]}')
                else:
                    additions.append(row)
                    accepted.add(row[0])
            for row in outcomes:
                if row[0] not in accepted:
                    raise ValueError('结果缺少对应的原始预测')
                existing = dst.execute('SELECT payload FROM overnight_outcome WHERE prediction_id=? AND observed_at=?', row[:2]).fetchone()
                if existing and json.loads(existing[0]) != json.loads(row[2]):
                    raise ValueError(f'结果冲突，已停止合并：{row[0]}')
                if not existing:
                    new_outcomes.append(row)
            if apply:
                dst.executemany('INSERT INTO overnight_prediction VALUES(?,?,?,?,?)', additions)
                dst.executemany('INSERT INTO overnight_outcome VALUES(?,?,?)', new_outcomes)
                dst.commit()
            return {'applied': apply, 'predictionsToAdd': len(additions), 'outcomesToAdd': len(new_outcomes),
                    'duplicates': len(predictions) - len(additions), 'backup': str(backup) if backup else None}
        except Exception:
            if apply:
                dst.rollback()
            raise


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--source', type=Path, required=True)
    parser.add_argument('--target', type=Path)
    parser.add_argument('--export', type=Path, dest='export_path')
    parser.add_argument('--apply', action='store_true')
    args = parser.parse_args()
    if args.export_path and not args.target and not args.apply:
        result = export_archive(args.source, args.export_path)
    elif args.target and not args.export_path:
        result = transfer(args.source, args.target, apply=args.apply)
    else:
        parser.error('请选择 --export 或 --target [--apply]')
    print(json.dumps(result, ensure_ascii=False))
