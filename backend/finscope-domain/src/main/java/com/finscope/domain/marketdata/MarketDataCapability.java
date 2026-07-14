package com.finscope.domain.marketdata;

/** 当前产品使用的外部市场数据能力。 */
public enum MarketDataCapability {
    REALTIME_STOCK_QUOTE,
    REALTIME_INDEX_QUOTE,
    REALTIME_FUND_ESTIMATE,
    REALTIME_SECTOR_QUOTE,
    SECTOR_CATALOG,
    CAPITAL_FLOW_5M
}
