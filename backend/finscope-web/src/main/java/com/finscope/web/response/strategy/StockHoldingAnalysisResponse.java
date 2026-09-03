package com.finscope.web.response.strategy;

import com.finscope.domain.strategy.holding.StockHoldingAnalysis;
import lombok.Data;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Data
public class StockHoldingAnalysisResponse {
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
    private ForecastEvidenceResponse forecast;
    private List<PathPointResponse> series = new ArrayList<PathPointResponse>();

    public static StockHoldingAnalysisResponse of(StockHoldingAnalysis value) {
        StockHoldingAnalysisResponse response = new StockHoldingAnalysisResponse();
        response.instrumentCode = value.getInstrumentCode();
        response.instrumentName = value.getInstrumentName();
        response.entryDate = value.getEntryDate();
        response.firstObservedDate = value.getFirstObservedDate();
        response.asOfDate = value.getAsOfDate();
        response.holdingCalendarDays = value.getHoldingCalendarDays();
        response.observedTradingDays = value.getObservedTradingDays();
        response.costBasis = value.getCostBasis();
        response.latestPrice = value.getLatestPrice();
        response.quantity = value.getQuantity();
        response.totalCost = value.getTotalCost();
        response.marketValue = value.getMarketValue();
        response.unrealizedProfit = value.getUnrealizedProfit();
        response.holdingReturn = value.getHoldingReturn();
        response.maximumFavorableExcursion = value.getMaximumFavorableExcursion();
        response.maximumAdverseExcursion = value.getMaximumAdverseExcursion();
        response.maximumDrawdown = value.getMaximumDrawdown();
        response.maximumDrawdownDays = value.getMaximumDrawdownDays();
        response.annualizedVolatility = value.getAnnualizedVolatility();
        response.qualityStatus = value.getQualityStatus();
        response.sourceCode = value.getSourceCode();
        response.method = value.getMethod();
        response.warnings.addAll(value.getWarnings());
        if (value.getForecast() != null) {
            response.forecast = ForecastEvidenceResponse.of(value.getForecast());
        }
        for (StockHoldingAnalysis.PathPoint point : value.getSeries()) {
            response.series.add(PathPointResponse.of(point));
        }
        return response;
    }

    @Data
    public static class ForecastEvidenceResponse {
        private Long runId;
        private LocalDate asOfDate;
        private int horizonDays;
        private String status;
        private Double upProbability;
        private Double p10;
        private Double p50;
        private Double p90;
        private String modelVersion;

        private static ForecastEvidenceResponse of(StockHoldingAnalysis.ForecastEvidence value) {
            ForecastEvidenceResponse response = new ForecastEvidenceResponse();
            response.runId = value.getRunId();
            response.asOfDate = value.getAsOfDate();
            response.horizonDays = value.getHorizonDays();
            response.status = value.getStatus();
            response.upProbability = value.getUpProbability();
            response.p10 = value.getP10();
            response.p50 = value.getP50();
            response.p90 = value.getP90();
            response.modelVersion = value.getModelVersion();
            return response;
        }
    }

    @Data
    public static class PathPointResponse {
        private LocalDate tradeDate;
        private double close;
        private double returnSinceEntry;
        private double drawdown;

        private static PathPointResponse of(StockHoldingAnalysis.PathPoint value) {
            PathPointResponse response = new PathPointResponse();
            response.tradeDate = value.getTradeDate();
            response.close = value.getClose();
            response.returnSinceEntry = value.getReturnSinceEntry();
            response.drawdown = value.getDrawdown();
            return response;
        }
    }
}
