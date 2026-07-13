package com.finscope.domain.knowledge;

import lombok.Data;

import java.time.LocalDateTime;

/** Bounded read-model candidate consumed by the deterministic action planner. */
@Data
public class KnowledgeActionCandidate {
    private String type;
    private long stableId;
    private Long taskId;
    private Long topicId;
    private String title;
    private int priority;
    private LocalDateTime sortAt;
}
