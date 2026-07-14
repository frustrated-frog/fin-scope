package com.finscope.domain.knowledge;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class KnowledgeEntry {
    /**
     * 主键 ID。
     */
    private Long id;
    /**
     * 主题 ID。
     */
    private Long topicId;
    /**
     * 学习任务 ID。
     */
    private Long learningTaskId;
    /**
     * 知识条目类型。
     */
    private String entryType;
    /**
     * 知识条目状态。
     */
    private String entryStatus;
    /**
     * 问题快照。
     */
    private String questionSnapshot;
    /**
     * 内容 Markdown。
     */
    private String contentMarkdown;
    /**
     * 置信度。
     */
    private String confidence;
    /**
     * 数据版本号，用于并发更新校验。
     */
    private long revision;
    /**
     * 创建时间。
     */
    private LocalDateTime createdAt;
    /**
     * 最近更新时间。
     */
    private LocalDateTime updatedAt;
}
