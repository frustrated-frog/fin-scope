from __future__ import annotations

from datetime import datetime
from typing import Protocol

from finscope_market_data.discovery.schemas import DiscoverySector


class HotSectorProvider(Protocol):
    source_code: str
    source_family: str

    def sectors(self, limit: int) -> list[DiscoverySector]: ...

    def constituents(self, sector: DiscoverySector) -> list[tuple[str, str, str]]: ...


class EastmoneyHotSectorProvider:
    source_code = "AKSHARE_EASTMONEY_SECTOR_FLOW"
    source_family = "EASTMONEY"

    def sectors(self, limit: int) -> list[DiscoverySector]:
        import akshare as ak

        retrieved_at = datetime.now().isoformat()
        values: list[DiscoverySector] = []
        for category, sector_type in (
            ("INDUSTRY", "行业资金流"),
            ("CONCEPT", "概念资金流"),
        ):
            frame = ak.stock_sector_fund_flow_rank(
                indicator="5日", sector_type=sector_type
            )
            code_map = self._code_map(ak, category)
            for rank, (_, row) in enumerate(frame.head(limit).iterrows(), start=1):
                name = str(row.get("名称", "")).strip()
                if not name:
                    continue
                values.append(
                    DiscoverySector(
                        code=code_map.get(name, name),
                        name=name,
                        category=category,
                        source_code=self.source_code,
                        source_family=self.source_family,
                        source_rank=rank,
                        change_pct=_number(row.get("5日涨跌幅")),
                        main_net_inflow=_number(row.get("5日主力净流入-净额")),
                        main_net_inflow_ratio=_number(
                            row.get("5日主力净流入-净占比")
                        ),
                        leader_stock_name=_text(row.get("5日主力净流入最大股")),
                        retrieved_at=retrieved_at,
                    )
                )
        if not values:
            raise RuntimeError("东方财富热门板块榜单为空")
        return values

    def constituents(self, sector: DiscoverySector) -> list[tuple[str, str, str]]:
        import akshare as ak

        frame = (
            ak.stock_board_industry_cons_em(symbol=sector.name)
            if sector.category == "INDUSTRY"
            else ak.stock_board_concept_cons_em(symbol=sector.name)
        )
        result: list[tuple[str, str, str]] = []
        for _, row in frame.iterrows():
            code = str(row.get("代码", "")).strip().zfill(6)
            name = str(row.get("名称", "")).strip()
            if len(code) != 6 or not code.isdigit() or not name:
                continue
            market = "SH" if code.startswith(("5", "6", "9")) else "SZ"
            result.append((code, market, name))
        return result

    def _code_map(self, ak: object, category: str) -> dict[str, str]:
        frame = (
            ak.stock_board_industry_name_em()
            if category == "INDUSTRY"
            else ak.stock_board_concept_name_em()
        )
        return {
            str(row.get("板块名称", "")).strip(): str(
                row.get("板块代码", "")
            ).strip()
            for _, row in frame.iterrows()
        }


class TonghuashunHotSectorProvider:
    """同花顺公开行业热榜；契约漂移时由服务自动切换东方财富。"""

    source_code = "AKSHARE_TONGHUASHUN_SECTOR_FLOW"
    source_family = "TONGHUASHUN"

    def sectors(self, limit: int) -> list[DiscoverySector]:
        import akshare as ak

        frame = ak.stock_board_industry_summary_ths()
        retrieved_at = datetime.now().isoformat()
        result: list[DiscoverySector] = []
        ordered = frame.sort_values("净流入", ascending=False).head(limit)
        for rank, (_, row) in enumerate(ordered.iterrows(), start=1):
            name = str(row.get("板块", "")).strip()
            if not name:
                continue
            result.append(
                DiscoverySector(
                    code=name,
                    name=name,
                    category="INDUSTRY",
                    source_code=self.source_code,
                    source_family=self.source_family,
                    period="1D",
                    source_rank=rank,
                    change_pct=_number(row.get("涨跌幅")),
                    main_net_inflow=_number(row.get("净流入")),
                    leader_stock_name=_text(row.get("领涨股")),
                    retrieved_at=retrieved_at,
                )
            )
        if not result:
            raise RuntimeError("同花顺热门行业榜单为空")
        return result

    def constituents(self, sector: DiscoverySector) -> list[tuple[str, str, str]]:
        import akshare as ak

        # 同花顺行业成分接口不稳定，使用公开板块页的统一成分接口。
        function = getattr(ak, "stock_board_cons_ths", None)
        if function is None:
            raise RuntimeError("当前 AkShare 不提供同花顺板块成分接口")
        frame = function(symbol=sector.name)
        result: list[tuple[str, str, str]] = []
        for _, row in frame.iterrows():
            code = str(row.get("代码", row.get("code", ""))).strip().zfill(6)
            name = str(row.get("名称", row.get("name", ""))).strip()
            if len(code) == 6 and code.isdigit() and name:
                result.append(
                    (code, "SH" if code.startswith(("5", "6", "9")) else "SZ", name)
                )
        return result


def _number(value: object) -> float | None:
    try:
        number = float(value)
        return number if number == number else None
    except (TypeError, ValueError):
        return None


def _text(value: object) -> str | None:
    text = str(value).strip() if value is not None else ""
    return text if text and text.lower() != "nan" else None
