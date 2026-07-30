package com.finscope.domain.search;

/**
 * 搜索供应商通用请求。
 */
public class WebSearchRequest {
    private final String query;
    private final int maxResults;
    private final String zone;
    private final String language;

    public WebSearchRequest(String query, int maxResults, String zone, String language) {
        if (query == null || query.trim().isEmpty()) {
            throw new IllegalArgumentException("搜索查询不能为空");
        }
        this.query = query.trim();
        this.maxResults = Math.max(1, Math.min(20, maxResults));
        this.zone = zone == null ? "" : zone.trim();
        this.language = language == null ? "" : language.trim();
    }

    public String getQuery() { return query; }
    public int getMaxResults() { return maxResults; }
    public String getZone() { return zone; }
    public String getLanguage() { return language; }
}
