from datetime import datetime
import json

import pytest

from finscope_market_data.models import MinutePriceBar, StockSymbol
from finscope_market_data.paper.engine import SHANGHAI
from finscope_market_data.paper.lab import PaperLab


@pytest.mark.asyncio
async def test_ex_date_window_survives_next_bar_and_restart(tmp_path, monkeypatch):
    symbol = StockSymbol(code="600000", market="SH")
    def bar(day, minute):
        at = datetime(2026, 11, day, 9, minute, tzinfo=SHANGHAI)
        return MinutePriceBar(symbol=symbol, observed_at=at, frequency_minutes=5,
                              open=10, high=11, low=9, close=10, volume=100)
    queries = []
    async def actions(router, limit, code, start, end):
        queries.append((start, end))
        return [{"ex_date":"2026-11-24"}]
    monkeypatch.setattr("finscope_market_data.paper.lab.read_actions", actions)
    lab=PaperLab(tmp_path/"paper.db",lambda:None)
    book={}
    history=[bar(23,35),bar(24,35),bar(24,40)]
    first=await lab.forward_actions({"600000":history},book,"2026-11-23",history[-1].observed_at)
    assert first["600000"]==[history[1],first["600000"][-1]]
    assert first["600000"][-1].actions and first["600000"][-1].windowClipped
    book["lastObserved"]={"600000":history[-1].observed_at.isoformat()}
    restored=PaperLab(tmp_path/"paper.db",lambda:None)
    history.append(bar(24,45))
    second=await restored.forward_actions({"600000":history},json.loads(json.dumps(book)),"2026-11-23",history[-1].observed_at)
    assert second["600000"]==history[1:] and len(queries)==1
