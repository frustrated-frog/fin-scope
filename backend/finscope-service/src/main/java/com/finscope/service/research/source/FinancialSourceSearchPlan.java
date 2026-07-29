package com.finscope.service.research.source;

public final class FinancialSourceSearchPlan {
    private final String originalQuery;
    private final String effectiveQuery;
    private final boolean officialLane;

    FinancialSourceSearchPlan(String originalQuery, String effectiveQuery, boolean officialLane) {
        this.originalQuery = originalQuery;
        this.effectiveQuery = effectiveQuery;
        this.officialLane = officialLane;
    }

    public String getOriginalQuery() { return originalQuery; }
    public String getEffectiveQuery() { return effectiveQuery; }
    public boolean isOfficialLane() { return officialLane; }
}
