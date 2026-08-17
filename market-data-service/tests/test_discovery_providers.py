from __future__ import annotations

import sys
from types import SimpleNamespace

import pandas as pd

from finscope_market_data.discovery.providers import TonghuashunHotSectorProvider


def test_tonghuashun_ranking_preserves_its_codes_and_expected_member_count(
    monkeypatch,
) -> None:
    fake_akshare = SimpleNamespace(
        stock_board_industry_summary_ths=lambda: pd.DataFrame(
            [{"板块": "半导体", "净流入": 20, "涨跌幅": 1.2,
              "上涨家数": 17, "下跌家数": 3}]
        ),
        stock_board_industry_name_ths=lambda: pd.DataFrame(
            [{"name": "半导体", "code": "881121"}]
        ),
    )
    monkeypatch.setitem(sys.modules, "akshare", fake_akshare)

    values = TonghuashunHotSectorProvider().sectors(1)

    assert values[0].code == "881121"
    assert values[0].expected_constituent_count == 20
    assert values[0].main_net_inflow == 2_000_000_000


def test_tonghuashun_fallback_uses_its_real_industry_flow_contract(monkeypatch) -> None:
    fake_akshare = SimpleNamespace(
        stock_board_industry_summary_ths=lambda: pd.DataFrame(
            [{"板块": "机器人", "净流入": 20, "涨跌幅": 1.2,
              "上涨家数": 10, "下跌家数": 2}]
        ),
        stock_board_industry_name_ths=lambda: pd.DataFrame(
            [{"name": "机器人", "code": "881125"}]
        ),
    )
    monkeypatch.setitem(sys.modules, "akshare", fake_akshare)

    values = TonghuashunHotSectorProvider().sectors(1)

    assert [(item.category, item.name, item.period) for item in values] == [
        ("INDUSTRY", "机器人", "1D")
    ]
