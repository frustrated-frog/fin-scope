from __future__ import annotations

from pydantic import BaseModel, ConfigDict, Field


def _to_camel(value: str) -> str:
    head, *tail = value.split("_")
    return head + "".join(part.capitalize() for part in tail)


class ForecastModel(BaseModel):
    model_config = ConfigDict(alias_generator=_to_camel, populate_by_name=True)


class SingleStockForecastRequest(ForecastModel):
    code: str = Field(pattern=r"^\d{6}$")


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


class ForecastObservation(ForecastModel):
    signal_date: str
    probability: float = Field(ge=0, le=1)
    actual_net_return: float
    correct: bool


class SingleStockForecastResult(ForecastModel):
    report_schema_version: str = "single-stock-research-v2"
    model_version: str = "logistic-walk-forward-v2"
    instrument_code: str
    as_of_date: str
    horizon_days: int = 20
    status: str
    conclusion: str
    bar_count: int
    labeled_sample_count: int | None = None
    up_probability: float | None = Field(default=None, ge=0, le=1)
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
    recent_observations: list[ForecastObservation] = Field(default_factory=list)
    warnings: list[str] = Field(default_factory=list)
