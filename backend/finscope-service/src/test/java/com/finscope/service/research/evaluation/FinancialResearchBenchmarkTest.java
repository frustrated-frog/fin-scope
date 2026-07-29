package com.finscope.service.research.evaluation;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.finscope.service.research.report.ResearchClaimAuditor;
import com.finscope.service.research.report.ResearchClaimExtractor;
import org.junit.jupiter.api.Test;

import java.io.InputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class FinancialResearchBenchmarkTest {

    @Test
    void evaluatesFrozenGroundingCaseWithDeterministicMetricsAndSerialization() throws Exception {
        FinancialResearchBenchmark benchmark = new FinancialResearchBenchmark(new ObjectMapper(),
                new ResearchClaimAuditor(new ResearchClaimExtractor()));

        ResearchBenchmarkRun first;
        try (InputStream input = resource()) {
            first = benchmark.run(input);
        }
        ResearchBenchmarkRun second;
        try (InputStream input = resource()) {
            second = benchmark.run(input);
        }

        assertEquals(1, first.getCases().size());
        ResearchGroundingMetrics metrics = first.getCases().get(0).getMetrics();
        assertEquals(1D, metrics.getCitationCoverageRate());
        assertEquals(1D, metrics.getClaimSupportRate());
        assertEquals(1D, metrics.getKeyFactCoverageRate());
        assertEquals(0.5D, metrics.getPrimarySourceRatio());
        assertEquals(1D, metrics.getCounterEvidenceCoverage());
        assertEquals(1D, metrics.getCitationAccessibilityRate());
        assertEquals(1D, metrics.getFreshnessRate());
        assertEquals(first.toCanonicalJson(), second.toCanonicalJson());
    }

    private InputStream resource() {
        InputStream input = getClass().getResourceAsStream("/research-benchmark/grounding-cases.json");
        assertNotNull(input);
        return input;
    }
}
