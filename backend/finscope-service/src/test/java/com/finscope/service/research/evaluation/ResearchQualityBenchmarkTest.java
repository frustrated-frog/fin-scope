package com.finscope.service.research.evaluation;

import com.finscope.service.research.quality.*;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class ResearchQualityBenchmarkTest {
    @Test
    void passesFrozenAdversarialCasesWithoutFalseFactAcceptance() {
        ResearchQualityEngine engine = new ResearchQualityEngine();
        ReflectionTestUtils.setField(engine, "verifier", new ResearchFactVerifier());
        ReflectionTestUtils.setField(engine, "lineage", new ResearchSourceLineage());
        ResearchQualityBenchmark benchmark = new ResearchQualityBenchmark();
        ReflectionTestUtils.setField(benchmark, "engine", engine);
        ReflectionTestUtils.setField(benchmark, "contractBuilder", new ResearchTaskContractBuilder());
        List<ResearchQualityBenchmark.Result> results = benchmark.run(getClass().getResourceAsStream("/research/task-quality-benchmark.json"));
        assertEquals(11, results.size());
        for (ResearchQualityBenchmark.Result result : results) {
            assertTrue(result.isPassed(), result.toString());
            assertEquals(0D, result.getFalseFactAcceptanceRate(), result.getId());
        }
    }
}
