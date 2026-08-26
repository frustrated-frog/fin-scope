from __future__ import annotations

from datetime import datetime, timedelta
import json

from finscope_market_data.discovery.constituents import (
    ConstituentBatch,
    ConstituentSnapshotStore,
)
from finscope_market_data.discovery.schemas import DiscoverySector


def _sector(expected: int = 1) -> DiscoverySector:
    return DiscoverySector(
        code="881121",
        name="半导体",
        category="INDUSTRY",
        source_code="AKSHARE_TONGHUASHUN_SECTOR_FLOW",
        source_family="TONGHUASHUN",
        source_rank=1,
        expected_constituent_count=expected,
        retrieved_at="2026-08-17T15:30:00",
    )


def _complete_batch() -> ConstituentBatch:
    return ConstituentBatch(
        sector_code="881121",
        sector_name="半导体",
        source_family="FUYAO_TONGHUASHUN",
        values=(("600584", "SH", "长电科技"),),
        expected_count=1,
        retrieved_count=1,
        quality_status="COMPLETE",
        coverage=1.0,
        retrieved_at="2026-08-17T15:30:00",
    )


def test_complete_constituent_snapshot_expires_after_thirty_days(tmp_path) -> None:
    now = datetime(2026, 8, 17, 15, 30)
    store = ConstituentSnapshotStore(
        tmp_path / "constituents.json", now=lambda: now
    )
    batch = _complete_batch()

    store.save(_sector(), batch)

    assert store.load(_sector()) == batch
    expired = ConstituentSnapshotStore(
        tmp_path / "constituents.json", now=lambda: now + timedelta(days=31)
    )
    assert expired.load(_sector()) is None


def test_partial_constituent_batch_does_not_replace_complete_snapshot(tmp_path) -> None:
    path = tmp_path / "constituents.json"
    store = ConstituentSnapshotStore(path)
    complete = _complete_batch()
    partial = ConstituentBatch(
        **{
            **complete.__dict__,
            "quality_status": "PARTIAL",
            "coverage": 0.5,
        }
    )

    store.save(_sector(), complete)
    store.save(_sector(), partial)

    assert store.load(_sector()) == complete


def test_legacy_snapshot_with_acquisition_diagnostics_remains_readable(tmp_path) -> None:
    path = tmp_path / "constituents.json"
    path.write_text(
        json.dumps(
            {
                "version": 1,
                "sectors": {
                    "881121": {
                        "sector_code": "881121",
                        "sector_name": "半导体",
                        "source_family": "TONGHUASHUN",
                        "values": [["600584", "SH", "长电科技"]],
                        "expected_count": 1,
                        "retrieved_count": 1,
                        "quality_status": "COMPLETE",
                        "coverage": 1.0,
                        "retrieved_at": "2026-08-17T15:30:00",
                        "warning": "",
                        "acquisition_mode": "BROWSER",
                        "recovery_used": True,
                        "snapshot_at": datetime.now().isoformat(),
                    }
                },
            },
            ensure_ascii=False,
        ),
        encoding="utf-8",
    )

    batch = ConstituentSnapshotStore(path).load(_sector())

    assert batch is not None
    assert batch.values == (("600584", "SH", "长电科技"),)
