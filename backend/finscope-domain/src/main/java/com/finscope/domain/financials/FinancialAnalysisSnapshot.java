package com.finscope.domain.financials;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class FinancialAnalysisSnapshot {
    private Long id;
    private Long reportId;
    private String algorithmVersion;
    private String sourceHash;
    private String inputHash;
    private String payloadJson;
    private String qualityLevel;
    private LocalDateTime createdAt;
}
