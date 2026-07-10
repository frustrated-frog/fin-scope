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

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}