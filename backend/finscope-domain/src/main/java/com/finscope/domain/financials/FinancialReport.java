package com.finscope.domain.financials;

import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
public class FinancialReport {
    private Long id;
    private Long instrumentId;
    private LocalDate periodEnd;
    private FinancialReportType reportType;
    private String scope;
    private String currency;
    private LocalDateTime publishedAt;
    private Boolean audited;
    private FinancialQualityStatus qualityStatus;
    private String sourceCode;
    private String warningMessage;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
