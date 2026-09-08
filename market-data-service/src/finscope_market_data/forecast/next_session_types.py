from typing import Literal
from pydantic import BaseModel, ConfigDict, Field
from pydantic.alias_generators import to_camel


class NextSessionJointEvidence(BaseModel):
    model_config = ConfigDict(alias_generator=to_camel, populate_by_name=True)
    model_version: str
    direction_evaluation: dict | None = None
    adaptation_evidence: dict | None = None
    training_universe_count: int | None = None
    display_universe_count: int | None = None
    industry_coverage: float | None = None
    return_target: Literal['ABSOLUTE', 'MARKET_RESIDUAL'] | None = None
    ranking_target: Literal['ABSOLUTE', 'MARKET_RESIDUAL'] | None = None
    selection_return_mse: float | None = None
    evidence_kind: Literal['RETROSPECTIVE', 'FORWARD_WINDOW'] | None = None
    selected_classifier: str
    applied: bool = False
    return_applied: bool = False
    classification_eligible: bool
    ranking_eligible: bool
    feature_count: int
    universe_count: int
    training_sample_count: int
    validation_sample_count: int
    validation_day_count: int
    test_start: str
    test_end: str
    selection_brier_score: float
    selection_rank_ic: float
    pooled_brier_score: float
    baseline_brier_score: float
    logistic_brier_score: float
    accuracy: float
    interval_coverage: float
    regression_mse: float
    baseline_regression_mse: float
    rank_ic: float
    top5_return: float
    top5_pool_excess: float
    top5_momentum_excess: float
    ranking_score: float
    ranking_percentile: float
    stock_validation_count: int
    stock_brier_score: float | None = None
    stock_baseline_brier_score: float | None = None
    up_probability: float = Field(ge=0, le=1)
    expected_return: float
    reason: str


class NextSessionPrediction(BaseModel):
    model_config = ConfigDict(alias_generator=to_camel, populate_by_name=True)
    status: Literal["READY", "WATCH", "INSUFFICIENT_DATA", "STALE_DATA", "CALENDAR_UNAVAILABLE", "BEFORE_CLOSE"]
    as_of_date: str
    target_date: str | None = None
    generated_at: str
    label: str = "NEXT_CLOSE_RETURN"
    last_close: float
    up_probability: float | None = Field(default=None, ge=0, le=1)
    expected_return: float | None = None
    lower_return: float | None = None
    upper_return: float | None = None
    decision: Literal["UP", "DOWN", "ABSTAIN"] = "ABSTAIN"
    model_code: str | None = None
    model_version: str = "next-session-rolling-v2"
    data_fingerprint: str
    training_through: str | None = None
    calibration_through: str | None = None
    training_sample_count: int = 0
    calibration_sample_count: int = 0
    validation_sample_count: int = 0
    accuracy: float | None = None
    brier_score: float | None = None
    baseline_brier_score: float | None = None
    interval_coverage: float | None = None
    direction_evaluation: dict | None = None
    joint_model: NextSessionJointEvidence | None = None
    warnings: list[str] = Field(default_factory=list)
