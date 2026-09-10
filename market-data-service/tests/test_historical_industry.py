from dataclasses import replace

import numpy as np
import pytest

from finscope_market_data.forecast.historical_industry import IndustryChange, industry_panel_features
from finscope_market_data.forecast.joint_dataset import ObservableRow


CODES = ('MOMENTUM_5', 'MOMENTUM_20', 'OVERNIGHT_GAP', 'INTRADAY_RETURN', 'AMOUNT_RATIO_20_60')


def change(code, industry='银行', effective='2020-01-01'):
    return IndustryChange(code, industry, effective, '008003', 'CNINFO', '2026-09-10T08:00:00+08:00')


def test_peer_features_exclude_self_and_do_not_backfill_future_membership():
    rows = (ObservableRow('a', '2025-01-01', (.1,.2,0.,.01,1.)),
            ObservableRow('b', '2025-01-01', (.3,.4,0.,.03,2.)),
            ObservableRow('c', '2025-01-01', (.5,.6,0.,.05,3.)))
    events = [change(code) for code in ('a','b','c')]
    features, codes = industry_panel_features(rows, CODES, events)
    assert features[0, codes.index('SI_MOMENTUM_5')] == .4
    assert features[0, codes.index('SI_RELATIVE_5')] == pytest.approx(-.3)
    assert features[0, codes.index('SI_MISSING')] == 0
    changed = industry_panel_features(rows, CODES, [*events, change('b','煤炭','2025-02-01')])[0]
    np.testing.assert_equal(features, changed)
    # Missing peer labels do not enter this API at all.
    other = list(rows); other[0] = replace(rows[0], features=(9.,9.,0.,.01,1.))
    shifted = industry_panel_features(other, CODES, events)[0]
    assert shifted[0, codes.index('SI_MOMENTUM_5')] == features[0, codes.index('SI_MOMENTUM_5')]


def test_vendor_reconstruction_is_not_strict_historical_availability():
    rows = [ObservableRow('a','2025-01-01',(0.,)*5)]
    values, codes = industry_panel_features(rows, CODES, [change('a')], availability='OBSERVED')
    assert values[0,codes.index('SI_MISSING')] == 1
    assert np.isnan(values[0,codes.index('SI_MOMENTUM_5')])
