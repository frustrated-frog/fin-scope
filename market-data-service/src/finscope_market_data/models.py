from __future__ import annotations

from datetime import datetime
from enum import Enum
from typing import Generic, TypeVar

from pydantic import BaseModel, ConfigDict, Field, field_validator


class Market(str, Enum):
    SH = "SH"
    SZ = "SZ"
    BJ = "BJ"


class DataCapability(str, Enum):
    QUOTE = "QUOTE"
    DAILY_BARS = "DAILY_BARS"
    CAPITAL_FLOW = "CAPITAL_FLOW"
    PROFILE = "PROFILE"
    FINANCIAL_STATEMENTS = "FINANCIAL_STATEMENTS"


class QualityStatus(str, Enum):
    FRESH_PRIMARY = "FRESH_PRIMARY"
    FRESH_FALLBACK = "FRESH_FALLBACK"
    PARTIAL_FRESH = "PARTIAL_FRESH"
    STALE_FALLBACK = "STALE_FALLBACK"
    UNAVAILABLE = "UNAVAILABLE"


class StockSymbol(BaseModel):
    model_config = ConfigDict(frozen=True)

    market: Market
    code: str

    @field_validator("market", mode="before")
    @classmethod
    def normalize_market(cls, value: object) -> object:
        return value.upper() if isinstance(value, str) else value

    @field_validator("code")
    @classmethod
    def validate_code(cls, value: str) -> str:
        normalized = value.strip()
        if len(normalized) != 6 or not normalized.isdigit():
            raise ValueError("A-share code must contain exactly six digits")
        return normalized

    @property
    def eastmoney_secid(self) -> str:
        return f"{'1' if self.market is Market.SH else '0'}.{self.code}"

    @property
    def prefixed_code(self) -> str:
        return f"{self.market.value.lower()}{self.code}"

    @property
    def cache_key(self) -> str:
        return f"{self.market.value}:{self.code}"


class ProviderAttempt(BaseModel):
    provider_code: str
    provider_family: str
    success: bool
    duration_ms: int = Field(ge=0)
    error_type: str | None = None
    error_message: str | None = None
    retry_count: int = Field(default=0, ge=0)


class StockQuote(BaseModel):
    symbol: StockSymbol
    name: str | None = None
    price: float
    previous_close: float | None = None
    open: float | None = None
    high: float | None = None
    low: float | None = None
    change: float | None = None
    change_pct: float | None = None
    volume: float | None = None
    amount: float | None = None
    turnover_rate: float | None = None
    volume_ratio: float | None = None
    pe_ratio: float | None = None
    pb_ratio: float | None = None
    market_cap: float | None = None
    circulating_market_cap: float | None = None
    bid_price: float | None = None
    ask_price: float | None = None
    observed_at: datetime


class DailyBar(BaseModel):
    symbol: StockSymbol
    trade_date: str
    open: float
    high: float
    low: float
    close: float
    volume: float
    amount: float | None = None
    amplitude: float | None = None
    change_pct: float | None = None
    change: float | None = None
    turnover_rate: float | None = None


class CapitalFlowPoint(BaseModel):
    symbol: StockSymbol
    granularity: str
    observed_at: datetime
    price: float | None = None
    change_pct: float | None = None
    main_net_inflow: float | None = None
    main_net_inflow_ratio: float | None = None
    super_large_net_inflow: float | None = None
    super_large_net_inflow_ratio: float | None = None
    large_net_inflow: float | None = None
    large_net_inflow_ratio: float | None = None
    medium_net_inflow: float | None = None
    medium_net_inflow_ratio: float | None = None
    small_net_inflow: float | None = None
    small_net_inflow_ratio: float | None = None
    volume: float | None = None
    amount: float | None = None
    turnover_rate: float | None = None
    volume_ratio: float | None = None
    quality_status: str = "COMPLETE"


class CapitalFlowData(BaseModel):
    minute_points: list[CapitalFlowPoint] = Field(default_factory=list)
    daily_points: list[CapitalFlowPoint] = Field(default_factory=list)
    turnover_rate: float | None = None
    volume_ratio: float | None = None
    warnings: list[str] = Field(default_factory=list)


class StockProfile(BaseModel):
    symbol: StockSymbol
    name: str | None = None
    industry: str | None = None
    concepts: list[str] = Field(default_factory=list)
    listing_date: str | None = None
    total_shares: float | None = None
    circulating_shares: float | None = None
    fields: dict[str, str | float | None] = Field(default_factory=dict)


class FinancialStatementType(str, Enum):
    INCOME = "INCOME"
    BALANCE_SHEET = "BALANCE_SHEET"
    CASH_FLOW = "CASH_FLOW"


class FinancialReportMeta(BaseModel):
    symbol: StockSymbol
    period_end: str
    report_type: str
    scope: str = "CONSOLIDATED"
    published_at: datetime | None = None
    audited: bool | None = None
    currency: str = "CNY"


class FinancialStatementValue(BaseModel):
    source_label: str
    concept_code: str | None = None
    period_role: str
    value: str | None = None
    unit_multiplier: str = "1"
    source_field: str | None = None


class FinancialStatement(BaseModel):
    statement_type: FinancialStatementType
    values: list[FinancialStatementValue] = Field(default_factory=list)


class FinancialStatementsData(BaseModel):
    report: FinancialReportMeta
    statements: list[FinancialStatement] = Field(default_factory=list)
    warnings: list[str] = Field(default_factory=list)


PayloadT = TypeVar("PayloadT")


class DataEnvelope(BaseModel, Generic[PayloadT]):
    capability: DataCapability
    symbol: StockSymbol
    quality_status: QualityStatus
    source_code: str | None = None
    source_family: str | None = None
    as_of: datetime | None = None
    retrieved_at: datetime
    stale_age_seconds: int | None = Field(default=None, ge=0)
    warnings: list[str] = Field(default_factory=list)
    attempts: list[ProviderAttempt] = Field(default_factory=list)
    data: PayloadT | None = None


class ProviderHealth(BaseModel):
    provider_code: str
    provider_family: str
    capabilities: set[DataCapability]
    state: str
    consecutive_failures: int = 0
    open_until: datetime | None = None
    last_success_at: datetime | None = None
    last_failure_at: datetime | None = None
    last_error: str | None = None
