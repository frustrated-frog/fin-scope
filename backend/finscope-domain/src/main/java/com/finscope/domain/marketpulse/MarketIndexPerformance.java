package com.finscope.domain.marketpulse;

import lombok.Data;

import java.time.LocalDate;

@Data
public class MarketIndexPerformance {
    private String code;
    private String name;
    private LocalDate businessDate;
    private Double close;
    private java.util.List<MarketIndexHistoryPoint> history = new java.util.ArrayList<>();
    private Double return1d;
    private Double return5d;
    private Double return20d;
    private String sourceCode;
    private String qualityStatus;
}
