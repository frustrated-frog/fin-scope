from dataclasses import replace

import numpy as np
import pytest

from test_next_session import bars
from finscope_market_data.forecast.context import build_aligned_context
from finscope_market_data.forecast.next_session import build_close_samples
from finscope_market_data.forecast.next_session_ensemble import NextSessionEnsemble
from finscope_market_data.forecast.short_term import (
    SHORT_TERM_FEATURE_CODES, ShortTermModel, short_term_features,
)


def samples():
    data = bars(500)
    indices = {row.trade_date: index for index, row in enumerate(data)}
    return [replace(row, features=short_term_features(data, indices[row.signal_date]))
            for row in build_close_samples(data)]


def test_features_use_signal_history_only_and_ignore_future_market_bars():
    data = bars(100)
    context = build_aligned_context(data, market_bars=data)
    expected = short_term_features(data, 70, context)
    changed = [row.model_copy(update={'close': row.close * 9, 'amount': row.amount * 4})
               if index > 70 else row for index, row in enumerate(data)]
    assert short_term_features(changed, 70, build_aligned_context(changed, market_bars=changed)) == expected
    assert len(expected) == len(SHORT_TERM_FEATURE_CODES) == 15
    assert np.all(np.isfinite(expected))
    assert expected[11] != 0  # observed one-day market move
    assert expected[12] == 0  # identical stock / market daily return


def test_features_are_invariant_to_price_and_amount_units():
    data = bars(100)
    scaled = [row.model_copy(update={**{key: getattr(row, key) * 10 for key in ('open', 'high', 'low', 'close')},
                                    'amount': row.amount * 100}) for row in data]
    assert short_term_features(data, 99) == pytest.approx(short_term_features(scaled, 99))


def test_flat_candles_and_missing_market_context_remain_finite():
    data = [row.model_copy(update={'open': 10, 'high': 10, 'low': 10, 'close': 10}) for row in bars(100)]
    features = short_term_features(data, 99)
    assert np.all(np.isfinite(features))
    assert features[11:13] == (0, 0)
    assert features[-2:] == (0, 0)


def test_recent_model_uses_newest_mature_label_but_no_future_labels_or_scaling():
    data = samples()
    cutoff = data[-30].signal_date
    original = ShortTermModel(data, cutoff)
    changed = ShortTermModel([replace(row, features=(1000,) * 15, net_return=2)
                              if row.exit_date >= cutoff else row for row in data], cutoff)
    assert original.predict(data[-30].features) == pytest.approx(changed.predict(data[-30].features))
    assert original.training_through == max(row.exit_date for row in data if row.exit_date < cutoff)
    assert ShortTermModel(data, '2026-09-05').training_through == '2026-09-04'


@pytest.mark.parametrize('outcome,expected', [(1, .99), (-1, .01)])
def test_single_class_training_has_bounded_prior(outcome, expected):
    data = [replace(row, net_return=outcome) for row in samples()]
    fit = ShortTermModel(data, '2026-09-05')
    assert fit.predict(data[-1].features) == expected


def test_invalid_training_and_feature_contracts_fail_explicitly():
    data = samples()
    with pytest.raises(ValueError, match='120'):
        ShortTermModel(data[:20], '2026-09-05')
    with pytest.raises(ValueError, match='维度'):
        ShortTermModel([replace(row, features=(1, 2)) for row in data], '2026-09-05')
    fit = ShortTermModel(data, '2026-09-05')
    with pytest.raises(ValueError, match='数值'):
        fit.predict((float('nan'),) * 15)
    with pytest.raises(ValueError, match='21'):
        short_term_features(bars(100), 10)


def test_ensemble_preserves_incumbent_returns_and_does_not_fit_on_future_labels():
    old = build_close_samples(bars(500))
    short = samples()
    cutoff = old[-30].signal_date
    fit = NextSessionEnsemble.fit(old, short, cutoff)
    changed = NextSessionEnsemble.fit(
        [replace(row, net_return=2) if row.exit_date >= cutoff else row for row in old],
        [replace(row, net_return=2) if row.exit_date >= cutoff else row for row in short], cutoff)
    original = fit.predict(old[-30].features, short[-30].features)
    assert original == pytest.approx(changed.predict(old[-30].features, short[-30].features))
    incumbent = fit.incumbent.predict(old[-30].features)
    assert original[1:] == incumbent[1:]
    assert original[0] == pytest.approx((incumbent[0] + fit.recent.predict(short[-30].features)) / 2)
    with pytest.raises(ValueError, match='同日期'):
        NextSessionEnsemble.fit(old, short[:-1], cutoff)
    with pytest.raises(ValueError, match='同日期'):
        NextSessionEnsemble.fit(old, [replace(short[0], net_return=2), *short[1:]], cutoff)
