package com.finscope.service.factorresearch;

import com.finscope.domain.quant.factor.FactorAnalysis;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FactorEvidenceAssessmentServiceTest {
    private final FactorEvidenceAssessmentService service = new FactorEvidenceAssessmentService();

    @Test
    void alignsLowDirectionMetricsWithoutCallingAnInSampleDiagnosticValidated() {
        FactorAnalysis analysis = analysis(-0.06, 0.12, 0.30, 80);

        service.assess(analysis, "NEGATIVE_HYPOTHESIS", "REAL");

        assertEquals(0.06, analysis.getDirectionAdjustedIcMean(), 0.000001);
        assertEquals(0.70, analysis.getFavorableIcRatio(), 0.000001);
        assertEquals("DIRECTIONALLY_ALIGNED", analysis.getSampleEvidence());
        assertEquals("SUPPORTED", analysis.getConclusion());
        assertTrue(analysis.isValidationEligible());
        assertTrue(analysis.getCaveats().stream().anyMatch(value -> value.contains("样本外")));
    }

    @Test
    void marksSmallLearningOrOppositeSamplesWithExplicitBoundaries() {
        FactorAnalysis learning = analysis(0.20, 0.10, 0.90, 100);
        service.assess(learning, "POSITIVE_HYPOTHESIS", "LEARNING_SAMPLE");
        assertEquals("INCONCLUSIVE", learning.getConclusion());
        assertTrue(learning.getCaveats().stream().anyMatch(value -> value.contains("虚拟学习数据")));

        FactorAnalysis small = analysis(0.20, 0.10, 0.90, 8);
        service.assess(small, "POSITIVE_HYPOTHESIS", "REAL");
        assertEquals("INSUFFICIENT_SAMPLE", small.getSampleEvidence());

        FactorAnalysis opposite = analysis(-0.08, 0.15, 0.30, 80);
        service.assess(opposite, "POSITIVE_HYPOTHESIS", "REAL");
        assertEquals("OPPOSED", opposite.getSampleEvidence());
    }

    private FactorAnalysis analysis(double mean, double std, double positiveRatio, int samples) {
        FactorAnalysis value = new FactorAnalysis();
        value.setIcMean(mean); value.setIcStd(std); value.setIcIr(std == 0 ? 0 : mean / std);
        value.setPositiveIcRatio(positiveRatio); value.setNegativeIcRatio(1d - positiveRatio);
        value.setSampleCount(samples); value.setMinCrossSectionSize(20);
        value.setQuantileSampleDays(samples); value.setQuantileSpreadMean(mean < 0 ? -0.01 : 0.01);
        value.setQuantileMonotonicityMean(mean < 0 ? -0.5 : 0.5);
        value.setIcMeanCiLower(mean - 0.01); value.setIcMeanCiUpper(mean + 0.01);
        return value;
    }
}
