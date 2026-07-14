package com.finscope.domain.marketdata;

/**
 * 一次市场数据刷新对调用方暴露的质量状态。
 * 状态按数据可用性从高到低排列，但调用方不应依赖枚举顺序比较严重度。
 */
public enum MarketDataQualityStatus {
    FRESH_PRIMARY,
    FRESH_FALLBACK,
    PARTIAL_FRESH,
    STALE_FALLBACK,
    UNAVAILABLE
}
