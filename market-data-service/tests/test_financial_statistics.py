from datetime import date, timedelta
from types import SimpleNamespace

import numpy as np
import pytest

from finscope_market_data import financial_statistics as stats


def bar(day, close, complete=True, adjustment="QFQ"):
    return SimpleNamespace(trade_date=day,close=close,is_complete=complete,adjustment=adjustment)


def sample(values):
    data = {}
    day = date(2026,5,1)
    for v in values:
        following = day + timedelta(days=1)
        data[following] = (day,v)
        day = following
    return data


def test_incomplete_adjustment_gap_and_cutoff_do_not_manufacture_daily_returns():
    bars = [bar("2026-11-02",10),bar("2026-11-03",11),bar("2026-11-05",30),
            bar("2026-11-06",30,False),bar("2026-11-09",30),bar("2026-11-10",30,adjustment="NONE"),
            bar("2026-11-11",60,adjustment="NONE")]
    seq, basis = stats.daily_sample(bars,date(2026,11,3),date(2026,11,10))
    assert len(seq)==1 and basis==["QFQ"]
    assert seq[date(2026,11,3)][1]==pytest.approx(.1)
    assert not stats.daily_sample(bars+[bar("2026-11-03",11)],date(2026,11,3),date(2026,11,10))[0]


def test_distribution_histogram_and_inference_on_short_and_flat_samples():
    short = stats.distribution(sample([.1,-.1,0]))
    assert short["compound"]==pytest.approx(-.01)
    assert short["lagOne"] is None and short["meanInterval"] is None
    assert short["bins"]==[1,0,0,1,0,1]
    flat = stats.distribution(sample([.01]*40))
    assert flat["skew"] is None and flat["absoluteLagOne"] is None
    assert flat["meanInterval"]["low"]==pytest.approx(.01)


def test_regression_uses_common_excess_returns_and_frontier_is_efficient():
    base = np.array([.01,-.03,.02,.01,.04,-.03])
    rf=.03
    daily=(1+rf)**(1/252)-1
    model=stats.regression(sample(.001+2*(base-daily)+daily),sample(base),rf)
    assert model["annualAlpha"]==pytest.approx(.252) and model["beta"]==pytest.approx(2)
    assert model["rSquared"]==pytest.approx(1)
    pair=stats.pair_analysis(sample(base),sample(.002-base),5)
    frontier=pair["frontier"]
    assert frontier["minimum"]["weight"]==pytest.approx(.5)
    assert frontier["minimum"]["annualVol"]<1e-7
    assert frontier["points"][-1]["annualReturn"]>=frontier["equal"]["annualReturn"]
    assert pair["rolling"][3]["value"] is None and pair["rolling"][4]["value"]==pytest.approx(-1)
    assert stats.regression(sample(base),sample(np.zeros(6)),0)["beta"] is None


def test_rolls_reset_after_missing_days():
    a=sample(range(14))
    del a[sorted(a)[6]]
    roll=stats.pair_analysis(a,a,5)["rolling"]
    assert roll[5]["value"]==pytest.approx(1) and roll[6]["value"] is None and roll[10]["value"]==pytest.approx(1)


def test_invalid_design_and_no_data_are_explicit():
    default=dict(asset="SH:000001",peer="SZ:399006",asOf="2026-11-27",days=180,window=20)
    with pytest.raises(ValueError,match="不同资产"):
        stats.StatisticsQuery(**(default|{"peer":default["asset"]}))
    with pytest.raises(ValueError,match="没有可核验"):
        stats.analyze(None,stats.StatisticsQuery(**default))
