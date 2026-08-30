package com.finscope.domain.strategy.holding;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class StockPosition {
    private Long instrumentId;
    private String instrumentCode;
    private String instrumentName;
    private BigDecimal quantity = BigDecimal.ZERO;
    private BigDecimal totalCost = BigDecimal.ZERO;
    private BigDecimal averageCost = BigDecimal.ZERO;
    private BigDecimal realizedProfit = BigDecimal.ZERO;
    private BigDecimal dividendIncome = BigDecimal.ZERO;
    private BigDecimal lastPrice;
    private LocalDate quoteDate;
    private String quoteQuality;
    private BigDecimal marketValue;
    private BigDecimal unrealizedProfit;
    private BigDecimal totalProfit;
    private BigDecimal weight;
}
