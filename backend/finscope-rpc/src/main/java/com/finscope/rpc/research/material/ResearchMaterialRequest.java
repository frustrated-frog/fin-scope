package com.finscope.rpc.research.material;

public final class ResearchMaterialRequest {
    private final String stockCode;
    private final String query;
    private final int limit;

    public ResearchMaterialRequest(String stockCode, String query, int limit) {
        String normalizedCode = stockCode == null ? "" : stockCode.trim();
        String normalizedQuery = query == null ? "" : query.replaceAll("[\\r\\n\\t]+", " ").trim();
        if (!normalizedCode.matches("\\d{6}")) {
            throw new IllegalArgumentException("研究资料检索仅支持六位 A 股代码");
        }
        if (normalizedQuery.length() > 100) {
            throw new IllegalArgumentException("研究资料查询词超过长度上限");
        }
        this.stockCode = normalizedCode;
        this.query = normalizedQuery;
        this.limit = Math.max(1, Math.min(limit, 50));
    }

    public String getStockCode() { return stockCode; }
    public String getQuery() { return query; }
    public int getLimit() { return limit; }
}
