import json
from pathlib import Path

import pytest

from test_next_session import bars
from finscope_market_data.forecast.executable_labels import build_executable_labels

PROTOCOL = json.loads((Path(__file__).resolve().parents[2] / 'docs/quant/executable-protocol-v1.json').read_text())


def test_five_open_intervals_and_mature_labels_only():
    data = bars(85)
    dates = [bar.trade_date for bar in data]
    labels = build_executable_labels(data, dates, PROTOCOL, as_of=dates[70])
    assert len(labels) == 5
    assert labels[0].signal_date == dates[60]
    assert labels[0].entry_date == dates[61]
    assert labels[0].exit_date == dates[66]
    assert labels[0].price_return == pytest.approx(data[66].open / data[61].open - 1)
    assert all(label.exit_date <= dates[70] for label in labels)


def test_suspension_does_not_shift_exit_and_future_does_not_change_features():
    data = bars(85)
    dates = [bar.trade_date for bar in data]
    labels = build_executable_labels(data, dates, PROTOCOL, as_of=dates[-1])
    missing = build_executable_labels([bar for bar in data if bar.trade_date != dates[66]], dates,
                                      PROTOCOL, as_of=dates[-1])
    assert dates[60] not in {label.signal_date for label in missing}
    changed = [bar.model_copy(update={key: getattr(bar, key) * 2 for key in ('open', 'high', 'low', 'close')}) if bar.trade_date > dates[60] else bar for bar in data]
    altered = build_executable_labels(changed, dates, PROTOCOL, as_of=dates[-1])
    assert altered[0].features == labels[0].features


def test_rejects_duplicate_dates_and_incompatible_protocol():
    data = bars(85)
    dates = [bar.trade_date for bar in data]
    with pytest.raises(ValueError, match='calendar'):
        build_executable_labels(data, dates + dates[-1:], PROTOCOL, as_of=dates[-1])
    with pytest.raises(ValueError, match='protocol'):
        build_executable_labels(data, dates, {**PROTOCOL, 'holdingTradingDays': 1}, as_of=dates[-1])
