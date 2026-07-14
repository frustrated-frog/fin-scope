package com.finscope.domain.fetch;

import java.time.LocalDateTime;

public class RawItem {
    /**
     * 标题。
     */
    private String title;
    /**
     * 资源 URL。
     */
    private String url;
    /**
     * 发布时间。
     */
    private LocalDateTime publishedAt;
    /**
     * 摘要。
     */
    private String summary;
    /**
     * 正文内容。
     */
    private String body;
    /**
     * 内容类型。
     */
    private String contentType;
    /**
     * 抽取方式。
     */
    private String extractionMethod;
    /**
     * 抽取备注。
     */
    private String extractionNote;
    /**
     * 质量评分。
     */
    private int qualityScore;
    /**
     * 来源信号评分。
     */
    private int sourceSignalScore;
    /**
     * 来源信号原因。
     */
    private String sourceSignalReason;
    /**
     * 来源排序。
     */
    private int sourceRank;

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

    public int getSourceSignalScore() {
        return sourceSignalScore;
    }

    public void setSourceSignalScore(int sourceSignalScore) {
        this.sourceSignalScore = sourceSignalScore;
    }

    public String getSourceSignalReason() {
        return sourceSignalReason;
    }

    public void setSourceSignalReason(String sourceSignalReason) {
        this.sourceSignalReason = sourceSignalReason;
    }

    public int getSourceRank() {
        return sourceRank;
    }

    public void setSourceRank(int sourceRank) {
        this.sourceRank = sourceRank;
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
