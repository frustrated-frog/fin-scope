package com.finscope.domain.research;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class LearningTask {
    private Long id;
    private Long eventId;
    private Long topicId;
    private String themeCode;
    private String question;
    private String concepts;
    private String difficulty;
    private String status;
    private String whyNeeded;
    private String origin = "AGENT";
    private String taskKey;
    private int priority = 50;
    private LocalDateTime acceptedAt;
    private String dismissedReason;
    private String completionMode;
    private long revision;
    private LocalDateTime createdAt;
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
