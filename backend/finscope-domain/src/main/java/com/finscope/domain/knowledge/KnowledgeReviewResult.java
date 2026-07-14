package com.finscope.domain.knowledge;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class KnowledgeReviewResult {
    /**
     * 知识条目。
     */
    private KnowledgeEntry entry;
    /**
     * 审核时间。
     */
    private LocalDateTime reviewedAt;
    /**
     * 下次复习时间。
     */
    private LocalDateTime nextReviewAt;
    /**
     * 复习间隔天数。
     */
    private int intervalDays;
    /**
     * 复习次数。
     */
    private int reviewCount;
    /**
     * 数据版本号，用于并发更新校验。
     */
    private long revision;
}
