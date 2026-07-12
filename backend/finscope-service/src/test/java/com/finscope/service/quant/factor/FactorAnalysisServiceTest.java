package com.finscope.service.quant.factor;

import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FactorAnalysisServiceTest {
    @Test
    void calculatesSpearmanRankIcWithTies() {
        FactorAnalysisService service = new FactorAnalysisService();
        double ic = service.rankIc(Arrays.asList(1d, 2d, 2d, 4d), Arrays.asList(1d, 2d, 3d, 4d));
        assertEquals(0.948683, ic, 0.000001);
    }

    @Test
    void summarizesIcWithoutProducingNonFiniteValues() {
        com.finscope.domain.quant.factor.FactorAnalysis result = new FactorAnalysisService()
                .summarize("EP", Arrays.asList(0.10, -0.05, 0.08, 0.12));
        assertEquals("EP", result.getFactorCode());
        assertTrue(Double.isFinite(result.getIcIr()));
        assertEquals(0.75, result.getPositiveIcRatio(), 0.000001);
    }
}
