#!/usr/bin/env python3
"""Offline report repair. Dry-run by default; raw evidence is never modified."""
import argparse
from datetime import datetime
import json
import os
from pathlib import Path
import sqlite3
import subprocess
import tempfile


def project_vault_path(database, report):
    root = database.parent / 'vault'
    raw = report.get('markdown_path') or ''
    suffix = raw.replace('\\', '/').split('/vault/', 1)
    path = root / suffix[1] if len(suffix) == 2 else (
        root / 'research-reports' / ('thesis-' + str(report['thesis_id']))
        / ('run-' + str(report['research_run_id']) + '.md'))
    path = path.resolve()
    if not path.is_relative_to(root.resolve()):
        raise ValueError('Report path escapes the vault')
    return path


def atomic_write(path, value):
    path.parent.mkdir(parents=True, exist_ok=True)
    with tempfile.NamedTemporaryFile(mode='w', encoding='utf-8', dir=path.parent, delete=False) as stream:
        stream.write(value)
        temporary = stream.name
    os.replace(temporary, path)


def apply_plan(database, plan, backup):
    connection = sqlite3.connect(database, timeout=10)
    connection.row_factory = sqlite3.Row
    connection.execute('PRAGMA foreign_keys=ON')
    restored_files = []
    try:
        connection.execute('BEGIN IMMEDIATE')
        originals = []
        for entry in plan:
            old = entry['original']
            current = connection.execute('SELECT * FROM research_report WHERE id=?', (old['id'],)).fetchone()
            if current is None or dict(current) != old:
                raise ValueError('Report changed after preview: ' + str(old['id']))
            run_id = old['research_run_id']
            run = connection.execute('SELECT status FROM research_run WHERE id=?', (run_id,)).fetchone()
            if run is None or run['status'] == 'RUNNING':
                raise ValueError('Research run is missing or active: ' + str(run_id))
            thesis = connection.execute('SELECT * FROM research_thesis WHERE id=?', (old['thesis_id'],)).fetchone()
            findings = connection.execute('SELECT * FROM thesis_finding WHERE research_run_id=?', (run_id,)).fetchall()
            path = project_vault_path(database, old)
            content = path.read_text(encoding='utf-8') if path.exists() else None
            originals.append({'report': old, 'thesis': dict(thesis) if thesis else None,
                              'findings': [dict(row) for row in findings], 'file': str(path), 'content': content})
        # Keep a durable row + file snapshot before any writes. The backup is outside the repository.
        atomic_write(backup / 'originals.json', json.dumps(originals, ensure_ascii=False, indent=2))
        now = datetime.now().isoformat(timespec='seconds')
        for entry, original in zip(plan, originals):
            old = entry['original']
            replacement = dict(entry['replacement'])
            path = Path(original['file'])
            restored_files.append((path, original['content']))
            atomic_write(path, replacement['content_markdown'])
            replacement['markdown_path'] = str(path)
            replacement['updated_at'] = now
            allowed = {'status', 'conclusion', 'conclusion_direction', 'confidence', 'executive_summary',
                       'content_markdown', 'generation_mode', 'warning_message', 'evidence_count',
                       'source_count', 'character_count', 'markdown_path', 'updated_at'}
            if set(replacement) != allowed:
                raise ValueError('Invalid replacement fields')
            fields = list(replacement)
            cursor = connection.execute('UPDATE research_report SET ' + ','.join(f + '=?' for f in fields)
                                        + ' WHERE id=? AND updated_at=?',
                                        [replacement[f] for f in fields] + [old['id'], old['updated_at']])
            if cursor.rowcount != 1:
                raise ValueError('Concurrent report update')
            connection.execute('DELETE FROM thesis_finding WHERE research_run_id=?', (old['research_run_id'],))
            for finding in entry['findings']:
                connection.execute('INSERT INTO thesis_finding(thesis_id,research_run_id,stance,summary,evidence_id,created_at,updated_at) '
                                   'VALUES(?,?,?,?,?,?,?)', (old['thesis_id'], old['research_run_id'], finding['stance'],
                                                          finding['summary'], finding['evidence_id'], now, now))
            latest = connection.execute('SELECT MAX(id) FROM research_run WHERE thesis_id=?', (old['thesis_id'],)).fetchone()[0]
            if latest == old['research_run_id']:
                cursor = connection.execute('UPDATE research_thesis SET conclusion=?,confidence=?,updated_at=? WHERE id=?',
                                           (replacement['conclusion'], replacement['confidence'], now, old['thesis_id']))
                if cursor.rowcount != 1:
                    raise ValueError('Thesis update failed')
        connection.commit()
    except BaseException:
        connection.rollback()
        for path, content in reversed(restored_files):
            if content is None:
                path.unlink(missing_ok=True)
            else:
                atomic_write(path, content)
        raise
    finally:
        connection.close()


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--database', type=Path, required=True)
    parser.add_argument('--classpath-file', type=Path, required=True)
    parser.add_argument('--java', default='java')
    parser.add_argument('--run-id', type=int, action='append')
    parser.add_argument('--apply', action='store_true')
    args = parser.parse_args()
    database = args.database.resolve(strict=True)
    backup = database.parent / 'exports' / ('research-report-repair-' + datetime.now().strftime('%Y%m%d-%H%M%S-%f'))
    backup.mkdir(parents=True, mode=0o700)
    root = Path(__file__).resolve().parents[1]
    modules = ['common', 'domain', 'dao', 'rpc', 'service', 'web']
    classpath = os.pathsep.join(str(root / 'backend' / ('finscope-' + module) / 'target/classes') for module in modules)
    classpath += os.pathsep + args.classpath_file.read_text().strip()
    preview = backup / 'preview.json'
    with (backup / 'preview.log').open('w') as log:
        command = [args.java, '-cp', classpath, 'com.finscope.web.maintenance.ResearchReportRepairPreview',
                   str(database), str(preview)]
        if args.run_id:
            command.append(','.join(str(value) for value in args.run_id))
        subprocess.run(command, stdout=log, stderr=subprocess.STDOUT, check=True)
    plan = json.loads(preview.read_text())['plans']
    if args.run_id:
        plan = [entry for entry in plan if entry['original']['research_run_id'] in args.run_id]
    if args.apply and plan:
        apply_plan(database, plan, backup)
    print(json.dumps({'applied': args.apply, 'backup': str(backup), 'reports': [
        {'id': entry['original']['id'], 'runId': entry['original']['research_run_id'],
         'status': entry['replacement']['status'], 'evidence': entry['replacement']['evidence_count']}
        for entry in plan]}, ensure_ascii=False, indent=2))


if __name__ == '__main__':
    main()
