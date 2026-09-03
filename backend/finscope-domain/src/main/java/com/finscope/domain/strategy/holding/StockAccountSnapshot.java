package com.finscope.domain.strategy.holding;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
public class StockAccountSnapshot {
    private BigDecimal cash = BigDecimal.ZERO;
    private BigDecimal marketValue = BigDecimal.ZERO;
    private BigDecimal totalEquity = BigDecimal.ZERO;
    private BigDecimal realizedProfit = BigDecimal.ZERO;
    private BigDecimal unrealizedProfit = BigDecimal.ZERO;
    private BigDecimal dividendIncome = BigDecimal.ZERO;
    private BigDecimal totalProfit = BigDecimal.ZERO;
    private BigDecimal concentration = BigDecimal.ZERO;
    private boolean cashTracked;
    private LocalDateTime calculatedAt;
    private List<StockPosition> positions = new ArrayList<StockPosition>();
}
