from datetime import datetime
import json

import pytest

from finscope_market_data.overnight.engine import expected_times
from finscope_market_data.overnight.provider import OvernightMinuteProvider
from finscope_market_data.providers.base import ProviderError


def rows():
    return [{'day': f'2026-11-04 {stamp}:00', 'open': '10', 'close': '10.1',
             'high': '10.2', 'low': '9.9', 'amount': '123456.78'} for stamp in expected_times('15:00')]


class Http:
    def __init__(self, fallback):
        self.fallback = fallback
        self.closed = False
        self.calls = []

    async def get_json(self, *args, **kwargs):
        self.calls.append('primary')
        raise ProviderError('CONNECTION_ERROR', 'offline')

    async def get_text(self, *args, **kwargs):
        self.calls.append('fallback')
        assert kwargs['params']['symbol'] == 'sz000001'
        return json.dumps(self.fallback)

    async def aclose(self):
        self.closed = True


def test_fallback_keeps_actual_amount_and_never_exposes_future_bars():
    http = Http(rows())
    batch = OvernightMinuteProvider(lambda: http).fetch('000001.SZ', datetime(2026, 11, 4, 14, 31))
    assert batch.source == 'SINA_5M_RAW'
    assert batch.failures == [{'source': 'EASTMONEY_5M_RAW', 'code': 'CONNECTION_ERROR'}]
    assert len(batch) == len(expected_times('14:30'))
    assert batch[-1].ended_at == datetime(2026, 11, 4, 14, 30)
    assert batch[-1].amount == 123456.78
    assert http.closed


def test_partial_oldest_day_cannot_shadow_complete_historical_data():
    oldest = [{**row, 'day': row['day'].replace('11-04', '11-02')} for row in rows()[-5:]]
    previous = [{**row, 'day': row['day'].replace('11-04', '11-03')} for row in rows()]
    batch = OvernightMinuteProvider(lambda: Http(oldest + previous + rows())).fetch(
        '000001.SZ', datetime(2026, 11, 4, 14, 31))
    assert not any(bar.ended_at.day == 2 for bar in batch)
    assert sum(bar.ended_at.day == 3 for bar in batch) == 48
    assert batch[-1].ended_at == datetime(2026, 11, 4, 14, 30)


def test_before_first_bar_uses_previous_completed_session_for_outcome_refresh():
    batch = OvernightMinuteProvider(lambda: Http(rows())).fetch('000001.SZ', datetime(2026, 11, 5, 0, 10))
    assert len(batch) == 48
    assert batch[-1].ended_at == datetime(2026, 11, 4, 15)


@pytest.mark.parametrize('change', ['missing_amount', 'stale', 'hole', 'conflict'])
def test_invalid_fallback_does_not_masquerade_as_success(change):
    data = rows()
    if change == 'missing_amount':
        del data[0]['amount']
    elif change == 'stale':
        data = [{**row, 'day': row['day'].replace('11-04', '11-03')} for row in data]
    elif change == 'hole':
        data.pop(2)
    else:
        data.append({**data[0], 'close': '10.05'})
    http = Http(data)
    with pytest.raises(ProviderError, match='SINA_5M_RAW'):
        OvernightMinuteProvider(lambda: http).fetch('000001.SZ', datetime(2026, 11, 4, 14, 31))
    assert http.closed
