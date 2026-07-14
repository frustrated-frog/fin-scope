package com.finscope.domain.knowledge;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class KnowledgeProjectionJob {
    /**
     * 主键 ID。
     */
    private Long id;
    /**
     * 主题 ID。
     */
    private Long topicId;
    /**
     * 知识条目 ID。
     */
    private Long entryId;
    /**
     * 当前状态。
     */
    private String status;
    /**
     * 尝试次数。
     */
    private int attemptCount;
    /**
     * 最近一次错误。
     */
    private String lastError;
    /**
     * 创建时间。
     */
    private LocalDateTime createdAt;
    /**
     * 最近更新时间。
     */
    private LocalDateTime updatedAt;
}
