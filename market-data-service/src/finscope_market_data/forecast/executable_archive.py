"""Typed, fail-closed input for generating an executable baseline from archived evidence."""
from __future__ import annotations

from datetime import date, datetime
from typing import Literal

from pydantic import BaseModel, ConfigDict, Field, model_validator

from finscope_market_data.models import DailyBar


class Contract(BaseModel):
    model_config = ConfigDict(extra='forbid', allow_inf_nan=False)


class BaselineProtocol(Contract):
    version: Literal['OPEN_5D_V1']
    holdingTradingDays: Literal[5]
    rebalanceTradingDays: Literal[5]
    slots: Literal[5]
    maxExposure: float = Field(gt=0, le=1)
    maxSingleWeight: float = Field(gt=0, le=1)
    maxIndustryWeight: float = Field(gt=0, le=1)
    signalTime: Literal['15:30']
    executionTime: Literal['09:30']
    initialCapital: float = Field(ge=100, le=1_000_000_000)
    buyCommission: float = Field(ge=0, le=0.1)
    sellCommission: float = Field(ge=0, le=0.1)
    minimumCommission: float = Field(ge=0, le=1000)
    stampDuty: float = Field(ge=0, le=0.1)
    slippageBps: float = Field(ge=0, le=1000)

    @model_validator(mode='after')
    def valid_weights(self):
        if (self.maxSingleWeight > self.maxExposure / self.slots
                or not self.maxSingleWeight <= self.maxIndustryWeight <= self.maxExposure):
            raise ValueError('Invalid portfolio weight limits')
        return self


class UniverseObservation(Contract):
    signalDate: date
    instrumentCode: str = Field(pattern=r'^(?:(?:600|601|603|605)\d{3}\.SH|(?:000|001|002|003)\d{3}\.SZ)$')
    availableAt: datetime
    industry: str = Field(min_length=1, max_length=2000)
    eligible: bool = Field(strict=True)
    rejectionReason: str | None = None
    evidence: str = Field(min_length=1, max_length=2000)


class ExecutionObservation(Contract):
    tradeDate: date
    instrumentCode: str
    open: float = Field(ge=0.01, le=1_000_000)
    close: float = Field(ge=0.01, le=1_000_000)
    openState: Literal['TRADABLE', 'BUY_BLOCKED', 'SELL_BLOCKED', 'SUSPENDED']
    sourceEvidence: str = Field(min_length=1, max_length=2000)


class CorporateCoverage(Contract):
    instrumentCode: str
    coverageFrom: date
    coverageThrough: date
    exDates: list[date]
    evidence: str = Field(min_length=1, max_length=2000)


class BaselineArchive(Contract):
    schemaVersion: Literal['EXECUTABLE_ARCHIVE_V1']
    protocol: BaselineProtocol
    startDate: date
    endDate: date
    priceBasis: Literal['RAW']
    minAverageAmount20d: float = Field(ge=0)
    universeEvidence: str = Field(min_length=1, max_length=2000)
    completeUniverseDates: list[date]
    universe: list[UniverseObservation]
    executionBars: list[ExecutionObservation]
    corporateCoverage: list[CorporateCoverage]
    # Loaded by the read-only snapshot adapter or supplied in an offline archive.
    researchHistories: dict[str, list[DailyBar]]
