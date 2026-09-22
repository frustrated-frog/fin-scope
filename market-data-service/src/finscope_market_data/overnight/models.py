from datetime import date, datetime
from typing import Literal

from pydantic import BaseModel, ConfigDict, Field, model_validator
from pydantic.alias_generators import to_camel


class OvernightRequest(BaseModel):
    model_config = ConfigDict(alias_generator=to_camel, populate_by_name=True, extra='forbid', allow_inf_nan=False)
    instrument_code: str = Field(pattern=r'^(?:(?:600|601|603|605)\d{3}\.SH|(?:000|001|002|003|300|301)\d{3}\.SZ)$')
    signal_date: date
    mode: Literal['TAIL_ENTRY', 'AFTER_CLOSE_HOLDING']
    cutoff: Literal['14:30', '14:45', '14:50', '15:00']
    cost_bps: float = Field(default=20, ge=0, le=200)
    cost_basis: float | None = Field(default=None, gt=0)
    quantity: float | None = Field(default=None, gt=0)
    position_opened_on: date | None = None

    @model_validator(mode='after')
    def check_mode(self):
        if self.mode == 'TAIL_ENTRY' and self.cutoff == '15:00':
            raise ValueError('尾盘入场必须使用收盘前时点')
        if self.mode == 'AFTER_CLOSE_HOLDING':
            if self.cutoff != '15:00' or not self.cost_basis or not self.quantity or not self.position_opened_on:
                raise ValueError('盘后研判需要真实持仓成本、数量、建仓日和收盘时点')
            if self.position_opened_on > self.signal_date:
                raise ValueError('不能为建仓之前的日期生成持仓判断')
        return self


class MinuteBar(BaseModel):
    model_config = ConfigDict(extra='forbid', allow_inf_nan=False)
    ended_at: datetime
    open: float = Field(gt=0)
    high: float = Field(gt=0)
    low: float = Field(gt=0)
    close: float = Field(gt=0)
    amount: float = Field(ge=0)

    @model_validator(mode='after')
    def price_bounds(self):
        if self.low > min(self.open, self.close) or self.high < max(self.open, self.close) or self.low > self.high:
            raise ValueError('分钟价格范围无效')
        if self.ended_at.tzinfo is not None:
            raise ValueError('分钟时间统一使用上海本地无时区时间')
        return self


class CapturePlan(BaseModel):
    model_config = ConfigDict(alias_generator=to_camel, populate_by_name=True, extra='forbid', allow_inf_nan=False)
    enabled: bool = False
    instrument_codes: list[str] = Field(default_factory=list, max_length=10)
    cost_bps: float = Field(default=20, ge=0, le=200)

    @model_validator(mode='after')
    def validate_codes(self):
        codes = []
        for value in self.instrument_codes:
            code = value.strip().upper()
            if len(code) == 6 and code.isdigit():
                code += '.SH' if code.startswith('6') else '.SZ'
            OvernightRequest(instrument_code=code, signal_date=date(2026, 1, 5),
                             mode='TAIL_ENTRY', cutoff='14:30')
            if code not in codes:
                codes.append(code)
        self.instrument_codes = codes
        if self.enabled and not codes:
            raise ValueError('启用自动留档前请设置观察名单')
        return self
