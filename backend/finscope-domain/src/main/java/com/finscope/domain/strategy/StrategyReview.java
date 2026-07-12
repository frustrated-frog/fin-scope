package com.finscope.domain.strategy;

import java.time.LocalDate;
import java.time.LocalDateTime;

public class StrategyReview {
    private Long id;
    private LocalDate reviewDate;
    private String facts;
    private String reasoning;
    private String nextAction;
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
