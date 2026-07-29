package com.finscope.service.research.source;

import org.springframework.stereotype.Component;

@Component
public class FinancialSourceQueryPolicy {
    private final OfficialFinancialSourceRegistry registry;

    public FinancialSourceQueryPolicy(OfficialFinancialSourceRegistry registry) {
        this.registry = registry;
    }

    public FinancialSourceSearchPlan plan(String query, String intent) {
        String original = query == null ? "" : query.trim();
        if (!"PRIMARY".equals(intent)) return new FinancialSourceSearchPlan(original, original, false);
        String sites = macro(original)
                ? "(site:pbc.gov.cn OR site:stats.gov.cn OR site:gov.cn OR site:sec.gov)"
                : "(site:cninfo.com.cn OR site:sse.com.cn OR site:szse.cn OR site:bse.cn OR site:csrc.gov.cn OR site:sec.gov)";
        return new FinancialSourceSearchPlan(original, original + " " + sites, true);
    }

    private boolean macro(String query) {
        String value = query.toLowerCase(java.util.Locale.ROOT);
        return value.contains("宏观") || value.contains("利率") || value.contains("货币")
                || value.contains("通胀") || value.contains("gdp") || value.contains("cpi")
                || value.contains("央行") || value.contains("统计");
    }
}
