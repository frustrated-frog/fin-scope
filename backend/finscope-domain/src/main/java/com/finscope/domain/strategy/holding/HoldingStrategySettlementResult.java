package com.finscope.domain.strategy.holding;

import lombok.Data;

@Data
public class HoldingStrategySettlementResult {
    private double strategyReturn;
    private double holdReturn;
    private double incrementalReturn;
    private String method;
}
