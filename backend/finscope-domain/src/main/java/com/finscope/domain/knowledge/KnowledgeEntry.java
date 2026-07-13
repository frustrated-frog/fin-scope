package com.finscope.domain.knowledge;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class KnowledgeEntry {
    private Long id;
    private Long topicId;
    private Long learningTaskId;
    private String entryType;
    private String entryStatus;
    private String questionSnapshot;
    private String contentMarkdown;
    private String confidence;
    private long revision;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
