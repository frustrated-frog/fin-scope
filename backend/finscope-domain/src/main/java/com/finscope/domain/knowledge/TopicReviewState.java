package com.finscope.domain.knowledge;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class TopicReviewState {
    private Long topicId;
    private LocalDateTime lastReviewedAt;
    private LocalDateTime nextReviewAt;
    private int intervalDays;
    private int reviewCount;
    private long revision;
}
