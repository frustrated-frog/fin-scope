package com.finscope.domain.marketpulse;

import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
public class MarketBreadthSnapshot {
    private LocalDate businessDate;
    private String sourceCode;
    private String sourceFamily;
    private String qualityStatus;
    private LocalDateTime retrievedAt;
    private Integer advanceCount;
    private Integer declineCount;
    private Integer flatCount;
    private Integer validCount;
    private Double advanceRatio;
    private Double totalAmount;
    private Integer limitUpCount;
    private Integer limitDownCount;
    private Double medianChangePct;
    private List<MarketReturnDistributionBucket> returnDistribution = new ArrayList<>();
    private MarketTrendBreadth trendBreadth;
    private MarketNewHighLow newHighLow;
    private Integer netAdvances;
    private Integer advanceDeclineLine;
    private List<MarketInternalHistoryPoint> history = new ArrayList<>();
    private MarketBreadthChangeSummary changeSummary;
    private List<MarketIndexPerformance> indices = new ArrayList<>();
    private String interpretation;
    private List<String> warnings = new ArrayList<>();
}
