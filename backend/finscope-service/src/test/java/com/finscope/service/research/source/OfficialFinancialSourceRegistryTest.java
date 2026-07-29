package com.finscope.service.research.source;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OfficialFinancialSourceRegistryTest {

    @Test
    void classifiesOfficialAndProfessionalFinancialDomainsFromOneRegistry() {
        OfficialFinancialSourceRegistry registry = new OfficialFinancialSourceRegistry();

        assertTrue(registry.isOfficial("static.sse.com.cn"));
        assertTrue(registry.isOfficial("www.sec.gov"));
        assertEquals("T1", registry.resolveTier("www.cninfo.com.cn", "T3"));
        assertEquals("T2", registry.resolveTier("www.caixin.com", "T3"));
        assertEquals("T3", registry.resolveTier("unknown.example.com", "T3"));
        assertFalse(registry.isOfficial("fake-sse.com.cn"));
    }

    @Test
    void primaryIntentUsesOfficialLaneWhileUpdateKeepsGeneralSearch() {
        FinancialSourceQueryPolicy policy = new FinancialSourceQueryPolicy(
                new OfficialFinancialSourceRegistry());

        FinancialSourceSearchPlan primary = policy.plan("长鑫科技 IPO 募集资金", "PRIMARY");
        FinancialSourceSearchPlan update = policy.plan("长鑫科技 最新进展", "UPDATE");

        assertTrue(primary.isOfficialLane());
        assertTrue(primary.getEffectiveQuery().contains("site:cninfo.com.cn"));
        assertTrue(primary.getEffectiveQuery().contains("site:sse.com.cn"));
        assertEquals("长鑫科技 最新进展", update.getEffectiveQuery());
        assertFalse(update.isOfficialLane());
    }
}
