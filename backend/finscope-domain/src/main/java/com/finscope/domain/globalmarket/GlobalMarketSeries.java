package com.finscope.domain.globalmarket;

import java.util.SortedMap;

/** 带来源时间的报价和已结束交易日的当地日期、收盘价。 */
public record GlobalMarketSeries(String symbol, String source, String marketStatus, String asOf, double price,
                                double reference, SortedMap<String, Double> closes) {
}
