from __future__ import annotations

from datetime import date
from typing import Literal

from pydantic import BaseModel, ConfigDict, Field, field_validator


def _to_camel(value: str) -> str:
    head, *tail = value.split("_")
    return head + "".join(part.title() for part in tail)


class DiscoveryRequest(BaseModel):
    model_config = ConfigDict(
        alias_generator=_to_camel,
        populate_by_name=True,
        extra="forbid",
    )

    business_date: str | None = None
    budget: float = Field(default=6000.0, gt=0)
    sector_limit: int = Field(default=5, ge=1, le=10)
    deep_limit: int = Field(default=15, ge=5, le=30)
    final_limit: int = Field(default=5, ge=1, le=5)
    horizon_days: Literal[1, 5, 20] = 5
    policy_version: str = Field(
        default="stock-discovery-v2",
        min_length=1,
        pattern=r"^[a-z0-9][a-z0-9._-]{0,63}$",
    )

    @field_validator("business_date")
    @classmethod
    def validate_business_date(cls, value: str | None) -> str | None:
        if value is None:
            return None
        parsed = date.fromisoformat(value)
        if parsed.isoformat() != value:
            raise ValueError("business_date must use YYYY-MM-DD")
        return value


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
    expected_constituent_count: int = Field(default=0, ge=0)
    resolved_constituent_count: int = Field(default=0, ge=0)
    constituent_source_family: str | None = None
    constituent_quality_status: Literal[
        "UNRESOLVED", "COMPLETE", "CACHED_COMPLETE", "SUPPLEMENTED_COMPLETE", "PARTIAL"
    ] = "UNRESOLVED"
    constituent_coverage: float = Field(default=0.0, ge=0, le=1)
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
    conclusion: Literal[
        "ROBUST",
        "CONDITIONALLY_EFFECTIVE",
        "NO_CLEAR_ADVANTAGE",
        "INSUFFICIENT_DATA",
    ]
    calibrated_probability: float = Field(ge=0, le=1)
    probability_lower_bound: float = Field(ge=0, le=1)
    brier_skill_score: float
    locked_accuracy: float = Field(ge=0, le=1)
    locked_log_loss: float = Field(ge=0)
    risk_adjusted_return: float
    max_drawdown: float
    stability_score: float = Field(ge=0, le=1)
    backtest_audit_status: Literal["PASS", "WARNING", "UNAVAILABLE"] | None = None
    backtest_entry_date_agreement_rate: float | None = Field(default=None, ge=0, le=1)
    backtest_return_delta: float | None = Field(default=None, ge=0)
    health_status: Literal["HEALTHY", "DEGRADED"]
    deep_score: float | None = None
    final_rank: int | None = None
    relative_score: float | None = None
    relative_rank: int | None = None
    research_tier: Literal["ACTIONABLE", "CONDITIONAL", "WATCH"] | None = None
    evidence: list[str] = Field(default_factory=list)
    risks: list[str] = Field(default_factory=list)
    forecast_report: dict[str, object] | None = None


class DiscoveryFunnel(BaseModel):
    raw_constituent_count: int = 0
    scope_excluded_count: int = 0
    star_market_excluded_count: int = 0
    beijing_market_excluded_count: int = 0
    unsupported_scope_excluded_count: int = 0
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
    quality_status: Literal[
        "FRESH_PRIMARY",
        "FRESH_FALLBACK",
        "PARTIAL_FRESH",
        "STALE_FALLBACK",
    ]
    retrieved_at: str
    data_fingerprint: str
    budget: float
    constituent_source_families: list[str] = Field(default_factory=list)
    constituent_quality_status: Literal[
        "COMPLETE", "MIXED_COMPLETE", "CACHED_COMPLETE", "PARTIAL"
    ] = "COMPLETE"
    sectors: list[DiscoverySector]
    candidates: list[DiscoveryCandidate]
    deep_evidence: list[DeepCandidateEvidence]
    relative_candidates: list[DeepCandidateEvidence] = Field(default_factory=list)
    final_candidates: list[DeepCandidateEvidence]
    funnel: DiscoveryFunnel
    warnings: list[str] = Field(default_factory=list)
    duration_ms: int = Field(default=0, ge=0)


class DiscoveryOutcomeObservation(BaseModel):
    model_config = ConfigDict(
        alias_generator=_to_camel,
        populate_by_name=True,
        extra="forbid",
        allow_inf_nan=False,
    )

    run_id: int = Field(gt=0)
    instrument_code: str = Field(pattern=r"^\d{6}\.(SH|SZ)$")
    as_of_date: str
    horizon_days: Literal[1, 5, 20] = 5
    admitted: bool
    final_rank: int | None = Field(default=None, ge=1, le=5)
    calibrated_probability: float | None = Field(default=None, ge=0, le=1)
    actual_net_return: float
    actual_direction: Literal["UP", "DOWN"]
    sector_names: list[str] = Field(default_factory=list)


class DiscoveryModelObservation(BaseModel):
    model_config = ConfigDict(
        alias_generator=_to_camel,
        populate_by_name=True,
        extra="forbid",
        allow_inf_nan=False,
    )

    run_id: int = Field(gt=0)
    instrument_code: str = Field(pattern=r"^\d{6}\.(SH|SZ)$")
    as_of_date: str
    horizon_days: Literal[1, 5, 20] = 5
    model_code: str = Field(min_length=1, max_length=64)
    model_name: str = Field(min_length=1, max_length=128)
    role: Literal["CHAMPION", "CHALLENGER", "BASELINE"]
    calibrated_probability: float = Field(ge=0, le=1)
    shadow_decision: Literal["UP", "DOWN", "ABSTAIN"]
    qualification_status: Literal[
        "QUALIFIED", "CONDITIONAL", "FAILED", "INSUFFICIENT_DATA"
    ]
    actual_direction: Literal["UP", "DOWN"]


class DiscoveryEvaluationRequest(BaseModel):
    model_config = ConfigDict(
        alias_generator=_to_camel,
        populate_by_name=True,
        extra="forbid",
        allow_inf_nan=False,
    )

    as_of_date: str | None = None
    pending_count: int = Field(default=0, ge=0)
    observations: list[DiscoveryOutcomeObservation] = Field(default_factory=list)
    model_observations: list[DiscoveryModelObservation] = Field(default_factory=list)


class DiscoveryProbabilityQuality(BaseModel):
    sample_count: int = 0
    brier_score: float | None = None
    brier_skill_score: float | None = None
    log_loss: float | None = None
    accuracy: float | None = None
    expected_calibration_error: float | None = None
    baseline_probability: float | None = None


class DiscoveryReliabilityBin(BaseModel):
    lower_bound: float
    upper_bound: float
    count: int
    mean_probability: float | None = None
    observed_up_rate: float | None = None
    calibration_error: float | None = None


class DiscoverySelectionMetric(BaseModel):
    limit: int
    matured_run_count: int
    sample_count: int
    hit_rate: float | None = None
    average_net_return: float | None = None
    median_net_return: float | None = None
    admitted_pool_average_return: float | None = None
    average_excess_vs_admitted_pool: float | None = None


class DiscoveryWindowMetric(BaseModel):
    window_days: int
    start_date: str | None = None
    matured_run_count: int = 0
    probability_sample_count: int = 0
    final_count: int = 0
    final_hit_rate: float | None = None
    final_average_net_return: float | None = None
    brier_skill_score: float | None = None


class DiscoverySectorPerformance(BaseModel):
    sector_name: str
    sample_count: int
    hit_rate: float
    average_net_return: float


class DiscoveryModelMetric(BaseModel):
    model_code: str
    model_name: str
    role: Literal["CHAMPION", "CHALLENGER", "BASELINE"]
    sample_count: int
    brier_score: float
    log_loss: float
    covered_count: int
    coverage: float
    covered_accuracy: float | None = None
    brier_delta_vs_champion: float
    log_loss_delta_vs_champion: float
    promotion_eligible: bool


class DiscoveryModelRace(BaseModel):
    status: Literal[
        "EVIDENCE_ACCUMULATING",
        "EVIDENCE_INCOMPLETE",
        "CHAMPION_LEADS",
        "NO_STABLE_EDGE",
        "PROMOTION_REVIEW",
    ] = "EVIDENCE_ACCUMULATING"
    sample_count: int = 0
    minimum_promotion_samples: int = 30
    champion_code: str | None = None
    promotion_candidate_code: str | None = None
    conclusion: str
    candidates: list[DiscoveryModelMetric] = Field(default_factory=list)


class DiscoveryRecentOutcome(BaseModel):
    run_id: int
    instrument_code: str
    as_of_date: str
    final_rank: int
    calibrated_probability: float | None = None
    actual_net_return: float
    actual_direction: Literal["UP", "DOWN"]
    sector_names: list[str] = Field(default_factory=list)


class DiscoveryRankingChallenger(BaseModel):
    status: Literal[
        "SHADOW_ACCUMULATING", "SHADOW_EVALUATING", "PROMOTION_REVIEW"
    ]
    training_date_count: int = Field(ge=0)
    calibration_date_count: int = Field(ge=0)
    locked_date_count: int = Field(ge=0)
    observation_count: int = Field(ge=0)
    pair_count: int = Field(ge=0)
    pairwise_accuracy: float | None = Field(default=None, ge=0, le=1)
    rank_ic: float | None = Field(default=None, ge=-1, le=1)
    top_k: int = Field(ge=1)
    top_k_average_return: float | None = None
    admitted_pool_average_return: float | None = None
    top_k_excess_return: float | None = None
    feature_weights: list[float] = Field(default_factory=list)
    method: str
    reason: str | None = None


class DiscoveryEvaluationReport(BaseModel):
    schema_version: str = "stock-discovery-evaluation-v1"
    as_of_date: str
    horizon_days: int = 5
    status: Literal["ACCUMULATING", "HEALTHY", "WATCH"]
    conclusion: str
    matured_run_count: int
    matured_candidate_count: int
    matured_final_count: int
    pending_count: int
    probability_quality: DiscoveryProbabilityQuality
    reliability_bins: list[DiscoveryReliabilityBin]
    selection_metrics: list[DiscoverySelectionMetric]
    windows: list[DiscoveryWindowMetric]
    sector_performance: list[DiscoverySectorPerformance]
    model_race: DiscoveryModelRace
    ranking_challenger: DiscoveryRankingChallenger
    recent_outcomes: list[DiscoveryRecentOutcome]
    warnings: list[str] = Field(default_factory=list)
