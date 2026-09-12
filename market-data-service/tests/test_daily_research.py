from datetime import date, datetime, timedelta
from zoneinfo import ZoneInfo

import pytest
from fastapi.testclient import TestClient

from finscope_market_data.daily_research import DailyResearchService
from finscope_market_data.models import DailyBar, DataCapability, DataEnvelope, StockSymbol
from finscope_market_data.snapshot_store import SnapshotStore

TODAY = date(2026, 8, 21)
NOW = datetime(2026, 9, 12, tzinfo=ZoneInfo('Asia/Shanghai'))


def dates():
    days = [TODAY - timedelta(days=i) for i in range(45)]
    return sorted(day for day in days if day.weekday() < 5)[-22:]


def save(store, code='600001', market='SH', missing=False, jump=150, adjustment='QFQ', gap=False):
    symbol = StockSymbol(market=market, code=code)
    bars = [DailyBar(symbol=symbol, trade_date=day.isoformat(), open=100+i,
                     high=100+i, low=100+i, close=100+i, volume=100,
                     amount=1000, adjustment=adjustment)
            for i, day in enumerate(dates())]
    bars[-2].close = 130
    bars[-2].change_pct = 4
    bars[-1].close = jump
    if missing:
        bars.pop()
    if gap:
        bars.pop(10)
    store.save(DataEnvelope(capability=DataCapability.DAILY_BARS, symbol=symbol,
                           quality_status='FRESH_PRIMARY', source_code='FIXTURE',
                           source_family='FIXTURE', retrieved_at=NOW, data=bars))


@pytest.fixture
def store(tmp_path):
    return SnapshotStore(tmp_path / 'snapshot.db')


def fetch(store, day=TODAY, now=NOW):
    return DailyResearchService(store, now=lambda: now).fetch(day)


def test_prior_selection_unaffected_by_current_jump_and_missing_today(store):
    save(store)
    first = fetch(store)
    save(store, jump=1)
    second = fetch(store)
    assert [g.members for g in first.groups] == [g.members for g in second.groups]
    assert all(g.members == ['600001.SH'] for g in first.groups)
    save(store, missing=True)
    missing = fetch(store)
    assert all(g.member_count == 1 and g.valid_count == 0 for g in missing.groups)
    assert missing.stocks[0].return_1d is None
    assert missing.sample_count == 1
    assert all(group.eligible_count <= missing.sample_count for group in missing.groups)
    assert len(missing.stocks) <= missing.sample_count


def test_small_samples_suppress_statistics_but_five_members_compute(store):
    save(store)
    assert all(g.advance_ratio is None and g.median_return is None for g in fetch(store).groups)
    for i in range(2, 6):
        save(store, code=f'60000{i}')
    assert all(g.valid_count == 5 and g.advance_ratio == 1 for g in fetch(store).groups)


def test_gap_and_mixed_adjustment_fail_closed_for_windows(store):
    save(store, gap=True)
    result = fetch(store)
    assert result.stocks[0].return_20d is None
    assert result.groups[1].eligible_count == result.groups[2].eligible_count == 0
    save(store, adjustment='NONE')
    result = fetch(store)
    assert result.stocks[0].return_5d is None
    assert result.groups[0].member_count == 1
    assert result.groups[1].member_count == result.groups[2].member_count == 0


@pytest.mark.parametrize('day', [date(1, 1, 1), date(2025, 12, 31), date(2026, 10, 1), date(2026, 8, 22), date(2027, 8, 21), date(2026, 9, 14)])
def test_holiday_unknown_and_future_dates_unavailable(store, day):
    save(store)
    result = fetch(store, day)
    assert result.quality_status == 'UNAVAILABLE'
    assert result.sample_count == 0


def test_live_day_is_not_closed_until_1530(store):
    save(store)
    result = fetch(store, now=datetime(2026, 8, 21, 15, 29, tzinfo=ZoneInfo('Asia/Shanghai')))
    assert result.quality_status == 'UNAVAILABLE'
    assert fetch(store, now=datetime(2026, 8, 21, 15, 30, tzinfo=ZoneInfo('Asia/Shanghai'))).sample_count == 1


def test_filters_indices_etfs_and_market_mismatch(store):
    for code, market in [('000001', 'SH'), ('510300', 'SH'), ('159915', 'SZ'),
                         ('399001', 'SZ'), ('600001', 'SZ'), ('000001', 'SZ'),
                         ('920001', 'BJ'), ('688001', 'SH')]:
        save(store, code=code, market=market)
    assert {s.instrument_code for s in fetch(store).stocks} == {'000001.SZ', '920001.BJ', '688001.SH'}


def test_empty_and_api_serialization(store):
    assert fetch(store).quality_status == 'UNAVAILABLE'
    save(store)
    from finscope_market_data.app import create_app
    from types import SimpleNamespace
    app = create_app(router=SimpleNamespace(snapshots=store))
    response = TestClient(app).get("/v1/markets/CN-A/daily-research?business_date=2026-08-21")
    assert response.status_code == 200
    data = response.json()
    assert set(data) == {"schema_version", "cache_hit", "calculated_at", "business_date", "selection_date", "source_code", "quality_status", "sample_count", "stocks", "groups", "warnings"}
    assert data["selection_date"] == "2026-08-20"
    assert data["schema_version"] == "daily-research-v1"


def test_mixed_adjustment_and_nonfinite_values_do_not_leak(store):
    save(store)
    symbol = StockSymbol(market="SH", code="600001")
    envelope = store.load(DataCapability.DAILY_BARS, symbol)
    envelope.data[-3].adjustment = "NONE"
    envelope.data[-1].amount = -1
    store.save(envelope)
    result = fetch(store)
    assert result.stocks[0].return_5d is None
    assert result.stocks[0].return_20d is None
    assert result.stocks[0].amount is None
    assert result.groups[0].member_count == 1
    assert result.groups[1].eligible_count == result.groups[2].eligible_count == 0
    assert result.stocks[0].return_1d is not None


def test_bounded_snapshot_query_limits_symbols_and_dates(store):
    for i in range(1, 4):
        save(store, code=f"60000{i}")
    panel = store.load_daily_bar_panel("2026-08-20", max_bars_per_symbol=3, max_symbols=2)
    assert len(panel) == 2
    assert all(len(bars) == 3 for bars in panel.values())
    assert all(bar.trade_date <= "2026-08-20" for bars in panel.values() for bar in bars)


def test_insufficient_prior_history_is_not_assigned_to_any_group(store):
    save(store)
    symbol = StockSymbol(market="SH", code="600001")
    envelope = store.load(DataCapability.DAILY_BARS, symbol)
    envelope.data = envelope.data[-1:]
    envelope.data[0].change_pct = 8
    store.save(envelope)
    result = fetch(store)
    assert result.sample_count == 1
    assert result.stocks[0].group_codes == []
    assert all(group.eligible_count == 0 for group in result.groups)


def test_sample_count_includes_evaluable_prior_nonmembers_with_missing_today(store):
    save(store, missing=True)
    symbol = StockSymbol(market="SH", code="600001")
    envelope = store.load(DataCapability.DAILY_BARS, symbol)
    envelope.data[-1].close = 50
    envelope.data[-1].change_pct = -3
    store.save(envelope)
    result = fetch(store)
    assert result.sample_count == 1
    assert result.stocks == []
    assert result.quality_status == "UNAVAILABLE"
    assert all(group.eligible_count == 1 for group in result.groups)


@pytest.mark.parametrize('invalid_close', [float('nan'), float('inf')])
def test_invalid_current_bar_preserves_history_and_other_stocks(store, invalid_close):
    save(store)
    symbol = StockSymbol(market="SH", code="600001")
    envelope = store.load(DataCapability.DAILY_BARS, symbol)
    envelope.data[-1].close = invalid_close
    store.save(envelope)
    save(store, code="600002")
    result = fetch(store)
    assert result.sample_count == 2
    stocks = {stock.instrument_code: stock for stock in result.stocks}
    assert stocks["600001.SH"].return_1d is None
    assert stocks["600001.SH"].group_codes == ["STRONG", "TREND", "BREAKOUT"]
    assert stocks["600002.SH"].return_1d is not None
    assert all(group.member_count == 2 and group.valid_count == 1 for group in result.groups)
    assert any("无效" in warning for warning in result.warnings)
