package com.finscope.domain.knowledge;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class TopicReviewState {
    /**
     * 主题 ID。
     */
    private Long topicId;
    /**
     * 最近复习时间。
     */
    private LocalDateTime lastReviewedAt;
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
