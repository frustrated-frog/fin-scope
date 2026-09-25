import sqlite3

import pytest

from finscope_market_data.discovery.recall_archive import load_outcome_inputs
from finscope_market_data.overnight.store import OvernightStore
from finscope_market_data.snapshot_store import SnapshotStore


def _record_connections(monkeypatch):
    original_connect = sqlite3.connect
    connections = []

    def connect(*args, **kwargs):
        connection = original_connect(*args, **kwargs)
        connections.append(connection)
        return connection

    monkeypatch.setattr(sqlite3, 'connect', connect)
    return connections


def _assert_closed(connections):
    assert connections
    for connection in connections:
        with pytest.raises(sqlite3.ProgrammingError, match='closed database'):
            connection.execute('SELECT 1')


def test_overnight_store_closes_connections_after_reads_writes_and_rollback(tmp_path, monkeypatch):
    connections = _record_connections(monkeypatch)
    store = OvernightStore(tmp_path / 'overnight.db')
    store.save_plan({'enabled': False})
    assert store.plan()['enabled'] is False

    with pytest.raises(RuntimeError):
        with store.connect() as connection:
            connection.execute("UPDATE overnight_capture_plan SET payload='{}' WHERE id=1")
            raise RuntimeError('abort transaction')

    assert store.plan() == {'enabled': False}
    _assert_closed(connections)


def test_snapshot_store_closes_connections_after_initialization_and_reads(tmp_path, monkeypatch):
    connections = _record_connections(monkeypatch)
    store = SnapshotStore(tmp_path / 'snapshots.db')

    assert store.daily_bar_revision() == 0
    _assert_closed(connections)


def test_discovery_archive_closes_read_only_connection(tmp_path, monkeypatch):
    path = tmp_path / 'snapshots.db'
    with sqlite3.connect(path) as connection:
        connection.execute('CREATE TABLE market_data_snapshot (capability TEXT, symbol_key TEXT, payload_json TEXT)')
    connection.close()
    connections = _record_connections(monkeypatch)

    result = load_outcome_inputs(path, '2026-09-01', '2026-09-02')

    assert result == {'histories': {}, 'calendar': []}
    _assert_closed(connections)
