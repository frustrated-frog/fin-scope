package com.finscope.domain.research;

import java.util.Locale;

/**
 * 研究运行模式及其不可突破的资源预算。
 */
public enum ResearchMode {
    QUICK(2, 2, 1, 5, 1),
    DEEP(6, 3, 3, 10, 1);

    private final int searchActionBudget;
    private final int fullTextReadsPerSearch;
    private final int maxConcurrency;
    private final int maxIterations;
    private final int repairAttempts;

    ResearchMode(int searchActionBudget,
                 int fullTextReadsPerSearch,
                 int maxConcurrency,
                 int maxIterations,
                 int repairAttempts) {
        this.searchActionBudget = searchActionBudget;
        this.fullTextReadsPerSearch = fullTextReadsPerSearch;
        this.maxConcurrency = maxConcurrency;
        this.maxIterations = maxIterations;
        this.repairAttempts = repairAttempts;
    }

    public static ResearchMode from(String value) {
        if (value == null || value.trim().isEmpty()) return DEEP;
        try {
            return valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("研究模式必须是 QUICK 或 DEEP");
        }
    }

    public static ResearchMode defaultIfNull(ResearchMode value) {
        return value == null ? DEEP : value;
    }

    public int getSearchActionBudget() { return searchActionBudget; }
    public int getFullTextReadsPerSearch() { return fullTextReadsPerSearch; }
    public int getMaxConcurrency() { return maxConcurrency; }
    public int getMaxIterations() { return maxIterations; }
    public int getRepairAttempts() { return repairAttempts; }
}
