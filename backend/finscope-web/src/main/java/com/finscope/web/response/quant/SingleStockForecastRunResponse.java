package com.finscope.web.response.quant;

import com.finscope.domain.quant.forecast.ForecastModelHealth;
import com.finscope.domain.quant.forecast.ForecastModelRace;
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
    private int horizonDays;
    private String status;
    private Double upProbability;
    private String dataFingerprint;
    private String modelVersion;
    private String reportSchemaVersion;
    private boolean sameDataAsPrevious;
    private SingleStockForecastRun.MaturityStatus maturityStatus;
    private LocalDateTime createdAt;
    private SingleStockForecast report;
    private SingleStockForecastRun.HoldingSnapshot holdingSnapshot;
    private SingleStockForecastRun.ForecastOutcome outcome;
    private ForecastModelHealth modelHealth;
    private ForecastModelRace modelRace;

    public static SingleStockForecastRunResponse of(SingleStockForecastRun value) {
        SingleStockForecastRunResponse response = new SingleStockForecastRunResponse();
        response.id = value.getId();
        response.instrumentCode = value.getInstrumentCode();
        response.asOfDate = value.getAsOfDate();
        response.horizonDays = value.getHorizonDays();
        response.status = value.getStatus();
        response.upProbability = value.getUpProbability();
        response.dataFingerprint = value.getDataFingerprint();
        response.modelVersion = value.getModelVersion();
        response.reportSchemaVersion = value.getReportSchemaVersion();
        response.sameDataAsPrevious = value.isSameDataAsPrevious();
        response.maturityStatus = value.getMaturityStatus();
        response.createdAt = value.getCreatedAt();
        response.report = value.getReport();
        response.holdingSnapshot = value.getHoldingSnapshot();
        response.outcome = value.getOutcome();
        response.modelHealth = value.getModelHealth();
        response.modelRace = value.getModelRace();
        return response;
    }
}
