package com.finscope.service.search.evidence;

public class SearchEvidenceRequest {
    private final String query;
    private final SearchDepth depth;
    private final int maxResultsPerProvider;
    private final int maxEvidence;
    private final String zone;
    private final String language;
    private final long timeoutMs;

    public SearchEvidenceRequest(String query, SearchDepth depth, int maxResultsPerProvider,
                                 int maxEvidence, String zone, String language, long timeoutMs) {
        if (query == null || query.trim().isEmpty()) throw new IllegalArgumentException("搜索查询不能为空");
        this.query = query.trim();
        this.depth = depth == null ? SearchDepth.QUICK : depth;
        this.maxResultsPerProvider = Math.max(1, Math.min(20, maxResultsPerProvider));
        this.maxEvidence = Math.max(1, maxEvidence);
        this.zone = zone == null ? "" : zone.trim();
        this.language = language == null ? "" : language.trim();
        this.timeoutMs = Math.max(100L, timeoutMs);
    }

    public String getQuery() { return query; }
    public SearchDepth getDepth() { return depth; }
    public int getMaxResultsPerProvider() { return maxResultsPerProvider; }
    public int getMaxEvidence() { return maxEvidence; }
    public String getZone() { return zone; }
    public String getLanguage() { return language; }
    public long getTimeoutMs() { return timeoutMs; }
}
