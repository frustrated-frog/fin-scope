package com.finscope.domain.research;

/** Extracted source material returned by the RPC acquisition boundary. */
public class ResearchSourceDocument {
    private final String finalUrl;
    private final String title;
    private final String body;
    private final String contentType;
    private final String extractionMethod;
    private final String fetchStatus;

    public ResearchSourceDocument(String finalUrl, String title, String body, String contentType,
                                  String extractionMethod, String fetchStatus) {
        this.finalUrl = finalUrl;
        this.title = title;
        this.body = body;
        this.contentType = contentType;
        this.extractionMethod = extractionMethod;
        this.fetchStatus = fetchStatus;
    }

    public String getFinalUrl() { return finalUrl; }
    public String getTitle() { return title; }
    public String getBody() { return body; }
    public String getContentType() { return contentType; }
    public String getExtractionMethod() { return extractionMethod; }
    public String getFetchStatus() { return fetchStatus; }
    public int getContentCharCount() { return body == null ? 0 : body.length(); }
}
