package com.finscope.domain.knowledge;

import lombok.Data;

import java.time.LocalDateTime;

/** Bounded read-model candidate consumed by the deterministic action planner. */
@Data
public class KnowledgeActionCandidate {
    /**
     * 类型。
     */
    private String type;
    /**
     * 稳定 ID。
     */
    private long stableId;
    /**
     * 任务 ID。
     */
    private Long taskId;
    /**
     * 主题 ID。
     */
    private Long topicId;
    /**
     * 标题。
     */
    private String title;
    /**
     * 优先级。
     */
    private int priority;
    /**
     * 排序时间。
     */
    private LocalDateTime sortAt;
}
