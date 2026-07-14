package com.finscope.domain.marketintel;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class MarketIntelRefreshRun {
    public enum Status {
        PENDING, RUNNING, SUCCEEDED, PARTIAL, FAILED;
        public boolean isTerminal() { return this == SUCCEEDED || this == PARTIAL || this == FAILED; }
    }
    private Long id;
    private Long instrumentId;
    private String triggerType;
    private Status status;
    private int successCount;
    private int failureCount;
    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;
}
