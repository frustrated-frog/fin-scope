from __future__ import annotations

import pandas as pd

from finscope_market_data.sectors import TonghuashunSectorService


def test_industry_catalog_merges_codes_with_daily_money_flow_ranking() -> None:
    service = TonghuashunSectorService(
        industry_names=lambda: pd.DataFrame(
            [
                {"name": "半导体", "code": "881121"},
                {"name": "白酒", "code": "881273"},
            ]
        ),
        industry_summary=lambda: pd.DataFrame(
            [
                {"板块": "白酒", "净流入": -3.5, "涨跌幅": -1.1, "领涨股": "贵州茅台", "上涨家数": 8, "下跌家数": 22},
                {"板块": "半导体", "净流入": 12.0, "涨跌幅": 2.4, "领涨股": "中芯国际", "上涨家数": 48, "下跌家数": 12},
            ]
        ),
        concept_names=lambda: pd.DataFrame(),
    )

    result = service.fetch("INDUSTRY")

    assert result.source_family == "TONGHUASHUN"
    assert [item.code for item in result.entries] == ["881121", "881273"]
    assert result.entries[0].source_rank == 1
    assert result.entries[0].main_net_inflow == 1_200_000_000
    assert result.entries[0].change_pct == 2.4
    assert result.entries[0].leader_stock_name == "中芯国际"
    assert result.entries[0].advance_count == 48
    assert result.entries[0].decline_count == 12
    assert result.entries[0].flat_count == 0
    assert result.entries[0].breadth_ratio == 0.8
    assert result.entries[1].source_rank == 2
    assert result.entries[1].main_net_inflow == -350_000_000


def test_concept_catalog_returns_all_ths_codes_without_fabricated_ranking() -> None:
    service = TonghuashunSectorService(
        industry_names=lambda: pd.DataFrame(),
        industry_summary=lambda: pd.DataFrame(),
        concept_names=lambda: pd.DataFrame(
            [
                {"name": "AI手机", "code": "309120"},
                {"name": "阿里巴巴概念", "code": "301558"},
            ]
        ),
    )

    result = service.fetch("CONCEPT")

    assert [item.code for item in result.entries] == ["309120", "301558"]
    assert all(item.category == "CONCEPT" for item in result.entries)
    assert all(item.source_rank is None for item in result.entries)
    assert all(item.main_net_inflow is None for item in result.entries)
    assert all(item.breadth_ratio is None for item in result.entries)


def test_sector_catalog_rejects_unknown_category() -> None:
    service = TonghuashunSectorService(
        industry_names=lambda: pd.DataFrame(),
        industry_summary=lambda: pd.DataFrame(),
        concept_names=lambda: pd.DataFrame(),
    )

    try:
        service.fetch("ALL")
    except ValueError as error:
        assert str(error) == "unsupported sector category: ALL"
    else:
        raise AssertionError("unknown category must be rejected")
