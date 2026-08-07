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


class ForecastObservation(ForecastModel):
    signal_date: str
    probability: float = Field(ge=0, le=1)
    actual_net_return: float
    correct: bool


class SingleStockForecastResult(ForecastModel):
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
    validation: ForecastValidation | None = None
    recent_observations: list[ForecastObservation] = Field(default_factory=list)
    warnings: list[str] = Field(default_factory=list)
