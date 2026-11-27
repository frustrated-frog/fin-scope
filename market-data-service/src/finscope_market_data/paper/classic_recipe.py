"""Configuration and versioned executable source for classic stock strategies."""
import inspect
from datetime import date
from typing import Literal
from uuid import UUID
from pydantic import BaseModel, ConfigDict, Field, model_validator
from . import classic_components


class Recipe(BaseModel):
    model_config = ConfigDict(extra="forbid", allow_inf_nan=False)
    family: Literal["TREND", "MOMENTUM", "REVERSION", "MULTI"]
    base: Literal["DISCOVERY", "COHORT", "GEM", "SH_MAIN"] = "COHORT"
    period: Literal["5m", "15m", "1d"] = "15m"
    window: int = Field(default=6, ge=3, le=20)
    holdings: int = Field(default=3, ge=1, le=10)
    poolSize: int = Field(default=12, ge=1, le=24)
    minTurnover: float = Field(default=0, ge=0, le=1e12)
    allocation: Literal["EQUAL", "INVERSE_VOL", "SCORE"] = "EQUAL"
    exposure: float = Field(default=.95, gt=0, le=1)
    maxWeight: float = Field(default=.4, gt=0, le=1)
    rebalance: Literal["DAILY", "WEEKLY", "MONTHLY"] = "DAILY"
    maxFeeBps: float = Field(default=0, ge=0, le=10000)

    @model_validator(mode="after")
    def coherent(self):
        if self.holdings > self.poolSize:
            raise ValueError("持股数不能超过候选池容量")
        if self.period == "15m" and self.window > 12:
            raise ValueError("十五分钟信号窗口支持 3 至 12 根")
        return self


class ClassicRequest(BaseModel):
    model_config = ConfigDict(extra="forbid")
    researchMode: Literal["CLASSIC"]
    recipe: Recipe
    capital: float = Field(ge=.01, le=1e10, allow_inf_nan=False)
    reference: int | None = Field(default=None, gt=0)
    startDate: date | None = None
    endDate: date | None = None
    autoStart: bool = False
    requestId: UUID | None = None

    @model_validator(mode="after")
    def validate_range(self):
        if (self.startDate is None) != (self.endDate is None):
            raise ValueError("自定义区间须同时填写开始日和结束日")
        if self.startDate and self.startDate > self.endDate:
            raise ValueError("开始日不能晚于结束日")
        return self


FAMILIES = {
    "TREND": ("双均线趋势跟踪", "短均线高于长均线时，按两条均线的相对距离选股。"),
    "MOMENTUM": ("截面动量", "按同一观察期的价格涨幅排序，选择相对领先的股票。"),
    "REVERSION": ("均值回归", "在价格低于移动均值的股票中，优先选择向下偏离标准差倍数较高的股票。"),
    "MULTI": ("动量与低波多因子", "分别计算动量和低波动率的截面排名，等权合成选股得分。"),
}


def program(recipe):
    from .research import Program
    name, thesis = FAMILIES[recipe.family]
    source = inspect.getsource(classic_components)
    return Program(name=name, hypothesis=thesis, factor=thesis,
                   code='RECIPE = ' + repr(recipe.model_dump()) + '\n' + source)
