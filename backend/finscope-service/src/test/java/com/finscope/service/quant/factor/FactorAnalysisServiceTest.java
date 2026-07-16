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

    @Test
    void rejectsConstantCrossSectionsAndSeparatesNegativeFromZeroDays() {
        FactorAnalysisService service = new FactorAnalysisService();
        assertTrue(Double.isNaN(service.rankIc(Arrays.asList(1d, 1d, 1d), Arrays.asList(1d, 2d, 3d))));
        com.finscope.domain.quant.factor.FactorAnalysis result = service
                .summarize("X", Arrays.asList(-0.1, 0d, 0.2));
        assertEquals(1d / 3d, result.getNegativeIcRatio(), 0.000001);
        assertEquals(1d / 3d, result.getZeroIcRatio(), 0.000001);
        assertTrue(result.getIcMeanCiLower() <= result.getIcMeanCiUpper());
    }

    @Test
    void calculatesTopBottomSpreadAndQuintileMonotonicity() {
        FactorAnalysisService service = new FactorAnalysisService();
        java.util.List<Double> factors = Arrays.asList(1d,2d,3d,4d,5d,6d,7d,8d,9d,10d);
        java.util.List<Double> returns = Arrays.asList(-.05,-.04,-.03,-.02,-.01,.01,.02,.03,.04,.05);
        assertEquals(0.09, service.quantileSpread(factors, returns), 0.000001);
        assertEquals(1d, service.quantileMonotonicity(factors, returns), 0.000001);
    }
}
