from __future__ import annotations

from datetime import datetime
from typing import Protocol

from finscope_market_data.discovery.schemas import DiscoverySector


class HotSectorProvider(Protocol):
    source_code: str
    source_family: str

    def sectors(self, limit: int) -> list[DiscoverySector]: ...


class TonghuashunHotSectorProvider:
    """The sole production authority for the hot-sector ranking."""

    source_code = "AKSHARE_TONGHUASHUN_SECTOR_FLOW"
    source_family = "TONGHUASHUN"

    def sectors(self, limit: int) -> list[DiscoverySector]:
        import akshare as ak

        retrieved_at = datetime.now().isoformat()
        code_frame = ak.stock_board_industry_name_ths()
        code_map = {
            str(row.get("name", "")).strip(): str(row.get("code", "")).strip()
            for _, row in code_frame.iterrows()
        }
        frame = ak.stock_board_industry_summary_ths()
        ordered = frame.sort_values("净流入", ascending=False).head(limit)
        result: list[DiscoverySector] = []
        for rank, (_, row) in enumerate(ordered.iterrows(), start=1):
            name = str(row.get("板块", "")).strip()
            code = code_map.get(name, "")
            if not name or not code:
                continue
            expected = _integer(row.get("上涨家数")) + _integer(row.get("下跌家数"))
            result.append(
                DiscoverySector(
                    code=code,
                    name=name,
                    category="INDUSTRY",
                    source_code=self.source_code,
                    source_family=self.source_family,
                    period="1D",
                    source_rank=rank,
                    change_pct=_number(row.get("涨跌幅")),
                    main_net_inflow=_yuan_from_yi(row.get("净流入")),
                    leader_stock_name=_text(row.get("领涨股")),
                    expected_constituent_count=expected,
                    retrieved_at=retrieved_at,
                )
            )
        if not result:
            raise RuntimeError("同花顺热门行业榜单为空或缺少行业代码")
        return result


def _number(value: object) -> float | None:
    try:
        number = float(value)
        return number if number == number else None
    except (TypeError, ValueError):
        return None


def _integer(value: object) -> int:
    number = _number(value)
    return max(0, int(number)) if number is not None else 0


def _yuan_from_yi(value: object) -> float | None:
    number = _number(value)
    return number * 100_000_000 if number is not None else None


def _text(value: object) -> str | None:
    text = str(value).strip() if value is not None else ""
    return text if text and text.lower() != "nan" else None
