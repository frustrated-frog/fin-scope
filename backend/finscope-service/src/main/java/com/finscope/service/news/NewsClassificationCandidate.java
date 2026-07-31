package com.finscope.service.news;

import java.time.LocalDateTime;

public final class NewsClassificationCandidate {
    private final String itemId;
    private final String title;
    private final String content;
    private final String sourceName;
    private final LocalDateTime publishedAt;

    public NewsClassificationCandidate(String itemId, String title, String content,
                                       String sourceName, LocalDateTime publishedAt) {
        this.itemId = itemId;
        this.title = title;
        this.content = content;
        this.sourceName = sourceName;
        this.publishedAt = publishedAt;
    }

    public String getItemId() { return itemId; }
    public String getTitle() { return title; }
    public String getContent() { return content; }
    public String getSourceName() { return sourceName; }
    public LocalDateTime getPublishedAt() { return publishedAt; }
}
