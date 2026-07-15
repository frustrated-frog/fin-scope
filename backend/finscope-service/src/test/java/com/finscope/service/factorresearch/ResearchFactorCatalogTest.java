package com.finscope.service.factorresearch;

import com.finscope.domain.factorresearch.FactorLifecycleStatus;
import com.finscope.domain.factorresearch.ResearchFactorDefinition;
import com.finscope.service.quant.factor.FactorRegistry;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ResearchFactorCatalogTest {

    @Test
    void documentsEveryExecutableLegacyFactorAndTheCapitalCandidateWithoutOverclaimingValidation() {
        ResearchFactorCatalog catalog = new ResearchFactorCatalog();
        List<ResearchFactorDefinition> definitions = catalog.list();
        Set<String> codes = definitions.stream()
                .map(value -> value.getIdentity().getCode())
                .collect(Collectors.toSet());

        Set<String> executableCodes = new FactorRegistry().list().stream()
                .map(value -> value.getCode())
                .collect(Collectors.toSet());

        assertEquals(16, definitions.size());
        assertTrue(codes.containsAll(executableCodes));
        assertTrue(codes.contains("MAIN_FLOW_SHARE"));
        assertTrue(codes.contains("SUPER_LARGE_FLOW_SHARE"));
        assertTrue(codes.contains("BIG_ORDER_FLOW_SHARE"));
        assertEquals(definitions.size(), new HashSet<String>(definitions.stream()
                .map(value -> value.getIdentity().toString())
                .collect(Collectors.toList())).size());
        assertFalse(definitions.stream().anyMatch(value ->
                value.getStatus() == FactorLifecycleStatus.VALIDATED
                        || value.getStatus() == FactorLifecycleStatus.PRODUCTION_ELIGIBLE));
    }

    @Test
    void exposesProfessionalBoundariesAndCalculatorAlignedFormulas() {
        ResearchFactorCatalog catalog = new ResearchFactorCatalog();

        assertDefinition(catalog.get("quant", "MOMENTUM_20D", "1.0.0"),
                "adjustedClose[t] / adjustedClose[t-20] - 1", "20");
        assertDefinition(catalog.get("quant", "TURNOVER_PROXY_20D", "1.0.0"),
                "std(volume, 20) / mean(volume, 20)", "成交量");
        assertDefinition(catalog.get("quant", "EP", "1.0.0"), "1 / pe", "倒数");
        assertDefinition(catalog.get("quant", "LOW_DEBT", "1.0.0"), "-debtRatio", "负号");

        ResearchFactorDefinition capital = catalog.get("capital", "MAIN_FLOW_SHARE", "1.0.0");
        assertEquals(FactorLifecycleStatus.EXPLORATORY, capital.getStatus());
        assertTrue(capital.getInterpretationBoundary().contains("不构成投资建议"));
        assertTrue(catalog.get("capital", "BIG_ORDER_FLOW_SHARE", "1.0.0")
                .getMissingPolicy().contains("必需订单桶缺失"));
    }

    private void assertDefinition(ResearchFactorDefinition definition, String formula, String boundaryTerm) {
        assertEquals(formula, definition.getCalculationKey());
        assertTrue(definition.getPlainMeaning().length() >= 10);
        assertTrue(definition.getHypothesis().length() >= 20);
        assertTrue(definition.getEconomicRationale().length() >= 20);
        assertTrue(definition.getInterpretationBoundary().contains(boundaryTerm));
        assertFalse(definition.getRequiredFields().isEmpty());
        assertEquals(FactorLifecycleStatus.CALCULATION_VERIFIED, definition.getStatus());
    }
}
