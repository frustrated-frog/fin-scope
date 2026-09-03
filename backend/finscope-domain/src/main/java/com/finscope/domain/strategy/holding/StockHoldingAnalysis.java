package com.finscope.domain.strategy.holding;

import lombok.Data;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Data
public class StockHoldingAnalysis {
    private String instrumentCode;
    private String instrumentName;
    private LocalDate entryDate;
    private LocalDate firstObservedDate;
    private LocalDate asOfDate;
    private int holdingCalendarDays;
    private int observedTradingDays;
    private double costBasis;
    private double latestPrice;
    private double quantity;
    private double totalCost;
    private double marketValue;
    private double unrealizedProfit;
    private double holdingReturn;
    private double maximumFavorableExcursion;
    private double maximumAdverseExcursion;
    private double maximumDrawdown;
    private int maximumDrawdownDays;
    private double annualizedVolatility;
    private String qualityStatus;
    private String sourceCode;
    private String method;
    private List<String> warnings = new ArrayList<String>();
    private ForecastEvidence forecast;
    private List<PathPoint> series = new ArrayList<PathPoint>();

    @Data
    public static class ForecastEvidence {
        private Long runId;
        private LocalDate asOfDate;
        private int horizonDays;
        private String status;
        private Double upProbability;
        private Double p10;
        private Double p50;
        private Double p90;
        private String modelVersion;
    }

    @Data
    public static class PathPoint {
        private LocalDate tradeDate;
        private double close;
        private double returnSinceEntry;
        private double drawdown;
    }
}
