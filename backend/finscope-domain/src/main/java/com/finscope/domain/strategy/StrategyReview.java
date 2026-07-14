package com.finscope.domain.strategy;

import java.time.LocalDate;
import java.time.LocalDateTime;

public class StrategyReview {
    /**
     * 主键 ID。
     */
    private Long id;
    /**
     * 复盘日期。
     */
    private LocalDate reviewDate;
    /**
     * 事实记录。
     */
    private String facts;
    /**
     * 推理过程。
     */
    private String reasoning;
    /**
     * 下一步行动。
     */
    private String nextAction;
    /**
     * 创建时间。
     */
    private LocalDateTime createdAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public LocalDate getReviewDate() { return reviewDate; }
    public void setReviewDate(LocalDate reviewDate) { this.reviewDate = reviewDate; }
    public String getFacts() { return facts; }
    public void setFacts(String facts) { this.facts = facts; }
    public String getReasoning() { return reasoning; }
    public void setReasoning(String reasoning) { this.reasoning = reasoning; }
    public String getNextAction() { return nextAction; }
    public void setNextAction(String nextAction) { this.nextAction = nextAction; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
