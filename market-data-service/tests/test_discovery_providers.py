from __future__ import annotations

import sys
from types import SimpleNamespace

import pandas as pd

from finscope_market_data.discovery.providers import (
    SinaHotSectorProvider,
    TonghuashunHotSectorProvider,
)
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


def test_tonghuashun_fallback_uses_its_real_industry_flow_contract(monkeypatch) -> None:
    fake_akshare = SimpleNamespace(
        stock_board_industry_summary_ths=lambda: pd.DataFrame(
            [{"板块": "机器人", "净流入": 20, "涨跌幅": 1.2}]
        )
    )
    monkeypatch.setitem(sys.modules, "akshare", fake_akshare)

    values = TonghuashunHotSectorProvider().sectors(1)

    assert [(item.category, item.name, item.period) for item in values] == [
        ("INDUSTRY", "机器人", "1D")
    ]


def test_sina_fallback_ranks_industries_and_preserves_source(monkeypatch) -> None:
    fake_akshare = SimpleNamespace(
        stock_sector_spot=lambda indicator: pd.DataFrame(
            [
                {"label": "new_jrhy", "板块": "金融", "涨跌幅": 1.1, "总成交额": 10},
                {"label": "new_jqsb", "板块": "机器人", "涨跌幅": 2.3, "总成交额": 20},
            ]
        ),
        stock_sector_detail=lambda sector: pd.DataFrame(
            [{"code": "600001", "name": "样本股份"}]
        ),
    )
    monkeypatch.setitem(sys.modules, "akshare", fake_akshare)
    provider = SinaHotSectorProvider()

    sectors = provider.sectors(1)
    members = provider.constituents(sectors[0])

    assert [(item.name, item.period, item.source_family) for item in sectors] == [
        ("机器人", "1D", "SINA")
    ]
    assert members == [("600001", "SH", "样本股份")]
