"""Deterministic broad cached training pool, independent of today's budget and prices.

This is a cached-equity universe, NOT a reconstructed historical listing universe.
Unknown delisted names cannot be restored from a current cache.
"""
import hashlib
from finscope_market_data.models import DailyBar
from finscope_market_data.snapshot_store import SnapshotStore
from finscope_market_data.forecast.features import _validated_bars


def load_training_universe(store: SnapshotStore, *, as_of: str,
                           required_codes: set[str] | None = None,
                           max_symbols: int = 1200) -> dict[str, list[DailyBar]]:
    if not 2 <= max_symbols <= 3000:
        raise ValueError('训练股票池上限需介于 2 和 3000')
    required = required_codes or set()
    keys = [key for key in store.daily_bar_symbols()
            if (key.startswith('SH:6') or key.startswith(('SZ:0', 'SZ:3'))) and len(key) == 9]
    keys.sort(key=lambda key: (key.split(':')[1] not in required, hashlib.sha256(key.encode()).hexdigest()))
    result = {}
    for key in keys:
        bars = store.daily_history_as_of(key, as_of)
        if len(bars) < 365:
            continue
        try:
            _validated_bars(bars)
        except ValueError:
            continue
        result[key.split(':')[1]] = bars
        if len(result) >= max_symbols:
            break
    return result
