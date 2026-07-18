package com.finscope.domain.financials;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
public class BrokerResearchReport {
    private Long id;
    private Long instrumentId;
    private Long linkedFinancialReportId;
    private String title;
    private String institution;
    private String analyst;
    private LocalDate publishedDate;
    private String reportType;
    private String rating;
    private BigDecimal targetPrice;
    private String targetPriceCurrency;
    private String sourceType;
    private String sourceUrl;
    private String originalFileName;
    private String relativePath;
    private Long fileSize;
    private String fileHash;
    private Integer pageCount;
    private String parseStatus;
    private String analysisStatus;
    private String qualityLevel;
    private String extractedText;
    private String analysisJson;
    private String errorMessage;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
