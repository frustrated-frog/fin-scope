from datetime import datetime, timedelta
from types import SimpleNamespace
from zoneinfo import ZoneInfo

import pytest

from finscope_market_data.discovery.event_provider import MarketEventProvider
from finscope_market_data.discovery.strength import strength_features, select_research_targets, assess_strength
from finscope_market_data.discovery.schemas import DiscoveryCandidate


def bars(count=100):
    start = datetime(2025, 1, 1)
    values = []
    price = 20.
    for i in range(count):
        opened = price
        price *= 1.10 if i % 3 == 0 else .95
        values.append(SimpleNamespace(trade_date=(start + timedelta(days=i)).date().isoformat(),
            close=price, open=opened, high=max(opened, price) * 1.01, amount=1e8))
    return values


def candidate(code, sector, strong=False):
    return DiscoveryCandidate(code=code, market='SH', name=code, price=20, lot_cost=2005,
        admitted=True, budget_eligible=True, sector_codes=[sector],
        factors={'event_active': float(strong), 'strength_score': 5 if strong else 0})


def test_event_gets_reserved_seat_beyond_old_top15_and_banks_cannot_fill_all_seats():
    values = [candidate(f'600{i:03}', 'bank') for i in range(20)]
    values += [candidate('605058', 'pcb', True)]
    selected = select_research_targets(values, 15, 3, .4)
    assert selected[0].code == '605058'
    assert selected[0].research_lane == 'SHORT_TERM_STRENGTH'
    assert sum(x.sector_codes == ['bank'] for x in selected) == 3
    assert len({x.code for x in selected}) == len(selected)


def test_twenty_percent_board_does_not_mistake_ten_percent_for_limit_move():
    values = bars(100)
    assert strength_features(values, '605058')['limit_like_streak'] == 1
    assert strength_features(values, '300001')['limit_like_streak'] == 0


def test_event_estimates_include_losers_and_use_only_mature_calendar_labels():
    values = bars(160)
    dates = [x.trade_date for x in values]
    result = assess_strength(values, '605058', dates)
    assert result['sample_count'] > 30
    assert result['loss_rate'] > .5
    assert result['validation_count'] > 0
    assert result['qualified'] is False
    assert result['execution_status'] == 'UNVERIFIED'
    assert result['training_through'] <= values[-1].trade_date
    assert assess_strength(values, '605058', [])['sample_count'] == 0
    before = assess_strength(values[:-10], '605058', dates)
    values[-1].close *= 2
    assert assess_strength(values[:-10], '605058', dates) == before


def test_scan_combines_limits_failures_and_spot_and_freezes(tmp_path):
    def loader(kind, day):
        return [{'代码': {'LIMIT_UP':'605058','BROKEN_LIMIT':'600001','SPOT':'000001'}[kind],
                 '名称': kind, '涨跌幅': 6, '所属行业': '元件'}]
    provider = MarketEventProvider(tmp_path, loader, lambda: datetime(2026, 9, 14, 16, tzinfo=ZoneInfo('Asia/Shanghai')))
    result = provider.scan('2026-09-14')
    assert result['status'] == 'COMPLETE'
    assert len(result['members']) == 3
    provider.loader = lambda *args: pytest.fail('frozen snapshot must not be refetched')
    assert provider.scan('2026-09-14') == result


def test_historical_scan_never_uses_today_spot_and_failed_source_is_explicit(tmp_path):
    calls = []
    def loader(kind, day):
        calls.append(kind)
        if kind == 'BROKEN_LIMIT':
            raise TimeoutError()
        return [{'代码':'605058', '名称':'澳弘电子', '涨跌幅':10}]
    provider = MarketEventProvider(tmp_path, loader, lambda: datetime(2026, 9, 16, 16))
    result = provider.scan('2026-09-14')
    assert calls == ['LIMIT_UP', 'BROKEN_LIMIT']
    assert result['status'] == 'PARTIAL'
    assert not list(tmp_path.glob('*.json'))
    assert provider.scan('2026-09-17')['status'] == 'BEFORE_CLOSE'


def test_before_close_never_freezes_live_event_prices(tmp_path):
    provider = MarketEventProvider(tmp_path, lambda *args: pytest.fail('not a close'),
                                   lambda: datetime(2026, 9, 16, 10))
    assert provider.scan('2026-09-16')['status'] == 'BEFORE_CLOSE'
