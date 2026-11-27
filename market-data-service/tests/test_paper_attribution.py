import pytest
from finscope_market_data.paper.attribution import bridge, compatible, stock_ledger, day_changes
from finscope_market_data.paper.performance import report


def test_bridge_embedded_slippage_is_counted_once_and_missing_stays_unknown():
    a = dict(net=-1650.71, fees=165.87, slippage=25.84, capital=10000)
    b = dict(net=-1941.24, fees=736.30, slippage=112.94)
    result = bridge(a,b)
    assert result["points"] == pytest.approx(2.9053)
    assert result["holdings"] == -367
    assert sum(result[k] for k in ("fees","slippage","holdings")) == pytest.approx(result["net"])
    partial = bridge(a,dict(b,slippage=None))
    assert partial["slippage"] is None and partial["holdings"] == -279.9


def test_requires_identical_inputs_and_keeps_partial_date_identifiable():
    history=dict(classicSnapshots=["a"],universeTimeline=[{"at":"date"}],engine="v2",
                 curve=[dict(at="2026-11-27T10:00",equity=80)])
    facts=dict(capital=100,first="a",last="b",benchmark=1)
    assert compatible(history,history,facts,facts)
    assert not compatible(history,dict(history,engine="v1"),facts,facts)
    assert not compatible(history,dict(history,classicSnapshots=[]),facts,facts)
    assert day_changes(history,100)["2026-11-27"]==dict(net=-20,at="2026-11-27T10:00")


def test_per_stock_uses_frozen_marks_and_actual_entitlements():
    first,last="2026-11-26T15:00","2026-11-27T15:00"
    event=dict(ex_date=last[:10],dividend_per_share=.1,per_share_bonus=.1)
    bars={"A":[[first,0,0,0,10,0,0,False,True,[]],[last,0,0,0,11,0,0,False,True,[event]]]}
    history=dict(curve=[dict(at=first,equity=995),dict(at=last,equity=1215)], fills=1,
                 trades=[dict(at=first,code="A",action="成交",price=10,quantity=100,fee=5,side="BUY")])
    rows,residual=stock_ledger(history,bars,1000)
    assert residual == 0 and rows["A"]==dict(net=215,shares=110,value=1210,fees=5,dividends=10,fills=1)
    with pytest.raises(ValueError):
        stock_ledger(history,{},1000)
    with pytest.raises(ValueError):
        stock_ledger(history,bars,1100)


def test_intraday_net_is_visible_but_not_counted_as_close_risk():
    points=[dict(at="2026-11-26T15:00",equity=105,benchmark=102),
            dict(at="2026-11-27T11:00",equity=85,benchmark=98)]
    out=report(points,100,0)
    assert out["netReturn"] == pytest.approx(-.15)
    assert out["closeDays"]==1 and out["days"]==2 and out["returnDays"]==0 and out["maxDrawdown"]==0
    assert report(points[-1:],100,0)["maxDrawdown"] is None
