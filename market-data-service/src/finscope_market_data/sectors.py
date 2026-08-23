from __future__ import annotations

from collections.abc import Callable
from datetime import datetime
from typing import Any, Literal

from pydantic import BaseModel, ConfigDict, Field


SectorCategory = Literal["INDUSTRY", "CONCEPT"]
FrameLoader = Callable[[], Any]


class SectorEntry(BaseModel):
    model_config = ConfigDict(extra="forbid")

    code: str
    name: str
    category: SectorCategory
    source_rank: int | None = Field(default=None, ge=1)
    change_pct: float | None = None
    main_net_inflow: float | None = None
    leader_stock_name: str | None = None
    advance_count: int | None = Field(default=None, ge=0)
    decline_count: int | None = Field(default=None, ge=0)
    flat_count: int | None = Field(default=None, ge=0)
    breadth_ratio: float | None = Field(default=None, ge=0, le=1)


class SectorEnvelope(BaseModel):
    model_config = ConfigDict(extra="forbid")

    schema_version: Literal["sector-market-v1"] = "sector-market-v1"
    source_code: Literal["AKSHARE_TONGHUASHUN_SECTOR"] = (
        "AKSHARE_TONGHUASHUN_SECTOR"
    )
    source_family: Literal["TONGHUASHUN"] = "TONGHUASHUN"
    category: SectorCategory
    retrieved_at: str
    entries: list[SectorEntry]
    warnings: list[str] = Field(default_factory=list)


class TonghuashunSectorService:
    def __init__(
        self,
        industry_names: FrameLoader | None = None,
        industry_summary: FrameLoader | None = None,
        concept_names: FrameLoader | None = None,
    ) -> None:
        self._industry_names = industry_names or self._load_industry_names
        self._industry_summary = industry_summary or self._load_industry_summary
        self._concept_names = concept_names or self._load_concept_names

    def fetch(self, category: str) -> SectorEnvelope:
        if category == "INDUSTRY":
            entries, warnings = self._industries()
        elif category == "CONCEPT":
            entries, warnings = self._concepts()
        else:
            raise ValueError(f"unsupported sector category: {category}")
        if not entries:
            raise RuntimeError(f"同花顺{category}板块目录为空")
        return SectorEnvelope(
            category=category,
            retrieved_at=datetime.now().isoformat(),
            entries=entries,
            warnings=warnings,
        )

    def _industries(self) -> tuple[list[SectorEntry], list[str]]:
        names = self._industry_names()
        summary = self._industry_summary()
        code_by_name = {
            _text(row.get("name")): _text(row.get("code"))
            for _, row in names.iterrows()
            if _text(row.get("name")) and _text(row.get("code"))
        }
        ordered = summary.sort_values("净流入", ascending=False)
        entries: list[SectorEntry] = []
        warnings: list[str] = []
        for _, row in ordered.iterrows():
            name = _text(row.get("板块"))
            code = code_by_name.get(name)
            if not name or not code:
                warnings.append(f"行业代码缺失: {name or 'UNKNOWN'}")
                continue
            entries.append(
                SectorEntry(
                    code=code,
                    name=name,
                    category="INDUSTRY",
                    source_rank=len(entries) + 1,
                    change_pct=_number(row.get("涨跌幅")),
                    main_net_inflow=_yuan_from_yi(row.get("净流入")),
                    leader_stock_name=_text(row.get("领涨股")),
                    advance_count=_integer(row.get("上涨家数")),
                    decline_count=_integer(row.get("下跌家数")),
                    flat_count=_integer(row.get("平盘家数")) or 0,
                    breadth_ratio=_breadth_ratio(row),
                )
            )
        return entries, warnings

    def _concepts(self) -> tuple[list[SectorEntry], list[str]]:
        names = self._concept_names()
        entries: list[SectorEntry] = []
        warnings: list[str] = []
        seen: set[str] = set()
        for _, row in names.iterrows():
            name = _text(row.get("name"))
            code = _text(row.get("code"))
            if not name or not code:
                warnings.append("概念名称或代码缺失")
                continue
            if code in seen:
                warnings.append(f"概念代码重复: {code}")
                continue
            seen.add(code)
            entries.append(
                SectorEntry(code=code, name=name, category="CONCEPT")
            )
        return entries, warnings

    @staticmethod
    def _load_industry_names() -> Any:
        import akshare as ak

        return ak.stock_board_industry_name_ths()

    @staticmethod
    def _load_industry_summary() -> Any:
        import akshare as ak

        return ak.stock_board_industry_summary_ths()

    @staticmethod
    def _load_concept_names() -> Any:
        import akshare as ak

        return ak.stock_board_concept_name_ths()


def _number(value: object) -> float | None:
    try:
        number = float(value)
    except (TypeError, ValueError):
        return None
    return number if number == number else None


def _yuan_from_yi(value: object) -> float | None:
    number = _number(value)
    return number * 100_000_000 if number is not None else None


def _integer(value: object) -> int | None:
    number = _number(value)
    return max(0, int(number)) if number is not None else None


def _breadth_ratio(row: Any) -> float | None:
    advance = _integer(row.get("上涨家数"))
    decline = _integer(row.get("下跌家数"))
    flat = _integer(row.get("平盘家数")) or 0
    if advance is None or decline is None:
        return None
    total = advance + decline + flat
    return advance / total if total > 0 else None


def _text(value: object) -> str:
    text = str(value).strip() if value is not None else ""
    return "" if not text or text.lower() == "nan" else text
