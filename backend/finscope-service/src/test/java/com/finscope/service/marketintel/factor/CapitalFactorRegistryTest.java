package com.finscope.service.marketintel.factor;

import com.finscope.domain.marketintel.CapitalFactorDefinition;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CapitalFactorRegistryTest {
    @Test
    void exposesOnlyPublishedAuditableDefinitionsToTheOnlineEngine() {
        CapitalFactorRegistry registry = new CapitalFactorRegistry();
        List<CapitalFactorDefinition> published = registry.published();

        assertTrue(published.size() >= 21);
        Set<String> codes = new HashSet<String>();
        Set<String> calculationKeys = new HashSet<String>();
        for (CapitalFactorDefinition definition : published) {
            assertTrue(codes.add(definition.getCode()));
            assertTrue(calculationKeys.add(definition.getCalculationKey()));
            assertEquals(CapitalFactorDefinition.AdmissionStatus.PUBLISHED, definition.getAdmissionStatus());
            assertFalse(definition.getCanonicalFormula().trim().isEmpty());
            assertFalse(definition.getInterpretationBoundary().trim().isEmpty());
        }
        assertTrue(codes.contains("MAIN_FLOW_SHARE"));
        assertTrue(codes.contains("BIG_SMALL_DIVERGENCE"));
        assertTrue(codes.contains("LATE_SESSION_FLOW_SHARE"));
        assertFalse(registry.find("NOT_PUBLISHED").isPresent());
    }
}
