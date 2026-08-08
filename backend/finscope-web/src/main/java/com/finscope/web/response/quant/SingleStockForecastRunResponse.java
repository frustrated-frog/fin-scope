package com.finscope.web.response.quant;

import com.finscope.domain.quant.forecast.SingleStockForecast;
import com.finscope.domain.quant.forecast.SingleStockForecastRun;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
public class SingleStockForecastRunResponse {
    private Long id;
    private String instrumentCode;
    private LocalDate asOfDate;
    private String status;
    private Double upProbability;
    private String dataFingerprint;
    private String modelVersion;
    private String reportSchemaVersion;
    private boolean sameDataAsPrevious;
    private LocalDateTime createdAt;
    private SingleStockForecast report;
    private SingleStockForecastRun.HoldingSnapshot holdingSnapshot;

    public static SingleStockForecastRunResponse of(SingleStockForecastRun value) {
        SingleStockForecastRunResponse response = new SingleStockForecastRunResponse();
        response.id = value.getId();
        response.instrumentCode = value.getInstrumentCode();
        response.asOfDate = value.getAsOfDate();
        response.status = value.getStatus();
        response.upProbability = value.getUpProbability();
        response.dataFingerprint = value.getDataFingerprint();
        response.modelVersion = value.getModelVersion();
        response.reportSchemaVersion = value.getReportSchemaVersion();
        response.sameDataAsPrevious = value.isSameDataAsPrevious();
        response.createdAt = value.getCreatedAt();
        response.report = value.getReport();
        response.holdingSnapshot = value.getHoldingSnapshot();
        return response;
    }
}
