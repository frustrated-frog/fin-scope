package com.finscope.domain.research;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ResearchRun {
    /**
     * 主键 ID。
     */
    private Long id;
    /**
     * 研究命题 ID。
     */
    private Long thesisId;
    /**
     * 研究深度模式；历史数据按 DEEP 解释。
     */
    private ResearchMode mode = ResearchMode.DEEP;
    /**
     * 运行日期。
     */
    private LocalDate runDate;
    /**
     * 主题编码列表。
     */
    private List<String> themeCodes = Collections.emptyList();
    /**
     * 来源数量。
     */
    private Integer sourceCount;
    /**
     * 已抓取来源数量。
     */
    private Integer fetchedSourceCount;
    /**
     * 文章数量。
     */
    private Integer articleCount;
    /**
     * 事件数量。
     */
    private Integer eventCount;
    /**
     * 证据数量。
     */
    private Integer evidenceCount;
    /**
     * 学习任务数量。
     */
    private Integer learningTaskCount;
    /**
     * 内容选题数量。
     */
    private Integer contentIdeaCount;
    /**
     * 简报日期。
     */
    private LocalDate briefDate;
    /**
     * 当前状态。
     */
    private String status;
    /**
     * 摘要。
     */
    private String summary;
    /**
     * 错误信息。
     */
    private String errorMessage;
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

    public Long getThesisId() {
        return thesisId;
    }

    public void setThesisId(Long thesisId) {
        this.thesisId = thesisId;
    }

    public ResearchMode getMode() {
        return mode;
    }

    public void setMode(ResearchMode mode) {
        this.mode = ResearchMode.defaultIfNull(mode);
    }

    public LocalDate getRunDate() {
        return runDate;
    }

    public void setRunDate(LocalDate runDate) {
        this.runDate = runDate;
    }

    public List<String> getThemeCodes() {
        return themeCodes;
    }

    public void setThemeCodes(List<String> themeCodes) {
        if (themeCodes == null || themeCodes.isEmpty()) {
            this.themeCodes = Collections.emptyList();
            return;
        }
        this.themeCodes = Collections.unmodifiableList(new ArrayList<String>(themeCodes));
    }

    public Integer getSourceCount() {
        return sourceCount;
    }

    public void setSourceCount(Integer sourceCount) {
        this.sourceCount = sourceCount;
    }

    public Integer getFetchedSourceCount() {
        return fetchedSourceCount;
    }

    public void setFetchedSourceCount(Integer fetchedSourceCount) {
        this.fetchedSourceCount = fetchedSourceCount;
    }

    public Integer getArticleCount() {
        return articleCount;
    }

    public void setArticleCount(Integer articleCount) {
        this.articleCount = articleCount;
    }

    public Integer getEventCount() {
        return eventCount;
    }

    public void setEventCount(Integer eventCount) {
        this.eventCount = eventCount;
    }

    public Integer getEvidenceCount() {
        return evidenceCount;
    }

    public void setEvidenceCount(Integer evidenceCount) {
        this.evidenceCount = evidenceCount;
    }

    public Integer getLearningTaskCount() {
        return learningTaskCount;
    }

    public void setLearningTaskCount(Integer learningTaskCount) {
        this.learningTaskCount = learningTaskCount;
    }

    public Integer getContentIdeaCount() {
        return contentIdeaCount;
    }

    public void setContentIdeaCount(Integer contentIdeaCount) {
        this.contentIdeaCount = contentIdeaCount;
    }

    public LocalDate getBriefDate() {
        return briefDate;
    }

    public void setBriefDate(LocalDate briefDate) {
        this.briefDate = briefDate;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getSummary() {
        return summary;
    }

    public void setSummary(String summary) {
        this.summary = summary;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
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
