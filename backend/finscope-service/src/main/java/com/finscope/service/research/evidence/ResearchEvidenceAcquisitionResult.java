package com.finscope.service.research.evidence;

public final class ResearchEvidenceAcquisitionResult {
    private final String content;
    private final String searchSnippet;
    private final String contentOrigin;
    private final String extractionMethod;
    private final String fetchStatus;
    private final int contentCharCount;

    public ResearchEvidenceAcquisitionResult(String content, String searchSnippet, String contentOrigin,
                                             String extractionMethod, String fetchStatus, int contentCharCount) {
        this.content = content;
        this.searchSnippet = searchSnippet;
        this.contentOrigin = contentOrigin;
        this.extractionMethod = extractionMethod;
        this.fetchStatus = fetchStatus;
        this.contentCharCount = contentCharCount;
    }

    public String getContent() { return content; }
    public String getSearchSnippet() { return searchSnippet; }
    public String getContentOrigin() { return contentOrigin; }
    public String getExtractionMethod() { return extractionMethod; }
    public String getFetchStatus() { return fetchStatus; }
    public int getContentCharCount() { return contentCharCount; }
}
