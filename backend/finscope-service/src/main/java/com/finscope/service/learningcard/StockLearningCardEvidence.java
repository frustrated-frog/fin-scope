package com.finscope.service.learningcard;

public final class StockLearningCardEvidence {
    private final String id;
    private final String title;
    private final String url;
    private final String source;
    private final String publishedAt;
    private final String content;

    public StockLearningCardEvidence(String id, String title, String url, String source,
                                     String publishedAt, String content) {
        this.id = id; this.title = title; this.url = url; this.source = source;
        this.publishedAt = publishedAt; this.content = content;
    }

    public String getId() { return id; }
    public String getTitle() { return title; }
    public String getUrl() { return url; }
    public String getSource() { return source; }
    public String getPublishedAt() { return publishedAt; }
    public String getContent() { return content; }
}
