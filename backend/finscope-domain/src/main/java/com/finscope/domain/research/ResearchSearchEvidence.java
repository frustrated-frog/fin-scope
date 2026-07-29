package com.finscope.domain.research;

import java.time.LocalDateTime;

/** Run-scoped web evidence that is intentionally isolated from the article library. */
public class ResearchSearchEvidence {
    private Long id;
    private Long researchRunId;
    private Long decisionId;
    private String provider;
    private String queryText;
    private String intent;
    private String title;
    private String url;
    private String content;
    private String sourceDomain;
    private String sourceTier;
    private Double relevanceScore;
    private String publishedAt;
    private LocalDateTime createdAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getResearchRunId() { return researchRunId; }
    public void setResearchRunId(Long researchRunId) { this.researchRunId = researchRunId; }
    public Long getDecisionId() { return decisionId; }
    public void setDecisionId(Long decisionId) { this.decisionId = decisionId; }
    public String getProvider() { return provider; }
    public void setProvider(String provider) { this.provider = provider; }
    public String getQueryText() { return queryText; }
    public void setQueryText(String queryText) { this.queryText = queryText; }
    public String getIntent() { return intent; }
    public void setIntent(String intent) { this.intent = intent; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getUrl() { return url; }
    public void setUrl(String url) { this.url = url; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    public String getSourceDomain() { return sourceDomain; }
    public void setSourceDomain(String sourceDomain) { this.sourceDomain = sourceDomain; }
    public String getSourceTier() { return sourceTier; }
    public void setSourceTier(String sourceTier) { this.sourceTier = sourceTier; }
    public Double getRelevanceScore() { return relevanceScore; }
    public void setRelevanceScore(Double relevanceScore) { this.relevanceScore = relevanceScore; }
    public String getPublishedAt() { return publishedAt; }
    public void setPublishedAt(String publishedAt) { this.publishedAt = publishedAt; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
