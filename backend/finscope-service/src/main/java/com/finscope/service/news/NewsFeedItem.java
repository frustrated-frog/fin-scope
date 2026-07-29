package com.finscope.service.news;

import java.time.LocalDateTime;

public final class NewsFeedItem {
    private final String id;
    private final String kind;
    private final String title;
    private final String content;
    private final String url;
    private final LocalDateTime publishedAt;
    private final String providerCode;
    private final String sourceName;
    private final String sourceTier;

    public NewsFeedItem(String id, String kind, String title, String content, String url,
                        LocalDateTime publishedAt, String providerCode, String sourceName, String sourceTier) {
        this.id = id; this.kind = kind; this.title = title; this.content = content; this.url = url;
        this.publishedAt = publishedAt; this.providerCode = providerCode; this.sourceName = sourceName;
        this.sourceTier = sourceTier;
    }

    public String getId() { return id; }
    public String getKind() { return kind; }
    public String getTitle() { return title; }
    public String getContent() { return content; }
    public String getUrl() { return url; }
    public LocalDateTime getPublishedAt() { return publishedAt; }
    public String getProviderCode() { return providerCode; }
    public String getSourceName() { return sourceName; }
    public String getSourceTier() { return sourceTier; }
}
