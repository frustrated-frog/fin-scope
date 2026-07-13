package com.finscope.domain.knowledge;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class KnowledgeProjectionJob {
    private Long id;
    private Long topicId;
    private Long entryId;
    private String status;
    private int attemptCount;
    private String lastError;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
