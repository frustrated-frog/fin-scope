import importlib.util
from pathlib import Path
import sqlite3
import json

import pytest
from finscope_market_data.snapshot_store import SnapshotStore
from test_joint_dataset import histories


spec = importlib.util.spec_from_file_location('general_experiment', Path(__file__).parents[1] / 'scripts/evaluate_general_next_session.py')
module = importlib.util.module_from_spec(spec)
spec.loader.exec_module(module)


def test_experiment_uses_read_only_connection_and_cuts_future_bars(tmp_path):
    path = tmp_path / 'cache.db'
    SnapshotStore(path)
    bars = histories()['000001'][-3:]
    with sqlite3.connect(path) as connection:
        connection.execute('INSERT INTO market_data_snapshot(capability,symbol_key,payload_json) VALUES (?,?,?)',
            ('DAILY_BARS', 'SZ:000001', json.dumps({'data': [b.model_dump(mode='json') for b in bars]})))
    store = module.ReadOnlyHistory(path)
    try:
        assert store.daily_bar_symbols() == ['SZ:000001']
        assert [b.trade_date for b in store.daily_history_as_of('SZ:000001', bars[1].trade_date)] == [b.trade_date for b in bars[:2]]
        with pytest.raises(sqlite3.OperationalError, match='readonly'):
            store.connection.execute('DELETE FROM market_data_snapshot')
    finally:
        store.connection.close()
