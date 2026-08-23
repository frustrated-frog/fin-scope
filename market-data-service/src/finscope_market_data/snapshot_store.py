from __future__ import annotations

import sqlite3
from pathlib import Path
from typing import Any

from finscope_market_data.models import (
    CapitalFlowData,
    DailyBar,
    DataCapability,
    DataEnvelope,
    FinancialStatementsData,
    MarketBreadthSnapshot,
    StockProfile,
    StockQuote,
    StockSymbol,
)


PAYLOAD_MODELS: dict[DataCapability, Any] = {
    DataCapability.QUOTE: StockQuote,
    DataCapability.DAILY_BARS: list[DailyBar],
    DataCapability.CAPITAL_FLOW: CapitalFlowData,
    DataCapability.PROFILE: StockProfile,
    DataCapability.FINANCIAL_STATEMENTS: FinancialStatementsData,
}


class SnapshotStore:
    def __init__(self, path: str | Path) -> None:
        self.path = Path(path)
        self.path.parent.mkdir(parents=True, exist_ok=True)
        with self._connect() as connection:
            connection.execute(
                """
                CREATE TABLE IF NOT EXISTS market_data_snapshot (
                    capability TEXT NOT NULL,
                    symbol_key TEXT NOT NULL,
                    payload_json TEXT NOT NULL,
                    updated_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    PRIMARY KEY (capability, symbol_key)
                )
                """
            )
            connection.execute(
                """
                CREATE TABLE IF NOT EXISTS market_breadth_snapshot (
                    business_date TEXT NOT NULL PRIMARY KEY,
                    payload_json TEXT NOT NULL,
                    updated_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP
                )
                """
            )

    def save(self, envelope: DataEnvelope[Any]) -> None:
        with self._connect() as connection:
            connection.execute(
                """
                INSERT INTO market_data_snapshot(capability, symbol_key, payload_json, updated_at)
                VALUES (?, ?, ?, CURRENT_TIMESTAMP)
                ON CONFLICT(capability, symbol_key) DO UPDATE SET
                    payload_json=excluded.payload_json,
                    updated_at=CURRENT_TIMESTAMP
                """,
                (
                    envelope.capability.value,
                    envelope.symbol.cache_key,
                    envelope.model_dump_json(),
                ),
            )

    def load(
        self,
        capability: DataCapability,
        symbol: StockSymbol,
    ) -> DataEnvelope[Any] | None:
        with self._connect() as connection:
            row = connection.execute(
                "SELECT payload_json FROM market_data_snapshot WHERE capability=? AND symbol_key=?",
                (capability.value, symbol.cache_key),
            ).fetchone()
        if row is None:
            return None
        envelope_type = DataEnvelope[PAYLOAD_MODELS[capability]]
        return envelope_type.model_validate_json(row[0])

    def save_market_breadth(self, snapshot: MarketBreadthSnapshot) -> None:
        with self._connect() as connection:
            connection.execute(
                """
                INSERT INTO market_breadth_snapshot(business_date, payload_json, updated_at)
                VALUES (?, ?, CURRENT_TIMESTAMP)
                ON CONFLICT(business_date) DO UPDATE SET
                    payload_json=excluded.payload_json,
                    updated_at=CURRENT_TIMESTAMP
                """,
                (snapshot.business_date, snapshot.model_dump_json()),
            )

    def load_market_breadth(
        self,
        business_date: str,
    ) -> MarketBreadthSnapshot | None:
        with self._connect() as connection:
            row = connection.execute(
                "SELECT payload_json FROM market_breadth_snapshot WHERE business_date=?",
                (business_date,),
            ).fetchone()
        return (
            None
            if row is None
            else MarketBreadthSnapshot.model_validate_json(row[0])
        )

    def check_ready(self) -> tuple[bool, str]:
        try:
            with self._connect() as connection:
                connection.execute("BEGIN IMMEDIATE")
                connection.execute(
                    "UPDATE market_data_snapshot SET updated_at=updated_at WHERE 0"
                )
                connection.rollback()
            return True, "UP"
        except sqlite3.Error as error:
            return False, f"SQLITE_{type(error).__name__.upper()}"

    def _connect(self) -> sqlite3.Connection:
        return sqlite3.connect(str(self.path), timeout=5)
