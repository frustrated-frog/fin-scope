package com.finscope.domain.search;

/**
 * 联网搜索单条结果，来源于 WebSearchClient（如 Tavily）。
 */
public class SearchResult {
    private String title;
    private String url;
    /** 摘要/正文片段 */
    private String content;
    /** 来源域名，如 caixin.com */
    private String sourceDomain;
    /** 来源可信度分级 T1/T2/T3 */
    private String sourceTier;
    /** 相关度评分 0~1 */
    private Double score;
    /** 发布时间文本（原样保留） */
    private String publishedAt;

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

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
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

    public Double getScore() {
        return score;
    }

    public void setScore(Double score) {
        this.score = score;
    }

    public String getPublishedAt() {
        return publishedAt;
    }

    public void setPublishedAt(String publishedAt) {
        this.publishedAt = publishedAt;
    }
}