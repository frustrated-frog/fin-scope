package com.finscope.domain.fetch;

import java.time.LocalDateTime;

public class RawItem {
    private String title;
    private String url;
    private LocalDateTime publishedAt;
    private String summary;
    private String body;
    private String contentType;
    private String extractionMethod;
    private String extractionNote;
    private int qualityScore;

    public RawItem() {
    }

    public RawItem(String title, String url, LocalDateTime publishedAt, String summary, String body) {
        this.title = title;
        this.url = url;
        this.publishedAt = publishedAt;
        this.summary = summary;
        this.body = body;
        this.contentType = "ARTICLE";
        this.extractionMethod = "unknown";
        this.qualityScore = score(summary, body);
    }

    public String getTitle() {
        return title;
    }

    public String getUrl() {
        return url;
    }

    public LocalDateTime getPublishedAt() {
        return publishedAt;
    }

    public String getSummary() {
        return summary;
    }

    public String getBody() {
        return body;
    }

    public String getContentType() {
        return contentType;
    }

    public void setContentType(String contentType) {
        this.contentType = contentType;
    }

    public String getExtractionMethod() {
        return extractionMethod;
    }

    public void setExtractionMethod(String extractionMethod) {
        this.extractionMethod = extractionMethod;
    }

    public String getExtractionNote() {
        return extractionNote;
    }

    public void setExtractionNote(String extractionNote) {
        this.extractionNote = extractionNote;
    }

    public int getQualityScore() {
        return qualityScore;
    }

    public void setQualityScore(int qualityScore) {
        this.qualityScore = qualityScore;
    }

    public RawItem withExtraction(String contentType, String extractionMethod, int qualityScore, String extractionNote) {
        this.contentType = contentType;
        this.extractionMethod = extractionMethod;
        this.qualityScore = qualityScore;
        this.extractionNote = extractionNote;
        return this;
    }

    private int score(String summary, String body) {
        int length = (summary == null ? 0 : summary.length()) + (body == null ? 0 : body.length());
        if (length >= 800) {
            return 90;
        }
        if (length >= 300) {
            return 80;
        }
        if (length >= 80) {
            return 65;
        }
        return 40;
    }
}
