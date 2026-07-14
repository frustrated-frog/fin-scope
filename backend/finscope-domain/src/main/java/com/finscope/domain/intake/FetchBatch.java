package com.finscope.domain.intake;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class FetchBatch {
    private Long id;
    private Long sourceId;
    private String sourceName;
    private String triggerType;
    private String status;
    private LocalDateTime startedAt;
    private LocalDateTime endedAt;
    private int lookbackDays = 3;
    private int maxItemsRequested = 10;
    private int rawItemCount;
    private int candidateCount;
    private int agentReviewedCount;
    private int duplicateCount;
    private int lowValueCount;
    private String errorMessage;
    private String batchSummaryJson;
    private String batchSummaryText;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

}
