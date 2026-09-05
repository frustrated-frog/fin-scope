from typing import Literal
from pydantic import BaseModel, ConfigDict, Field
from pydantic.alias_generators import to_camel


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
    model_version: str = "next-session-rolling-v1"
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
    warnings: list[str] = Field(default_factory=list)
