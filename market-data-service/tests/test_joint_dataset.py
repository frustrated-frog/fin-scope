from datetime import date, timedelta
import math
import pytest
from finscope_market_data.models import DailyBar, StockSymbol
from finscope_market_data.forecast.joint_dataset import build_joint_dataset, history_fingerprint


def histories(count=120, stocks=4):
    dates = [(date(2025, 1, 1) + timedelta(days=i)).isoformat() for i in range(count)]
    return {f'{code:06d}': [DailyBar(symbol=StockSymbol(code=f'{code:06d}', market='SZ'),
        trade_date=day, open=10 + i * .01, close=10 + i * .01 + math.sin(i + code) * .03,
        high=11 + i * .01, low=9 + i * .01, volume=1_000_000 + i * 17,
        amount=10_000_000 + i * 170, adjustment='QFQ') for i, day in enumerate(dates)]
        for code in range(1, stocks + 1)}


def test_joint_rows_use_exact_next_market_close_and_finite_enriched_features():
    data = histories()
    result = build_joint_dataset(data, as_of=data['000001'][-1].trade_date, minimum_cross_section=2)
    row = next(r for r in result.rows if r.code == '000001')
    idx = next(i for i, b in enumerate(data[row.code]) if b.trade_date == row.sample.signal_date)
    assert row.sample.exit_date == data[row.code][idx + 1].trade_date
    assert row.sample.net_return == pytest.approx(data[row.code][idx + 1].close / data[row.code][idx].close - 1)
    assert len(row.sample.features) == len(result.feature_codes) > 28
    assert all(math.isfinite(v) for r in result.rows for v in r.sample.features)


def test_future_bars_cannot_change_dataset_or_current_features():
    data = histories()
    as_of = data['000001'][-5].trade_date
    full = build_joint_dataset(data, as_of=as_of, minimum_cross_section=2)
    past = build_joint_dataset({c: b[:-4] for c, b in data.items()}, as_of=as_of, minimum_cross_section=2)
    assert full == past


def test_suspended_next_day_is_excluded_instead_of_using_later_close():
    data = histories()
    missing = data['000001'][90].trade_date
    signal = data['000001'][89].trade_date
    data['000001'] = [b for b in data['000001'] if b.trade_date != missing]
    result = build_joint_dataset(data, as_of=data['000002'][-1].trade_date, minimum_cross_section=2)
    assert not any(r.code == '000001' and r.sample.signal_date == signal for r in result.rows)


def test_fingerprint_uses_same_effective_history_across_request_limits():
    data = histories(1200)['000001']
    assert history_fingerprint(data) == history_fingerprint(data[-1061:])


def test_short_or_unadjusted_history_cannot_enter_joint_training():
    data = histories(40)
    with pytest.raises(ValueError, match='截面'):
        build_joint_dataset(data, as_of=data['000001'][-1].trade_date, minimum_cross_section=2)
