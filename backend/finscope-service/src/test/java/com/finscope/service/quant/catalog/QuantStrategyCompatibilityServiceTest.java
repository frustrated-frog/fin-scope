package com.finscope.service.quant.catalog;

import com.finscope.domain.quant.catalog.QuantStrategyCatalogEntry;
import com.finscope.domain.quant.catalog.QuantStrategyCompatibility;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QuantStrategyCompatibilityServiceTest {
    private final QuantStrategyCompatibilityService service = new QuantStrategyCompatibilityService();

    @Test
    void mapsSupportedFactorFamiliesWithExplicitApproximationCaveats() {
        QuantStrategyCompatibility value = service.evaluate(entry("股票中的低波动因素效应"));

        assertEquals("ADAPTABLE", value.getStatus());
        assertEquals(Arrays.asList("VOLATILITY_20D"), value.getMappedFactors());
        assertTrue(value.getAdaptationNote().contains("20日"));
        assertTrue(value.getAdaptationNote().contains("近似"));
    }

    @Test
    void mapsBookToMarketAndShortReversalToRegisteredFactors() {
        assertEquals(Arrays.asList("BP"), service.evaluate(entry("价值（账面价值）因素")).getMappedFactors());
        assertEquals(Arrays.asList("REVERSAL_5D"), service.evaluate(entry("股票的短期反转效应")).getMappedFactors());
    }

    @Test
    void marksExactMissingFactorsAsResearchWork() {
        QuantStrategyCompatibility value = service.evaluate(entry("股票内部的ROA效应"));

        assertEquals("NEEDS_FACTOR", value.getStatus());
        assertEquals(Arrays.asList("ROA"), value.getMissingFactors());
    }

    @Test
    void rejectsLongShortPairAndDerivativeStrategiesAtTheEngineBoundary() {
        for (String title : Arrays.asList("空头利息效应--多空版本", "与股票的配对交易", "股票期权日内策略")) {
            QuantStrategyCompatibility value = service.evaluate(entry(title));
            assertEquals("UNSUPPORTED", value.getStatus(), title);
            assertTrue(value.getAdaptationNote().contains("当前引擎"), title);
        }
    }

    @Test
    void keepsUnknownStrategiesAsUnmappedResearchCandidates() {
        QuantStrategyCompatibility value = service.evaluate(entry("公司文件词汇密度"));

        assertEquals("NEEDS_FACTOR", value.getStatus());
        assertTrue(value.getMissingFactors().isEmpty());
        assertTrue(value.getAdaptationNote().contains("人工拆解"));
    }

    private QuantStrategyCatalogEntry entry(String title) {
        QuantStrategyCatalogEntry value = new QuantStrategyCatalogEntry();
        value.setTitle(title);
        return value;
    }
}
