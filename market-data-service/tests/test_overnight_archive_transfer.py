from datetime import datetime
import json
import sqlite3

import pytest

from finscope_market_data.overnight.store import OvernightStore
from finscope_market_data.overnight.archive_transfer import export_archive, transfer


def insert(store, identifier='one', value='original'):
    with store.connect() as db:
        db.execute('INSERT INTO overnight_prediction VALUES(?,?,?,?,?)',
            (identifier, identifier, '2026-10-08T14:30:01', json.dumps({'id': identifier,
             'generatedAt': '2026-10-08T14:30:01', 'value': value}), '[]'))
    store.outcome(identifier, datetime(2026, 10, 9, 16), {'status': 'SETTLED'})


def test_dry_run_idempotent_merge_and_backup_preserve_original_evidence(tmp_path):
    source, target = OvernightStore(tmp_path / 'old.db'), OvernightStore(tmp_path / 'current.db')
    insert(source)
    insert(target, 'today')
    assert transfer(source.path, target.path)['predictionsToAdd'] == 1
    assert len(target.history()) == 1
    result = transfer(source.path, target.path, apply=True)
    assert result['predictionsToAdd'] == result['outcomesToAdd'] == 1
    assert len(target.history()) == 2
    with sqlite3.connect(result['backup']) as db:
        assert db.execute('SELECT COUNT(*) FROM overnight_prediction').fetchone()[0] == 1
    assert transfer(source.path, target.path, apply=True)['predictionsToAdd'] == 0
    assert len(source.history()) == 1


def test_conflicting_prediction_rolls_back_the_entire_import(tmp_path):
    source, target = OvernightStore(tmp_path / 'old.db'), OvernightStore(tmp_path / 'current.db')
    insert(source, 'new')
    insert(source, 'conflict')
    insert(target, 'conflict', 'different')
    with pytest.raises(ValueError, match='冲突'):
        transfer(source.path, target.path, apply=True)
    assert len(target.history()) == 1


def test_export_copies_only_evidence_and_refuses_to_overwrite(tmp_path):
    source, target = OvernightStore(tmp_path / 'source.db'), OvernightStore(tmp_path / 'target.db')
    insert(source)
    exported = tmp_path / 'archive.db'
    export_archive(source.path, exported)
    with sqlite3.connect(exported) as db:
        assert {row[0] for row in db.execute("SELECT name FROM sqlite_master WHERE type='table'")} == {
            'overnight_prediction', 'overnight_outcome'}
    assert transfer(exported, target.path, apply=True)['predictionsToAdd'] == 1
    assert target.history() == source.history()
    with pytest.raises(ValueError, match='不能覆盖'):
        export_archive(source.path, exported)
