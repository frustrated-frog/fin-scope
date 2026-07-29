package com.finscope.service.research.source;

import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

@Component
public class OfficialFinancialSourceRegistry {
    private final Set<String> officialDomains = Collections.unmodifiableSet(new LinkedHashSet<String>(Arrays.asList(
            "gov.cn", "csrc.gov.cn", "sse.com.cn", "szse.cn", "bse.cn", "cninfo.com.cn",
            "pbc.gov.cn", "stats.gov.cn", "safe.gov.cn", "samr.gov.cn",
            "sec.gov", "federalreserve.gov", "treasury.gov", "nyse.com", "nasdaq.com",
            "hkexnews.hk", "hkex.com.hk")));
    private final Set<String> professionalDomains = Collections.unmodifiableSet(new LinkedHashSet<String>(Arrays.asList(
            "caixin.com", "cnstock.com", "stcn.com", "xinhuanet.com", "chinanews.com",
            "eastmoney.com", "yicai.com", "21jingji.com", "reuters.com", "bloomberg.com", "cnbc.com")));

    public boolean isOfficial(String domain) {
        return matches(domain, officialDomains);
    }

    public String resolveTier(String domain, String fallback) {
        if (isOfficial(domain)) return "T1";
        if (matches(domain, professionalDomains)) return "T2";
        return text(fallback).isEmpty() ? "T3" : fallback.trim().toUpperCase(Locale.ROOT);
    }

    public Set<String> officialDomains() { return officialDomains; }

    private boolean matches(String domain, Set<String> candidates) {
        String value = text(domain).toLowerCase(Locale.ROOT);
        for (String candidate : candidates) {
            if (value.equals(candidate) || value.endsWith("." + candidate)) return true;
        }
        return false;
    }

    private String text(String value) { return value == null ? "" : value.trim(); }
}
