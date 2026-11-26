package com.finscope.domain.globalmarket;

import java.util.SortedMap;

/** UTC ISO 时间与来源价格，范围由各市场的交易时段决定。 */
public record IntradaySeries(String source, String window, SortedMap<String, Double> points) {
}
