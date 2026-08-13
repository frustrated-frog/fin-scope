from __future__ import annotations

from typing import Literal

from pydantic import BaseModel, Field


class DiscoveryRequest(BaseModel):
    business_date: str | None = None
    budget: float = Field(default=6000.0, gt=0)
    sector_limit: int = Field(default=5, ge=1, le=10)
    deep_limit: int = Field(default=15, ge=5, le=30)
    final_limit: int = Field(default=5, ge=1, le=5)
    horizon_days: Literal[1, 5, 20] = 5
    policy_version: str = "stock-discovery-v1"


class DiscoverySector(BaseModel):
    code: str
    name: str
    category: Literal["INDUSTRY", "CONCEPT"]
    source_code: str
    source_family: str
    period: str = "5D"
    source_rank: int = Field(ge=1)
    change_pct: float | None = None
    main_net_inflow: float | None = None
    main_net_inflow_ratio: float | None = None
    leader_stock_name: str | None = None
    retrieved_at: str


class DiscoveryCandidate(BaseModel):
    code: str
    market: Literal["SH", "SZ"]
    name: str
    price: float = Field(gt=0)
    lot_cost: float = Field(gt=0)
    budget_eligible: bool
    admitted: bool
    rejection_reasons: list[str] = Field(default_factory=list)
    sector_codes: list[str] = Field(default_factory=list)
    sector_names: list[str] = Field(default_factory=list)
    factors: dict[str, float] = Field(default_factory=dict)
    lightweight_score: float | None = None
    lightweight_rank: int | None = None


class DeepCandidateEvidence(BaseModel):
    code: str
    qualified: bool
    conclusion: str
    calibrated_probability: float = Field(ge=0, le=1)
    probability_lower_bound: float = Field(ge=0, le=1)
    brier_skill_score: float
    locked_accuracy: float = Field(ge=0, le=1)
    locked_log_loss: float = Field(ge=0)
    risk_adjusted_return: float
    max_drawdown: float
    stability_score: float = Field(ge=0, le=1)
    health_status: str
    deep_score: float | None = None
    final_rank: int | None = None
    evidence: list[str] = Field(default_factory=list)
    risks: list[str] = Field(default_factory=list)
    forecast_report: dict[str, object] | None = None


class DiscoveryFunnel(BaseModel):
    constituent_count: int = 0
    admitted_count: int = 0
    quantified_count: int = 0
    deep_review_count: int = 0
    final_count: int = 0


class DiscoveryReport(BaseModel):
    schema_version: str = "1.0.0"
    policy_version: str
    as_of_date: str
    source_code: str
    source_family: str
    quality_status: str
    retrieved_at: str
    data_fingerprint: str
    budget: float
    sectors: list[DiscoverySector]
    candidates: list[DiscoveryCandidate]
    deep_evidence: list[DeepCandidateEvidence]
    final_candidates: list[DeepCandidateEvidence]
    funnel: DiscoveryFunnel
    warnings: list[str] = Field(default_factory=list)
    duration_ms: int = Field(default=0, ge=0)
