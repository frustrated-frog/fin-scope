package com.finscope.domain.marketpulse;

import lombok.Data;
import java.time.LocalDate;

/** 指数收盘价序列，供同基日起点的市场趋势比较使用。 */
@Data
public class MarketIndexHistoryPoint {
    private LocalDate businessDate;
    private Double close;
}
