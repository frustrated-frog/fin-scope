package com.finscope.common.enums.marketpulse;

/** 市场流动性相对指标采用的基础量，防止将成交量代理解释为真实成交额。 */
public enum MarketLiquidityBasis {
    AMOUNT,
    VOLUME_PROXY
}
