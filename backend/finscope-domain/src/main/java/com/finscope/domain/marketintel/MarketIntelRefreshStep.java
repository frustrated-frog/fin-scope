package com.finscope.domain.marketintel;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class MarketIntelRefreshStep {
    public enum Status {
        PENDING, RUNNING, SUCCEEDED, EMPTY, FAILED, SKIPPED;
        public boolean isTerminal() { return this == SUCCEEDED || this == EMPTY || this == FAILED || this == SKIPPED; }
    }
    private Long id;
    private Long runId;
    private String dimension;
    private String providerCode;
    private int attempt;
    private Status status;
    private boolean fallbackUsed;
    private String errorType;
    private String errorMessage;
    private int outputCount;
    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;
}
