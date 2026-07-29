package com.finscope.service.research.report;

import com.finscope.domain.article.Article;
import com.finscope.domain.research.EvidenceItem;
import com.finscope.domain.research.ResearchSourceIdentity;

public class ResearchEvidenceCard {
    private final Article article;
    private final EvidenceItem evidenceItem;
    private final String stance;
    private final int relevanceScore;
    private final String claim;
    private final String sourceIdentity;
    private final String sourceTier;

    ResearchEvidenceCard(Article article, EvidenceItem evidenceItem, String stance, int relevanceScore, String claim) {
        this.article = article;
        this.evidenceItem = evidenceItem;
        this.stance = stance;
        this.relevanceScore = relevanceScore;
        this.claim = claim;
        this.sourceIdentity = ResearchSourceIdentity.resolve(article);
        this.sourceTier = null;
    }

    ResearchEvidenceCard(Article article, EvidenceItem evidenceItem, String stance, int relevanceScore,
                         String claim, String sourceIdentity, String sourceTier) {
        this.article = article;
        this.evidenceItem = evidenceItem;
        this.stance = stance;
        this.relevanceScore = relevanceScore;
        this.claim = claim;
        this.sourceIdentity = sourceIdentity;
        this.sourceTier = sourceTier;
    }

    public Article getArticle() { return article; }
    public EvidenceItem getEvidenceItem() { return evidenceItem; }
    public String getStance() { return stance; }
    public int getRelevanceScore() { return relevanceScore; }
    public String getClaim() { return claim; }
    public String getSourceIdentity() { return sourceIdentity; }
    public String getSourceTier() { return sourceTier; }
}
