package com.finscope.domain.research;

import java.time.LocalDateTime;

/**
 * 研究工作区中的长期研究问题。
 */
public class ResearchThesis {
    /**
     * 主键 ID。
     */
    private Long id;
    /**
     * 研究问题。
     */
    private String question;
    /**
     * 研究对象类型。
     */
    private String subjectType;
    /**
     * 研究对象名称。
     */
    private String subjectName;
    /**
     * 研究对象编码。
     */
    private String subjectCode;
    /**
     * 当前状态。
     */
    private String status;
    /**
     * 研究结论。
     */
    private String conclusion;
    /**
     * 置信度。
     */
    private String confidence;
    /**
     * 下一步验证事项。
     */
    private String nextValidation;
    /**
     * 创建时间。
     */
    private LocalDateTime createdAt;
    /**
     * 最近更新时间。
     */
    private LocalDateTime updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getQuestion() { return question; }
    public void setQuestion(String question) { this.question = question; }
    public String getSubjectType() { return subjectType; }
    public void setSubjectType(String subjectType) { this.subjectType = subjectType; }
    public String getSubjectName() { return subjectName; }
    public void setSubjectName(String subjectName) { this.subjectName = subjectName; }
    public String getSubjectCode() { return subjectCode; }
    public void setSubjectCode(String subjectCode) { this.subjectCode = subjectCode; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getConclusion() { return conclusion; }
    public void setConclusion(String conclusion) { this.conclusion = conclusion; }
    public String getConfidence() { return confidence; }
    public void setConfidence(String confidence) { this.confidence = confidence; }
    public String getNextValidation() { return nextValidation; }
    public void setNextValidation(String nextValidation) { this.nextValidation = nextValidation; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
