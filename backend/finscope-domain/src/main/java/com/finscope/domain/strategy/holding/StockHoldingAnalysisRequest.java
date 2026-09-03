package com.finscope.domain.strategy.holding;

import lombok.Data;

import java.time.LocalDate;

@Data
public class StockHoldingAnalysisRequest {
    private String instrumentCode;
    private LocalDate entryDate;
    private double costBasis;
    private double quantity;
    private double marketPrice;
}
