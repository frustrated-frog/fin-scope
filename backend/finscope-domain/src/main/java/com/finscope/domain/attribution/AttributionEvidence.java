package com.finscope.domain.attribution;

import java.time.LocalDateTime;

/**
 * 归因证据：一条支撑归因结论的线索（来自全网搜索或本地新闻）。
 */
public class AttributionEvidence {
    private Long id;
    private Long reportId;
    /** 证据来源：WEB_SEARCH | LOCAL_NEWS | QUOTE */
    private String origin;
    private String title;
    private String url;
    /** 摘要/关键陈述 */
    private String snippet;
    private String sourceDomain;
    /** 来源可信度：T1 | T2 | T3 */
    private String sourceTier;
    /** 相关度 0~100 */
    private Integer relevance;
    /** COMPANY | INDUSTRY | MACRO | MARKET | COUNTER 等研究事件类别。 */
    private String eventType;
    /** SUPPORT | COUNTER | BACKGROUND。 */
    private String stance;
    /** DIRECT | INDIRECT | BACKGROUND。 */
    private String directness;
    /** 来源给出的发布时间原文，避免不同来源格式导致反序列化失败。 */
    private String publishedAt;
    /** 归一化后的事件聚合键。 */
    private String eventKey;
    /** 是否为历史报告带入的背景，不计入当日证据质量。 */
    private boolean historicalContext;
    private LocalDateTime createdAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getReportId() {
        return reportId;
    }

    public void setReportId(Long reportId) {
        this.reportId = reportId;
    }

    public String getOrigin() {
        return origin;
    }

    public void setOrigin(String origin) {
        this.origin = origin;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getSnippet() {
        return snippet;
    }

    public void setSnippet(String snippet) {
        this.snippet = snippet;
    }

    public String getSourceDomain() {
        return sourceDomain;
    }

    public void setSourceDomain(String sourceDomain) {
        this.sourceDomain = sourceDomain;
    }

    public String getSourceTier() {
        return sourceTier;
    }

    public void setSourceTier(String sourceTier) {
        this.sourceTier = sourceTier;
    }

    public Integer getRelevance() {
        return relevance;
    }

    public void setRelevance(Integer relevance) {
        this.relevance = relevance;
    }

    public String getEventType() { return eventType; }
    public void setEventType(String eventType) { this.eventType = eventType; }
    public String getStance() { return stance; }
    public void setStance(String stance) { this.stance = stance; }
    public String getDirectness() { return directness; }
    public void setDirectness(String directness) { this.directness = directness; }
    public String getPublishedAt() { return publishedAt; }
    public void setPublishedAt(String publishedAt) { this.publishedAt = publishedAt; }
    public String getEventKey() { return eventKey; }
    public void setEventKey(String eventKey) { this.eventKey = eventKey; }
    public boolean isHistoricalContext() { return historicalContext; }
    public void setHistoricalContext(boolean historicalContext) { this.historicalContext = historicalContext; }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
