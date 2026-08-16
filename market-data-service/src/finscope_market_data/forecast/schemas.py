from __future__ import annotations

from typing import Literal

from pydantic import BaseModel, ConfigDict, Field


def _to_camel(value: str) -> str:
    head, *tail = value.split("_")
    return head + "".join(part.capitalize() for part in tail)


class ForecastModel(BaseModel):
    model_config = ConfigDict(alias_generator=_to_camel, populate_by_name=True)


class SingleStockForecastRequest(ForecastModel):
    code: str = Field(pattern=r"^\d{6}$")
    horizon_days: Literal[1, 5, 20] = 5


class ForecastValidation(ForecastModel):
    out_of_sample_count: int
    independent_sample_count: int
    accuracy: float
    brier_score: float
    baseline_brier_score: float
    observed_up_rate: float


class EvaluationSlice(ForecastModel):
    sample_count: int
    accuracy: float
    brier_score: float
    baseline_brier_score: float | None = None
    evidence_role: str


class StrategyPolicy(ForecastModel):
    signal_threshold: float
    holding_days: int
    entry_rule: str
    exit_rule: str
    overlap_policy: str
    round_trip_cost_rate: float
    benchmark: str


class FactorExplanation(ForecastModel):
    code: str
    name: str
    category: str
    formula: str
    window: str
    current_value: float
    historical_percentile: float
    standardized_value: float
    coefficient: float
    contribution: float
    direction: str
    economic_meaning: str
    boundary: str


class PerformanceSummary(ForecastModel):
    total_return: float
    annualized_return: float
    annualized_volatility: float
    sharpe_ratio: float
    daily_win_rate: float
    max_drawdown: float
    max_drawdown_start_date: str
    max_drawdown_trough_date: str
    max_drawdown_recovery_date: str | None = None
    max_drawdown_duration_days: int


class TradeSummary(ForecastModel):
    signal_date: str
    entry_date: str
    exit_date: str
    probability: float
    net_return: float
    cost: float
    holding_days: int


class PerformanceReport(ForecastModel):
    benchmark_label: str
    strategy: PerformanceSummary
    benchmark: PerformanceSummary
    excess_return: float
    trade_count: int
    profitable_trade_rate: float
    turnover: float
    total_cost: float
    holding_time_ratio: float
    average_holding_days: float
    trades: list[TradeSummary] = Field(default_factory=list)


class EquityPoint(ForecastModel):
    trade_date: str
    strategy_nav: float
    benchmark_nav: float
    drawdown: float
    invested: bool


class AuditEngineMetrics(ForecastModel):
    engine: str
    trade_count: int = Field(ge=0)
    total_return: float
    max_drawdown: float = Field(ge=0)
    sharpe_ratio: float
    total_cost: float = Field(ge=0)


class AuditMismatch(ForecastModel):
    category: Literal[
        "TRADE_COUNT", "ENTRY_DATE", "EXIT_DATE", "RETURN", "COST",
        "MAX_DRAWDOWN", "SHARPE",
    ]
    trade_index: int | None = Field(default=None, ge=1)
    primary_value: str | float | int | None = None
    shadow_value: str | float | int | None = None
    detail: str


class BacktestAudit(ForecastModel):
    status: Literal["PASS", "WARNING", "UNAVAILABLE"]
    mode: Literal["SHADOW"] = "SHADOW"
    primary_engine: AuditEngineMetrics
    shadow_engine: AuditEngineMetrics | None = None
    trade_count_agreement: bool
    entry_date_agreement_rate: float = Field(ge=0, le=1)
    exit_date_agreement_rate: float = Field(ge=0, le=1)
    return_delta: float = Field(ge=0)
    max_drawdown_delta: float = Field(ge=0)
    sharpe_delta: float = Field(ge=0)
    cost_delta: float = Field(ge=0)
    duration_ms: int = Field(ge=0)
    mismatches: list[AuditMismatch] = Field(default_factory=list)
    limitations: list[str] = Field(default_factory=list)


class AnnualPerformance(ForecastModel):
    year: int
    strategy_return: float
    benchmark_return: float
    excess_return: float
    max_drawdown: float
    trade_count: int


class RegimePerformance(ForecastModel):
    regime: str
    label: str
    sample_days: int
    strategy_return: float
    benchmark_return: float
    excess_return: float
    sharpe_ratio: float
    max_drawdown: float
    trade_count: int
    holding_time_ratio: float


class StabilityScenario(ForecastModel):
    holding_days: int
    threshold: float
    primary: bool
    annualized_return: float
    excess_return: float
    sharpe_ratio: float
    max_drawdown: float
    trade_count: int


class ParameterStability(ForecastModel):
    scenarios: list[StabilityScenario] = Field(default_factory=list)
    positive_excess_ratio: float
    worst_excess_return: float
    worst_sharpe_ratio: float
    neighbor_mean_excess_return: float
    neighbor_median_excess_return: float
    outperform_benchmark_ratio: float = Field(ge=0, le=1)
    surface_variance: float = Field(ge=0)
    robust_region_size: int = Field(ge=0)
    scenario_count: int = Field(ge=1)


class ForecastObservation(ForecastModel):
    signal_date: str
    probability: float = Field(ge=0, le=1)
    actual_net_return: float
    correct: bool


class ConfidenceInterval(ForecastModel):
    status: str
    lower: float | None = None
    upper: float | None = None
    confidence_level: float = 0.95
    method: str
    valid_iterations: int
    reason: str | None = None
    limitation: str | None = None


class SplitSliceAudit(ForecastModel):
    start_date: str
    end_date: str
    sample_count: int
    independent_sample_count: int
    positive_count: int
    purged_count: int


class QualificationSplitAudit(ForecastModel):
    development: SplitSliceAudit
    calibration: SplitSliceAudit
    locked_test: SplitSliceAudit
    label_horizon_days: int = 20
    independent_stride_days: int = 20
    rule: str


class ProbabilityMetricSet(ForecastModel):
    sample_count: int
    accuracy: float
    brier_score: float
    baseline_brier_score: float
    brier_skill_score: float
    log_loss: float
    expected_calibration_error: float


class ReliabilityBin(ForecastModel):
    lower_bound: float
    upper_bound: float
    count: int
    mean_probability: float | None = None
    observed_up_rate: float | None = None
    calibration_error: float | None = None


class CalibrationReport(ForecastModel):
    status: str
    method: str = "PLATT"
    sample_count: int
    positive_count: int
    slope: float
    intercept: float
    raw_log_loss: float
    calibrated_log_loss: float
    reason: str | None = None


class LockedTestReport(ForecastModel):
    baseline_probability: float
    raw_metrics: ProbabilityMetricSet
    calibrated_metrics: ProbabilityMetricSet
    baseline_metrics: ProbabilityMetricSet
    reliability_bins: list[ReliabilityBin]


class SelectiveValidation(ForecastModel):
    lower_threshold: float
    upper_threshold: float
    sample_count: int
    covered_count: int
    coverage: float
    covered_accuracy: float
    abstain_rate: float


class ContextSource(ForecastModel):
    code: str | None = None
    label: str
    status: str
    coverage: float
    regime: str | None = None
    reason: str | None = None


class ForecastContextReport(ForecastModel):
    market: ContextSource
    industry: ContextSource
    feature_codes: list[str] = Field(default_factory=list)
    alignment_rule: str


class ModelCandidate(ForecastModel):
    code: str
    name: str
    selected: bool
    selection_sample_count: int
    accuracy: float
    brier_score: float
    log_loss: float
    baseline_brier_score: float
    validation_fold_count: int = 1
    brier_std: float = 0.0
    role: Literal["CHAMPION", "CHALLENGER", "BASELINE"] = "CHALLENGER"
    model_version: str = "competition-pending-v6"
    raw_probability: float = Field(default=0.5, ge=0, le=1)
    calibrated_probability: float = Field(default=0.5, ge=0, le=1)
    shadow_decision: Literal["UP", "DOWN", "ABSTAIN"] = "ABSTAIN"
    qualification_status: Literal[
        "QUALIFIED", "CONDITIONAL", "FAILED", "INSUFFICIENT_DATA"
    ] = "INSUFFICIENT_DATA"
    locked_metrics: ProbabilityMetricSet | None = None
    reason: str


class ModelCompetitionReport(ForecastModel):
    selected_model: str
    selection_end_date: str
    calibration_start_date: str
    selection_rule: str
    candidates: list[ModelCandidate] = Field(default_factory=list)


class LeakageAudit(ForecastModel):
    status: str
    checked_sample_count: int
    checks: list[str] = Field(default_factory=list)


class QlibReference(ForecastModel):
    status: str = "NOT_RUN"
    role: str = "可选离线对照实验，不参与线上预测"
    runtime_dependency: bool = False


class PanelModelReport(ForecastModel):
    status: Literal["NOT_AVAILABLE", "SHADOW", "BLENDED"] = "NOT_AVAILABLE"
    mode: Literal["UNAVAILABLE", "PANEL_CORE", "PANEL_FULL"] = "UNAVAILABLE"
    artifact_version: str | None = None
    published_at: str | None = None
    artifact_age_days: int | None = None
    universe_size: int = 0
    sample_count: int = 0
    feature_coverage: float = Field(default=0.0, ge=0, le=1)
    feature_distance: float | None = None
    drift_status: Literal["UNAVAILABLE", "HEALTHY", "WATCH", "REJECTED"] = "UNAVAILABLE"
    individual_probability: float | None = Field(default=None, ge=0, le=1)
    panel_probability: float | None = Field(default=None, ge=0, le=1)
    final_probability: float | None = Field(default=None, ge=0, le=1)
    blend_weight: float = Field(default=0.0, ge=0, le=0.45)
    target_locked_sample_count: int = 0
    locked_brier_delta: float | None = None
    locked_log_loss_delta: float | None = None
    panel_brier_score: float | None = None
    panel_log_loss: float | None = None
    panel_ece: float | None = None
    fallback_reason: str | None = None
    evidence: list[str] = Field(default_factory=list)


class QualificationIntervals(ForecastModel):
    brier_skill_score: ConfidenceInterval
    accuracy: ConfidenceInterval
    excess_return: ConfidenceInterval
    sharpe_ratio: ConfidenceInterval


class TrialIdentity(ForecastModel):
    trial_id: str
    feature_version: str
    label_version: str
    split_version: str
    calibration_version: str
    bootstrap_version: str
    random_seed: int
    model_version: str


class ModelQualification(ForecastModel):
    status: str
    reason: str | None = None
    trial: TrialIdentity
    split_audit: QualificationSplitAudit
    calibration: CalibrationReport
    locked_test: LockedTestReport
    confidence_intervals: QualificationIntervals


class SingleStockForecastResult(ForecastModel):
    report_schema_version: str = "single-stock-research-v7"
    model_version: str = "competition-pending-v5"
    instrument_code: str
    as_of_date: str
    horizon_days: int = 5
    status: str
    conclusion: str
    decision: Literal["UP", "DOWN", "ABSTAIN"] = "ABSTAIN"
    decision_reason: str
    bar_count: int
    labeled_sample_count: int | None = None
    up_probability: float | None = Field(default=None, ge=0, le=1)
    raw_probability: float | None = Field(default=None, ge=0, le=1)
    probability_interval: ConfidenceInterval | None = None
    expected_net_return: float | None = None
    lower_net_return: float | None = None
    upper_net_return: float | None = None
    data_fingerprint: str
    source_code: str
    source_family: str
    quality_status: str
    last_close: float
    strategy_policy: StrategyPolicy
    validation: ForecastValidation | None = None
    factor_explanations: list[FactorExplanation] = Field(default_factory=list)
    performance: PerformanceReport | None = None
    equity_curve: list[EquityPoint] = Field(default_factory=list)
    annual_performance: list[AnnualPerformance] = Field(default_factory=list)
    regime_performance: list[RegimePerformance] = Field(default_factory=list)
    in_sample: EvaluationSlice | None = None
    out_of_sample: EvaluationSlice | None = None
    parameter_stability: ParameterStability | None = None
    backtest_audit: BacktestAudit | None = None
    recent_observations: list[ForecastObservation] = Field(default_factory=list)
    qualification: ModelQualification | None = None
    selective_validation: SelectiveValidation | None = None
    context: ForecastContextReport | None = None
    model_competition: ModelCompetitionReport | None = None
    leakage_audit: LeakageAudit | None = None
    qlib_reference: QlibReference = Field(default_factory=QlibReference)
    panel_model: PanelModelReport = Field(default_factory=PanelModelReport)
    warnings: list[str] = Field(default_factory=list)
