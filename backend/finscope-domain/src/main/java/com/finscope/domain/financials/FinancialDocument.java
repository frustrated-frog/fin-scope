package com.finscope.domain.financials;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class FinancialDocument {
    private Long id;
    private Long instrumentId;
    private Long reportId;
    private String originalFileName;
    private String relativePath;
    private String mimeType;
    private Long fileSize;
    private String fileHash;
    private Integer pageCount;
    private String parseStatus;
    private String extractedText;
    private String errorMessage;
    private LocalDateTime createdAt;
}
