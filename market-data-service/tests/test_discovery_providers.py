from __future__ import annotations

import sys
from types import SimpleNamespace

import pandas as pd

from finscope_market_data.discovery.providers import TonghuashunHotSectorProvider
from finscope_market_data.discovery.schemas import DiscoverySector


def test_tonghuashun_ranking_uses_available_eastmoney_constituent_contract(
    monkeypatch,
) -> None:
    fake_akshare = SimpleNamespace(
        stock_board_industry_cons_em=lambda symbol: pd.DataFrame(
            [{"代码": "600001", "名称": "样本股份"}]
        )
    )
    monkeypatch.setitem(sys.modules, "akshare", fake_akshare)
    sector = DiscoverySector(
        code="人工智能",
        name="人工智能",
        category="INDUSTRY",
        source_code="AKSHARE_TONGHUASHUN_SECTOR_FLOW",
        source_family="TONGHUASHUN",
        source_rank=1,
        retrieved_at="2026-08-14T15:30:00",
    )

    values = TonghuashunHotSectorProvider().constituents(sector)

    assert values == [("600001", "SH", "样本股份")]


def test_tonghuashun_ranking_includes_industries_and_concepts(monkeypatch) -> None:
    fake_akshare = SimpleNamespace(
        stock_board_industry_summary_ths=lambda: pd.DataFrame(
            [{"板块": "机器人", "净流入": 20, "涨跌幅": 1.2}]
        ),
        stock_board_concept_summary_ths=lambda: pd.DataFrame(
            [{"板块": "低空经济", "净流入": 30, "涨跌幅": 2.1}]
        ),
    )
    monkeypatch.setitem(sys.modules, "akshare", fake_akshare)

    values = TonghuashunHotSectorProvider().sectors(1)

    assert [(item.category, item.name) for item in values] == [
        ("INDUSTRY", "机器人"),
        ("CONCEPT", "低空经济"),
    ]
