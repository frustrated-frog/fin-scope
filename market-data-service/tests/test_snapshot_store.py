from datetime import datetime

from finscope_market_data.models import (
    DailyBar,
    DataCapability,
    DataEnvelope,
    QualityStatus,
    StockSymbol,
)
from finscope_market_data.snapshot_store import SnapshotStore


def _bar(symbol: StockSymbol, trade_date: str, close: float, amount: float) -> DailyBar:
    return DailyBar(
        symbol=symbol,
        trade_date=trade_date,
        open=close,
        high=close,
        low=close,
        close=close,
        volume=100,
        amount=amount,
        adjustment="QFQ",
    )


def test_load_daily_bar_pairs_returns_target_and_previous_trading_bar(tmp_path) -> None:
    store = SnapshotStore(tmp_path / "snapshots.db")
    first = StockSymbol(market="SH", code="600001")
    second = StockSymbol(market="SZ", code="000001")
    for symbol, closes in ((first, (10.0, 11.0)), (second, (20.0, 18.0))):
        store.save(
            DataEnvelope[list[DailyBar]](
                capability=DataCapability.DAILY_BARS,
                symbol=symbol,
                quality_status=QualityStatus.FRESH_FALLBACK,
                source_code="FIXTURE",
                source_family="FIXTURE",
                retrieved_at=datetime(2026, 8, 22, 12, 0),
                data=[
                    _bar(symbol, "2026-08-20", closes[0], 100.0),
                    _bar(symbol, "2026-08-21", closes[1], 200.0),
                ],
            )
        )

    pairs = store.load_daily_bar_pairs("2026-08-21")

    assert len(pairs) == 2
    assert pairs[0][0].trade_date == "2026-08-20"
    assert pairs[0][1].trade_date == "2026-08-21"
    assert sum(pair[1].amount or 0 for pair in pairs) == 400.0
