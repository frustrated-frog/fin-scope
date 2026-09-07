from datetime import datetime
from finscope_market_data.models import DailyBar, DataCapability, DataEnvelope, QualityStatus, StockSymbol
from finscope_market_data.snapshot_store import SnapshotStore
from finscope_market_data.forecast.training_universe import load_training_universe
from test_joint_dataset import histories


def test_training_pool_ignores_budget_keeps_required_and_truncates_future(tmp_path):
    store = SnapshotStore(tmp_path / 'market.db')
    data = histories(400, 4)
    for code, bars in data.items():
        store.save(DataEnvelope[list[DailyBar]](capability=DataCapability.DAILY_BARS,
            symbol=bars[0].symbol, retrieved_at=datetime(2026, 9, 7),
            quality_status=QualityStatus.FRESH_PRIMARY, data=bars))
    as_of = data['000001'][-11].trade_date
    result = load_training_universe(store, as_of=as_of, required_codes={'000004'}, max_symbols=2)
    assert '000004' in result and len(result) == 2
    assert all(b[-1].trade_date == as_of for b in result.values())
    result_again = load_training_universe(store, as_of=as_of, required_codes={'000004'}, max_symbols=2)
    assert result == result_again
    assert not load_training_universe(store, as_of=data['000001'][100].trade_date, max_symbols=2)


def test_index_and_invalid_equity_prefix_cannot_enter_training(tmp_path):
    store = SnapshotStore(tmp_path / 'market.db')
    bars = histories(400)['000001']
    symbol = StockSymbol(code='000300', market='SH')
    store.save(DataEnvelope[list[DailyBar]](capability=DataCapability.DAILY_BARS, symbol=symbol,
        retrieved_at=datetime(2026, 9, 7), quality_status=QualityStatus.FRESH_PRIMARY,
        data=[bar.model_copy(update={'symbol': symbol}) for bar in bars]))
    assert not load_training_universe(store, as_of=bars[-1].trade_date)
