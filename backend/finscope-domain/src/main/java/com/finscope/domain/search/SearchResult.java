package com.finscope.domain.search;

/**
 * 联网搜索单条结果，来源于 WebSearchClient（如 Tavily）。
 */
public class SearchResult {
    /**
     * 标题。
     */
    private String title;
    /**
     * 资源 URL。
     */
    private String url;
    /**
     * 正文内容。
     */
    private String content;
    /**
     * 来源域名。
     */
    private String sourceDomain;
    /**
     * 来源层级。
     */
    private String sourceTier;
    /**
     * 搜索相关性评分。
     */
    private Double score;
    /**
     * 发布时间。
     */
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