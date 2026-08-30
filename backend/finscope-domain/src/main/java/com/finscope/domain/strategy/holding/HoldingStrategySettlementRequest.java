package com.finscope.domain.strategy.holding;

import lombok.Data;

@Data
public class HoldingStrategySettlementRequest {
    private String action;
    private int suggestedQuantity;
    private double heldQuantity;
    private double currentMarketValue;
    private double entryPrice;
    private double actualNetReturn;
}
