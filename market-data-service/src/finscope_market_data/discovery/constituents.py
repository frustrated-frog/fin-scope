from __future__ import annotations

from dataclasses import dataclass
from datetime import datetime
import json
from pathlib import Path
from typing import Callable

from finscope_market_data.discovery.schemas import DiscoverySector


ConstituentValue = tuple[str, str, str]


@dataclass(frozen=True)
class ConstituentBatch:
    sector_code: str
    sector_name: str
    source_family: str
    values: tuple[ConstituentValue, ...]
    expected_count: int
    retrieved_count: int
    quality_status: str
    coverage: float
    retrieved_at: str
    warning: str = ""


class ConstituentSnapshotStore:
    def __init__(
        self,
        path: str | Path,
        ttl_days: int = 30,
        now: Callable[[], datetime] | None = None,
    ) -> None:
        self.path = Path(path)
        self.ttl_days = max(1, ttl_days)
        self.now = now or datetime.now

    def save(self, sector: DiscoverySector, batch: ConstituentBatch) -> None:
        if batch.quality_status != "COMPLETE":
            return
        payload = self._read() or {"version": 1, "sectors": {}}
        payload.setdefault("sectors", {})[sector.code] = {
            "sector_code": batch.sector_code,
            "sector_name": batch.sector_name,
            "source_family": batch.source_family,
            "values": [list(item) for item in batch.values],
            "expected_count": batch.expected_count,
            "retrieved_count": batch.retrieved_count,
            "quality_status": batch.quality_status,
            "coverage": batch.coverage,
            "retrieved_at": batch.retrieved_at,
            "warning": batch.warning,
            "snapshot_at": self.now().isoformat(),
        }
        self.path.parent.mkdir(parents=True, exist_ok=True)
        temporary = self.path.with_suffix(".tmp")
        temporary.write_text(
            json.dumps(payload, ensure_ascii=False, sort_keys=True), encoding="utf-8"
        )
        temporary.replace(self.path)

    def load(self, sector: DiscoverySector) -> ConstituentBatch | None:
        payload = self._read()
        if not payload:
            return None
        try:
            item = payload["sectors"][sector.code]
            snapshot_at = datetime.fromisoformat(str(item["snapshot_at"]))
            if (self.now() - snapshot_at).total_seconds() > self.ttl_days * 86400:
                return None
            expected = int(item["expected_count"])
            if sector.expected_constituent_count and expected < sector.expected_constituent_count:
                return None
            return ConstituentBatch(
                sector_code=str(item["sector_code"]),
                sector_name=str(item["sector_name"]),
                source_family=str(item["source_family"]),
                values=tuple(tuple(value) for value in item["values"]),
                expected_count=expected,
                retrieved_count=int(item["retrieved_count"]),
                quality_status=str(item["quality_status"]),
                coverage=float(item["coverage"]),
                retrieved_at=str(item["retrieved_at"]),
                warning=str(item.get("warning", "")),
            )
        except (KeyError, TypeError, ValueError):
            return None

    def _read(self) -> dict[str, object] | None:
        if not self.path.exists():
            return None
        try:
            value = json.loads(self.path.read_text(encoding="utf-8"))
            return value if isinstance(value, dict) else None
        except (OSError, json.JSONDecodeError):
            return None
