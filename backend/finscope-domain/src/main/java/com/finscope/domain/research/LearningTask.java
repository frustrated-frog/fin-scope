package com.finscope.domain.research;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class LearningTask {
    /**
     * 主键 ID。
     */
    private Long id;
    /**
     * 事件 ID。
     */
    private Long eventId;
    /**
     * 主题 ID。
     */
    private Long topicId;
    /**
     * 主题编码。
     */
    private String themeCode;
    /**
     * 研究问题。
     */
    private String question;
    /**
     * 概念列表。
     */
    private String concepts;
    /**
     * 难度。
     */
    private String difficulty;
    /**
     * 当前状态。
     */
    private String status;
    /**
     * 学习必要性。
     */
    private String whyNeeded;
    /**
     * 来源。
     */
    private String origin = "AGENT";
    /**
     * 任务键。
     */
    private String taskKey;
    /**
     * 优先级。
     */
    private int priority = 50;
    /**
     * 接收时间。
     */
    private LocalDateTime acceptedAt;
    /**
     * 忽略原因。
     */
    private String dismissedReason;
    /**
     * 完成方式。
     */
    private String completionMode;
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

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getEventId() {
        return eventId;
    }

    public void setEventId(Long eventId) {
        this.eventId = eventId;
    }

    public String getThemeCode() {
        return themeCode;
    }

    public void setThemeCode(String themeCode) {
        this.themeCode = themeCode;
    }

    public String getQuestion() {
        return question;
    }

    public void setQuestion(String question) {
        this.question = question;
    }

    public String getConcepts() {
        return concepts;
    }

    public void setConcepts(String concepts) {
        this.concepts = concepts;
    }

    public String getDifficulty() {
        return difficulty;
    }

    public void setDifficulty(String difficulty) {
        this.difficulty = difficulty;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getWhyNeeded() {
        return whyNeeded;
    }

    public void setWhyNeeded(String whyNeeded) {
        this.whyNeeded = whyNeeded;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
