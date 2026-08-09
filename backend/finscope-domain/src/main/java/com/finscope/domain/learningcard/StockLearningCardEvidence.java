package com.finscope.domain.learningcard;

public class StockLearningCardEvidence {
    private Long databaseId;
    private Long runId;
    private String dimensionCode;
    private String evidenceCode;
    private String title;
    private String url;
    private String source;
    private String publishedAt;
    private String content;
    private String contentOrigin;
    private int sortOrder;

    public StockLearningCardEvidence() { }

    public StockLearningCardEvidence(String evidenceCode, String title, String url, String source,
                                     String publishedAt, String content) {
        this.evidenceCode = evidenceCode; this.title = title; this.url = url; this.source = source;
        this.publishedAt = publishedAt; this.content = content;
    }

    public Long getDatabaseId() { return databaseId; }
    public void setDatabaseId(Long databaseId) { this.databaseId = databaseId; }
    public Long getRunId() { return runId; }
    public void setRunId(Long runId) { this.runId = runId; }
    public String getDimensionCode() { return dimensionCode; }
    public void setDimensionCode(String dimensionCode) { this.dimensionCode = dimensionCode; }
    public String getEvidenceCode() { return evidenceCode; }
    public void setEvidenceCode(String evidenceCode) { this.evidenceCode = evidenceCode; }
    public String getId() { return evidenceCode; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getUrl() { return url; }
    public void setUrl(String url) { this.url = url; }
    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }
    public String getPublishedAt() { return publishedAt; }
    public void setPublishedAt(String publishedAt) { this.publishedAt = publishedAt; }
    public String content() { return content; }
    public void content(String content) { this.content = content; }
    public String getContentOrigin() { return contentOrigin; }
    public void setContentOrigin(String contentOrigin) { this.contentOrigin = contentOrigin; }
    public int getSortOrder() { return sortOrder; }
    public void setSortOrder(int sortOrder) { this.sortOrder = sortOrder; }
}
