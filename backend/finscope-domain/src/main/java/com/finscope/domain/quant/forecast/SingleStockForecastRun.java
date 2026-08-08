package com.finscope.domain.quant.forecast;

import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
public class SingleStockForecastRun {
    private Long id;
    private String instrumentCode;
    private LocalDate asOfDate;
    private String status;
    private Double upProbability;
    private String dataFingerprint;
    private String modelVersion;
    private String reportSchemaVersion;
    private boolean sameDataAsPrevious;
    private String reportJson;
    private String holdingSnapshotJson;
    private LocalDateTime createdAt;
    private SingleStockForecast report;
    private HoldingSnapshot holdingSnapshot;

    @Data
    public static class HoldingSnapshot {
        private boolean held;
        private String instrumentCode;
        private String instrumentName;
        private String role;
        private Double targetWeight;
        private Double currentWeight;
        private Double quantity;
        private Double averageCost;
        private Double lastClose;
        private Double estimatedMarketValue;
        private Double unrealizedReturn;
        private String note;
        private String interpretation;
    }
}
