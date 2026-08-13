package com.finscope.domain.quant.discovery;

import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
public class StockDiscoveryRun {
    private Long id;
    private String runKey;
    private LocalDate businessDate;
    private String triggerType;
    private String status;
    private double budget;
    private String policyVersion;
    private String asOfDate;
    private String sourceFamily;
    private String qualityStatus;
    private String dataFingerprint;
    private int sectorCount;
    private int constituentCount;
    private int admittedCount;
    private int deepReviewCount;
    private int finalCount;
    private String reportJson;
    private String errorMessage;
    private LocalDateTime createdAt;
    private LocalDateTime startedAt;
    private LocalDateTime completedAt;
}
