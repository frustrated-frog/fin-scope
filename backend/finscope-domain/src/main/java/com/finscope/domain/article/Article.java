package com.finscope.domain.article;

import java.time.LocalDateTime;

public class Article {
    private Long id;
    private Long sourceId;
    private String sourceName;
    private String title;
    private String url;
    private LocalDateTime publishedAt;
    private String summary;
    private String body;
    private String category;
    private String noveltyType;
    private String noveltyReason;
    private LocalDateTime fetchedAt;

    public static Article createFetched(Long sourceId, String sourceName, String title, String url,
                                        LocalDateTime publishedAt, String summary, String body) {
        Article article = new Article();
        article.sourceId = sourceId;
        article.sourceName = sourceName;
        article.title = title;
        article.url = url;
        article.publishedAt = publishedAt;
        article.summary = summary;
        article.body = body;
        article.category = "市场";
        article.noveltyType = "NEW";
        article.noveltyReason = "首次进入信息流";
        article.fetchedAt = LocalDateTime.now();
        return article;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getSourceId() {
        return sourceId;
    }

    public void setSourceId(Long sourceId) {
        this.sourceId = sourceId;
    }

    public String getSourceName() {
        return sourceName;
    }

    public void setSourceName(String sourceName) {
        this.sourceName = sourceName;
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

    public LocalDateTime getPublishedAt() {
        return publishedAt;
    }

    public void setPublishedAt(LocalDateTime publishedAt) {
        this.publishedAt = publishedAt;
    }

    public String getSummary() {
        return summary;
    }

    public void setSummary(String summary) {
        this.summary = summary;
    }

    public String getBody() {
        return body;
    }

    public void setBody(String body) {
        this.body = body;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
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

    public LocalDateTime getFetchedAt() {
        return fetchedAt;
    }

    public void setFetchedAt(LocalDateTime fetchedAt) {
        this.fetchedAt = fetchedAt;
    }
}
