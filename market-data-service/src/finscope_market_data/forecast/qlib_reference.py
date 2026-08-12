from __future__ import annotations

import csv
from dataclasses import dataclass
from importlib.util import find_spec
from pathlib import Path
from typing import Sequence

from finscope_market_data.models import DailyBar


@dataclass(frozen=True)
class QlibReferenceStatus:
    status: str
    message: str
    runtime_dependency: bool = False


@dataclass(frozen=True)
class ResearchDatasetExport:
    path: Path
    row_count: int


def qlib_status() -> QlibReferenceStatus:
    if find_spec("qlib") is None:
        return QlibReferenceStatus(
            status="NOT_INSTALLED",
            message="Qlib 未安装；如需研究对照，请在独立环境中读取导出的 CSV。",
        )
    return QlibReferenceStatus(
        status="AVAILABLE",
        message="检测到 Qlib，但 FinScope 在线预测仍不依赖或调用它。",
    )


def export_research_dataset(
    bars: Sequence[DailyBar], target: str | Path
) -> ResearchDatasetExport:
    ordered = sorted(bars, key=lambda item: item.trade_date)
    if not ordered:
        raise ValueError("Qlib 研究数据导出不能为空")
    if any(item.adjustment != "QFQ" for item in ordered):
        raise ValueError("Qlib 研究数据导出只接受前复权日线")
    symbols = {item.symbol.cache_key for item in ordered}
    if len(symbols) != 1:
        raise ValueError("一次 Qlib 研究数据导出只允许一只股票")

    destination = Path(target)
    destination.parent.mkdir(parents=True, exist_ok=True)
    fields = (
        "date", "instrument", "open", "high", "low", "close",
        "volume", "amount", "adjustment",
    )
    with destination.open("w", encoding="utf-8", newline="") as output:
        writer = csv.DictWriter(output, fieldnames=fields)
        writer.writeheader()
        for bar in ordered:
            writer.writerow(
                {
                    "date": bar.trade_date,
                    "instrument": f"{bar.symbol.code}.{bar.symbol.market.value}",
                    "open": bar.open,
                    "high": bar.high,
                    "low": bar.low,
                    "close": bar.close,
                    "volume": bar.volume,
                    "amount": "" if bar.amount is None else bar.amount,
                    "adjustment": bar.adjustment,
                }
            )
    return ResearchDatasetExport(path=destination, row_count=len(ordered))
