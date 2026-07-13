package com.finscope.service.research.report;

import com.finscope.domain.article.Article;
import com.finscope.domain.research.EvidenceItem;

public class ResearchEvidenceCard {
    private final Article article;
    private final EvidenceItem evidenceItem;
    private final String stance;
    private final int relevanceScore;
    private final String claim;

    ResearchEvidenceCard(Article article, EvidenceItem evidenceItem, String stance, int relevanceScore, String claim) {
        this.article = article;
        this.evidenceItem = evidenceItem;
        this.stance = stance;
        this.relevanceScore = relevanceScore;
        this.claim = claim;
    }

    public Article getArticle() { return article; }
    public EvidenceItem getEvidenceItem() { return evidenceItem; }
    public String getStance() { return stance; }
    public int getRelevanceScore() { return relevanceScore; }
    public String getClaim() { return claim; }
}
