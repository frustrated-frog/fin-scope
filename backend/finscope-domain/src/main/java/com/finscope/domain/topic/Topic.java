package com.finscope.domain.topic;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class Topic {
    /**
     * 主键 ID。
     */
    private Long id;
    /**
     * 名称。
     */
    private String name;
    /**
     * URL 友好的主题标识。
     */
    private String slug;
    /**
     * 描述信息。
     */
    private String description;
    /**
     * 当前状态。
     */
    private String status = "LEARNING";
    /**
     * Markdown 文件路径。
     */
    private String markdownPath;
    /**
     * 术语列表。
     */
    private String terms;
    /**
     * 学习问题列表。
     */
    private String learningQuestions;
    /**
     * 生命周期状态。
     */
    private String lifecycleStatus = "ACTIVE";
    /**
     * 掌握状态。
     */
    private String masteryStatus = "EXPLORING";
    /**
     * 数据版本号，用于并发更新校验。
     */
    private long revision;
    /**
     * 文章数量。
     */
    private int articleCount;
    /**
     * 关联简报数量。
     */
    private int briefCount;
    /**
     * 创建时间。
     */
    private LocalDateTime createdAt;
    /**
     * 最近更新时间。
     */
    private LocalDateTime updatedAt;

}
