package com.finscope.service.research.report;

import java.time.LocalDateTime;

/** A bounded, traceable evidence item used only by report generation. */
public class ResearchEvidenceDossier {
    private final String evidenceRef;
    private final Long articleId;
    private final Long evidenceId;
    private final String sourceIdentity;
    private final String sourceName;
    private final String sourceTier;
    private final String title;
    private final LocalDateTime publishedAt;
    private final String url;
    private final String factExcerpt;
    private final String stance;
    private final int relevanceScore;

    public ResearchEvidenceDossier(String evidenceRef, Long articleId, Long evidenceId, String sourceIdentity,
                            String sourceName, String sourceTier, String title, LocalDateTime publishedAt,
                            String url, String factExcerpt, String stance, int relevanceScore) {
        this.evidenceRef = evidenceRef;
        this.articleId = articleId;
        this.evidenceId = evidenceId;
        this.sourceIdentity = sourceIdentity;
        this.sourceName = sourceName;
        this.sourceTier = sourceTier;
        this.title = title;
        this.publishedAt = publishedAt;
        this.url = url;
        this.factExcerpt = factExcerpt;
        this.stance = stance;
        this.relevanceScore = relevanceScore;
    }

    public String getEvidenceRef() { return evidenceRef; }
    public Long getArticleId() { return articleId; }
    public Long getEvidenceId() { return evidenceId; }
    public String getSourceIdentity() { return sourceIdentity; }
    public String getSourceName() { return sourceName; }
    public String getSourceTier() { return sourceTier; }
    public String getTitle() { return title; }
    public LocalDateTime getPublishedAt() { return publishedAt; }
    public String getUrl() { return url; }
    public String getFactExcerpt() { return factExcerpt; }
    public String getStance() { return stance; }
    public int getRelevanceScore() { return relevanceScore; }
}
