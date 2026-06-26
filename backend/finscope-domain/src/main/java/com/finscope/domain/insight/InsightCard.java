package com.finscope.domain.insight;

import java.time.LocalDateTime;

public class InsightCard {
    private Long id;
    private Long articleId;
    private String title;
    private String sourceName;
    private String sourceUrl;
    private LocalDateTime publishedAt;
    private String oneSentenceSummary;
    private String coreEvent;
    private String importance;
    private String impactTargets;
    private String noveltyType;
    private String noveltyReason;
    private String followUpQuestions;
    private String cardMarkdown;

    // 新增字段：深度解读
    private String background;           // 背景是什么
    private String keyData;              // 关键数据
    private String timeline;             // 时间线
    private String relatedParties;       // 相关方
    private String riskFactors;          // 风险因素
    private String futureOutlook;        // 未来展望
    private String impactOnInvestment;   // 对投资的影响
    private String impactOnStartup;      // 对创业的影响
    private String professionalInsight;  // 专业解读
    private String facts;                // 事实
    private String reasoning;            // 推理
    private String opinions;             // 观点

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getArticleId() {
        return articleId;
    }

    public void setArticleId(Long articleId) {
        this.articleId = articleId;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getSourceName() {
        return sourceName;
    }

    public void setSourceName(String sourceName) {
        this.sourceName = sourceName;
    }

    public String getSourceUrl() {
        return sourceUrl;
    }

    public void setSourceUrl(String sourceUrl) {
        this.sourceUrl = sourceUrl;
    }

    public LocalDateTime getPublishedAt() {
        return publishedAt;
    }

    public void setPublishedAt(LocalDateTime publishedAt) {
        this.publishedAt = publishedAt;
    }

    public String getOneSentenceSummary() {
        return oneSentenceSummary;
    }

    public void setOneSentenceSummary(String oneSentenceSummary) {
        this.oneSentenceSummary = oneSentenceSummary;
    }

    public String getCoreEvent() {
        return coreEvent;
    }

    public void setCoreEvent(String coreEvent) {
        this.coreEvent = coreEvent;
    }

    public String getImportance() {
        return importance;
    }

    public void setImportance(String importance) {
        this.importance = importance;
    }

    public String getImpactTargets() {
        return impactTargets;
    }

    public void setImpactTargets(String impactTargets) {
        this.impactTargets = impactTargets;
    }

    public String getNoveltyType() {
        return noveltyType;
    }

    public void setNoveltyType(String noveltyType) {
        this.noveltyType = noveltyType;
    }

    public String getNoveltyReason() {
        return noveltyReason;
    }

    public void setNoveltyReason(String noveltyReason) {
        this.noveltyReason = noveltyReason;
    }

    public String getFollowUpQuestions() {
        return followUpQuestions;
    }

    public void setFollowUpQuestions(String followUpQuestions) {
        this.followUpQuestions = followUpQuestions;
    }

    public String getCardMarkdown() {
        return cardMarkdown;
    }

    public void setCardMarkdown(String cardMarkdown) {
        this.cardMarkdown = cardMarkdown;
    }

    public String getBackground() {
        return background;
    }

    public void setBackground(String background) {
        this.background = background;
    }

    public String getKeyData() {
        return keyData;
    }

    public void setKeyData(String keyData) {
        this.keyData = keyData;
    }

    public String getTimeline() {
        return timeline;
    }

    public void setTimeline(String timeline) {
        this.timeline = timeline;
    }

    public String getRelatedParties() {
        return relatedParties;
    }

    public void setRelatedParties(String relatedParties) {
        this.relatedParties = relatedParties;
    }

    public String getRiskFactors() {
        return riskFactors;
    }

    public void setRiskFactors(String riskFactors) {
        this.riskFactors = riskFactors;
    }

    public String getFutureOutlook() {
        return futureOutlook;
    }

    public void setFutureOutlook(String futureOutlook) {
        this.futureOutlook = futureOutlook;
    }

    public String getImpactOnInvestment() {
        return impactOnInvestment;
    }

    public void setImpactOnInvestment(String impactOnInvestment) {
        this.impactOnInvestment = impactOnInvestment;
    }

    public String getImpactOnStartup() {
        return impactOnStartup;
    }

    public void setImpactOnStartup(String impactOnStartup) {
        this.impactOnStartup = impactOnStartup;
    }

    public String getProfessionalInsight() {
        return professionalInsight;
    }

    public void setProfessionalInsight(String professionalInsight) {
        this.professionalInsight = professionalInsight;
    }

    public String getFacts() {
        return facts;
    }

    public void setFacts(String facts) {
        this.facts = facts;
    }

    public String getReasoning() {
        return reasoning;
    }

    public void setReasoning(String reasoning) {
        this.reasoning = reasoning;
    }

    public String getOpinions() {
        return opinions;
    }

    public void setOpinions(String opinions) {
        this.opinions = opinions;
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
