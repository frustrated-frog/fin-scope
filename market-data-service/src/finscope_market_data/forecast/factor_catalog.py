from __future__ import annotations

from dataclasses import dataclass


@dataclass(frozen=True)
class FactorDefinition:
    code: str
    name: str
    category: str
    formula: str
    window: str
    economic_meaning: str
    boundary: str


FACTORS: tuple[FactorDefinition, ...] = (
    FactorDefinition(
        "MOMENTUM_5",
        "5 日动量",
        "趋势",
        "收盘价 / 5 个交易日前收盘价 - 1",
        "5 个交易日",
        "观察很短周期内价格是否延续原有方向。",
        "容易受事件跳空和短期情绪影响，不代表趋势一定延续。",
    ),
    FactorDefinition(
        "MOMENTUM_20",
        "20 日动量",
        "趋势",
        "收盘价 / 20 个交易日前收盘价 - 1",
        "20 个交易日",
        "近似观察一个月价格趋势的强弱。",
        "震荡行情中可能频繁反转，强动量也可能意味着拥挤。",
    ),
    FactorDefinition(
        "MOMENTUM_60",
        "60 日动量",
        "趋势",
        "收盘价 / 60 个交易日前收盘价 - 1",
        "60 个交易日",
        "观察中期趋势是否为当前信号提供背景支持。",
        "对基本面突变反应较慢，不能单独区分趋势与估值透支。",
    ),
    FactorDefinition(
        "PRICE_VS_MA20",
        "价格相对 20 日均线",
        "位置",
        "收盘价 / 20 日平均收盘价 - 1",
        "20 个交易日",
        "衡量当前价格相对短期成本中枢的位置。",
        "远离均线既可能是趋势，也可能是均值回归风险。",
    ),
    FactorDefinition(
        "PRICE_VS_MA60",
        "价格相对 60 日均线",
        "位置",
        "收盘价 / 60 日平均收盘价 - 1",
        "60 个交易日",
        "衡量价格相对中期成本中枢的偏离。",
        "均线是价格的滞后摘要，不提供基本面因果解释。",
    ),
    FactorDefinition(
        "VOLATILITY_20",
        "20 日年化波动率",
        "风险",
        "20 日日对数收益标准差 × √252",
        "20 个交易日",
        "描述近期价格路径的不确定程度。",
        "高波动不等于必跌，低波动也不保证未来风险较小。",
    ),
    FactorDefinition(
        "AMOUNT_RATIO_20_60",
        "短中期成交额比",
        "活跃度",
        "20 日平均成交额 / 60 日平均成交额 - 1",
        "20 / 60 个交易日",
        "观察近期交易活跃度是否相对中期水平扩张。",
        "放量既可能来自资金确认，也可能来自分歧和派发。",
    ),
)
