package com.finscope.domain.knowledge;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class KnowledgeReviewResult {
    private KnowledgeEntry entry;
    private LocalDateTime reviewedAt;
    private LocalDateTime nextReviewAt;
    private int intervalDays;
    private int reviewCount;
    private long revision;
}
