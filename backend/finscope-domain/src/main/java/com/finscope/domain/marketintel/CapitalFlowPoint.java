package com.finscope.domain.marketintel;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
public class CapitalFlowPoint {
    private Long id;
    private Long instrumentId;
    private String providerCode;
    private String granularity;
    private LocalDate dataDate;
    private LocalDateTime observedAt;
    private BigDecimal price;
    private BigDecimal tradeVolume;
    private BigDecimal intervalTradeAmount;
    private BigDecimal cumulativeTradeAmount;
    private BigDecimal turnoverRate;
    private BigDecimal volumeRatio;
    private BigDecimal mainInflow;
    private BigDecimal mainOutflow;
    private BigDecimal mainNetInflow;
    private BigDecimal superLargeNetInflow;
    private BigDecimal largeNetInflow;
    private BigDecimal mediumNetInflow;
    private BigDecimal smallNetInflow;
    private String calculationVersion;
    private LocalDateTime retrievedAt;
    private String payloadHash;
    private String qualityStatus;

    public String metricRef(String metric) {
        if (id == null) {
            throw new IllegalStateException("metric reference requires a persisted flow point");
        }
        return "flow:" + id + ":" + metric;
    }
}
