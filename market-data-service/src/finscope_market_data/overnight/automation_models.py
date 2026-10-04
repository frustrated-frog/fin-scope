from datetime import date

from pydantic import BaseModel, ConfigDict, Field
from pydantic.alias_generators import to_camel


class LedgerPosition(BaseModel):
    model_config = ConfigDict(alias_generator=to_camel, populate_by_name=True, extra='forbid', allow_inf_nan=False)
    instrument_code: str
    instrument_name: str | None = None
    quantity: float = Field(gt=0)
    average_cost: float = Field(ge=0)
    opened_on: date | None = None


class AutomationContext(BaseModel):
    model_config = ConfigDict(alias_generator=to_camel, populate_by_name=True, extra='forbid', allow_inf_nan=False)
    enabled: bool = True
    candidate_limit: int = Field(default=6, ge=1, le=10)
    tail_cost_bps: float = Field(default=20, ge=0, le=200)
    holding_cost_bps: float = Field(default=10, ge=0, le=200)
    positions: list[LedgerPosition] = Field(default_factory=list, max_length=200)
