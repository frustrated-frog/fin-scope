from concurrent.futures import ThreadPoolExecutor
from unittest.mock import patch
import sqlite3

from test_daily_research import store, save, TODAY, NOW
from finscope_market_data.daily_research import DailyResearchService
from finscope_market_data.snapshot_store import SnapshotStore


def test_persistent_cache_coalesces_and_returns_copies(store):
    save(store)
    service = DailyResearchService(store, now=lambda: NOW)
    with patch.object(store, 'load_daily_bar_panel', wraps=store.load_daily_bar_panel) as scan:
        with ThreadPoolExecutor(max_workers=4) as pool:
            results = list(pool.map(service.fetch, [TODAY] * 4))
        assert scan.call_count == 1
        results[0].stocks.clear()
        assert len(service.fetch(TODAY).stocks) == 1
    restarted = DailyResearchService(SnapshotStore(store.path), now=lambda: NOW)
    with patch.object(restarted.snapshots, 'load_daily_bar_panel', side_effect=AssertionError('scan')):
        assert restarted.fetch(TODAY).cache_hit


def test_revision_updates_same_second_and_external_delete(store):
    save(store)
    service = DailyResearchService(store, now=lambda: NOW)
    first = service.fetch(TODAY)
    save(store, jump=1)
    assert service.fetch(TODAY).stocks[0].return_1d != first.stocks[0].return_1d
    with sqlite3.connect(store.path) as connection:
        connection.execute("DELETE FROM market_data_snapshot WHERE capability='DAILY_BARS'")
    assert service.fetch(TODAY).sample_count == 0


def test_revision_race_never_publishes_wrong_version(store):
    save(store)
    service = DailyResearchService(store, now=lambda: NOW)
    original = store.load_daily_bar_panel
    def racing(*args, **kwargs):
        panel = original(*args, **kwargs)
        save(store, jump=1)
        return panel
    with patch.object(store, 'load_daily_bar_panel', side_effect=racing):
        stale = service.fetch(TODAY)
    fresh = service.fetch(TODAY)
    assert fresh.stocks[0].return_1d != stale.stocks[0].return_1d


def test_quote_does_not_invalidate_but_external_update_does(store):
    save(store)
    service = DailyResearchService(store, now=lambda: NOW)
    service.fetch(TODAY)
    with sqlite3.connect(store.path) as connection:
        connection.execute("INSERT INTO market_data_snapshot VALUES ('QUOTE','SH:600001','{}',CURRENT_TIMESTAMP)")
    assert service.fetch(TODAY).cache_hit
    with sqlite3.connect(store.path) as connection:
        connection.execute("UPDATE market_data_snapshot SET updated_at=updated_at WHERE capability='DAILY_BARS'")
    assert not service.fetch(TODAY).cache_hit


def test_cache_retains_twenty_dates_and_algorithm_version_isolated(store):
    from datetime import timedelta
    from finscope_market_data.daily_research import is_closed_research_date
    service = DailyResearchService(store, now=lambda: NOW)
    days = [TODAY - timedelta(days=i) for i in range(40)]
    days = sorted(day for day in days if is_closed_research_date(day, NOW))
    for day in days:
        service.fetch(day)
    with sqlite3.connect(store.path) as connection:
        assert connection.execute('SELECT count(*) FROM daily_research_cache').fetchone()[0] == 20
    assert service.fetch(days[-1]).cache_hit
    service.ALGORITHM_VERSION = 'new-algorithm'
    assert not service.fetch(days[-1]).cache_hit


def test_exception_not_cached_and_cutoff_revalidated(store):
    from datetime import datetime
    from zoneinfo import ZoneInfo
    save(store)
    now = datetime(2026, 8, 21, 15, 29, tzinfo=ZoneInfo('Asia/Shanghai'))
    service = DailyResearchService(store, now=lambda: now)
    assert service.fetch(TODAY).sample_count == 0
    now = now.replace(minute=30)
    with patch.object(store, 'load_daily_bar_panel', side_effect=RuntimeError('scan')):
        import pytest
        with pytest.raises(RuntimeError):
            service.fetch(TODAY)
    assert service.fetch(TODAY).sample_count == 1
    assert service.fetch(TODAY).cache_hit
    now = now.replace(minute=29)
    assert service.fetch(TODAY).sample_count == 0
