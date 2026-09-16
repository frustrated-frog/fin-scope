from datetime import date, datetime, timedelta
import random

import pytest
from pydantic import ValidationError

from finscope_market_data.forecast.trading_calendar import next_session
from finscope_market_data.overnight.models import MinuteBar, OvernightRequest
from finscope_market_data.overnight.engine import predict, settle, expected_times, group_bars, build_samples
from finscope_market_data.overnight.provider import OvernightMinuteProvider
from finscope_market_data.overnight.store import OvernightStore
from finscope_market_data.overnight.service import OvernightService


def history(count=90):
    rng = random.Random(42)
    day = date(2026, 1, 5)
    price, bars = 20., []
    for _ in range(count):
        price *= 1 + rng.uniform(-.01, .01)
        for stamp in expected_times('15:00'):
            opened = price
            price *= 1 + rng.uniform(-.002, .002)
            bars.append(MinuteBar(ended_at=datetime.fromisoformat(f'{day}T{stamp}:00'),
                open=opened, high=max(opened, price) * 1.001, low=min(opened, price) * .999,
                close=price, amount=rng.uniform(1e5, 1e6)))
        day = next_session(day)
    return bars


def request(day, **kwargs):
    return OvernightRequest(instrumentCode='605058.SH', signalDate=day, mode='TAIL_ENTRY', cutoff='14:30', **kwargs)


def test_entry_uses_delayed_proxy_and_future_bars_do_not_leak():
    bars = history()
    day = bars[-1].ended_at.date()
    req = request(day)
    now = datetime.combine(day, datetime.strptime('14:31', '%H:%M').time())
    result = predict(req, bars, now)
    assert result['status'] == 'WATCH'
    assert result['evidenceKind'] == 'FORWARD'
    for target in result['targets']:
        assert target['trainingThrough'] < result['dataThrough']
        assert target['validationCount'] == 20
    mutated = [bar.model_copy(update={'close':bar.close * 3}) if bar.ended_at > now else bar for bar in bars]
    assert predict(req, mutated, now) == result
    samples = build_samples(group_bars(bars), req, now)
    first = samples['OPEN'][0]
    grouped = group_bars(bars)
    signal = date.fromisoformat(first['signalDate'])
    expected = grouped[next_session(signal)]['09:35'].open / grouped[signal]['14:40'].open - 1 - .002
    assert first['actualNetReturn'] == pytest.approx(expected)


def test_missing_cutoff_is_not_replaced_by_close_and_backfill_is_labelled():
    bars = history(4)
    day = bars[-1].ended_at.date()
    req = request(day)
    now = datetime.combine(day, datetime.strptime('16:00', '%H:%M').time())
    result = predict(req, bars, now)
    assert result['evidenceKind'] == 'RETROSPECTIVE'
    assert result['status'] == 'INSUFFICIENT_DATA'
    bars = [b for b in bars if b.ended_at != datetime.fromisoformat(f'{day}T14:30:00')]
    assert predict(req, bars, now)['status'] == 'DATA_UNAVAILABLE'
    assert predict(req, bars, now.replace(hour=14, minute=0))['status'] == 'BEFORE_CUTOFF'


def test_holding_requires_position_and_is_separate_from_entry():
    with pytest.raises(ValidationError):
        OvernightRequest(instrumentCode='605058.SH', signalDate='2026-09-16', mode='AFTER_CLOSE_HOLDING', cutoff='15:00')
    bars = history()
    day = bars[-1].ended_at.date()
    req = OvernightRequest(instrumentCode='605058.SH', signalDate=day,
        mode='AFTER_CLOSE_HOLDING', cutoff='15:00', costBasis=20, quantity=100,
        positionOpenedOn='2026-01-05', costBps=10)
    result = predict(req, bars, datetime.fromisoformat(f'{day}T16:00:00'))
    assert result['mode'] == 'AFTER_CLOSE_HOLDING'
    assert result['evidenceKind'] == 'FORWARD'
    assert result['targets'][0]['costBasisReturn'] is not None


def test_store_preserves_first_forecast_and_settlement_never_uses_later_session(tmp_path):
    bars = history(4)
    day = bars[48].ended_at.date()
    req = request(day)
    report = predict(req, bars, datetime.fromisoformat(f'{day}T14:31:00'))
    store = OvernightStore(tmp_path / 'test.db')
    frozen = store.freeze(req, report, [])
    changed = store.freeze(req, {**report, 'status':'CHANGED'}, [])
    assert changed == frozen
    tomorrow = next_session(day)
    missing = [b for b in bars if b.ended_at.date() != tomorrow]
    assert settle(frozen, missing, bars[-1].ended_at)['status'] == 'PENDING'
    outcome = settle(frozen, bars, bars[-1].ended_at)
    assert outcome['status'] == 'SETTLED'
    assert len(outcome['targets']) == 4
    assert store.history()[0]['status'] != 'CHANGED'


def test_adapter_filters_future_and_rejects_conflicting_or_invalid_prices():
    now = datetime(2026, 9, 16, 14, 30)
    rows = [{'时间':'2026-09-16 14:30:00','开盘':10,'最高':11,'最低':9,'收盘':10.5,'成交额':5000}]
    assert len(OvernightMinuteProvider.parse(rows, now)) == 1
    assert OvernightMinuteProvider.parse(rows, now - timedelta(minutes=1)) == []
    with pytest.raises(ValueError):
        OvernightMinuteProvider.parse(rows + [{**rows[0], '收盘':10.8}], now)


def test_source_failure_and_pending_cutoff_do_not_create_fake_forecasts(tmp_path):
    class Broken:
        source = 'TEST'
        def fetch(self, *args):
            raise TimeoutError()
    service = OvernightService(OvernightStore(tmp_path / 'test.db'), Broken(),
                               lambda: datetime(2026, 9, 16, 14, 31))
    result = service.generate(request('2026-09-16'))
    assert result['status'] == 'DATA_UNAVAILABLE'
    assert any('TimeoutError' in text for text in result['warnings'])
    assert service.store.history() == []


def test_refresh_failure_is_visible_and_batch_is_bounded(tmp_path):
    class Broken:
        source = 'TEST'
        calls = []
        def fetch(self, code, now):
            self.calls.append(code)
            raise TimeoutError()
    store = OvernightStore(tmp_path / 'refresh.db')
    for code in ['600001.SH', '600002.SH', '600003.SH', '600004.SH']:
        req = request('2026-09-15').model_copy(update={'instrument_code': code})
        store.freeze(req, predict(req, [], datetime(2026, 9, 15, 14, 31)), [])
    provider = Broken()
    ticks = iter([datetime(2026, 9, 16, 16), datetime(2026, 9, 16, 16, 1)])
    service = OvernightService(store, provider, lambda: next(ticks))
    results = service.refresh_outcomes()
    assert len(provider.calls) == 3
    assert sum(bool((r.get('outcome') or {}).get('warnings')) for r in results) == 3
    service.refresh_outcomes()
    assert len(set(provider.calls)) == 4
