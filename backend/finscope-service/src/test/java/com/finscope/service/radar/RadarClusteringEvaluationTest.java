package com.finscope.service.radar;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.finscope.domain.radar.RadarSignal;
import com.finscope.service.dedupe.FingerprintService;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RadarClusteringEvaluationTest {
    @Test
    void keepsFalseMergesAtZeroAndPositiveRecallAboveBaseline() throws Exception {
        InputStream input = getClass().getResourceAsStream("/radar/clustering-cases.json");
        List<Case> cases = new ObjectMapper().readValue(input, new TypeReference<List<Case>>() {});
        RadarClusteringService service = new RadarClusteringService(new RadarTextAnalyzer(new FingerprintService()));
        int truePositive = 0, falsePositive = 0, falseNegative = 0, positive = 0;
        StringBuilder failures = new StringBuilder();

        for (Case sample : cases) {
            boolean actual = "SAME".equals(service.decide(signal(sample.leftTitle, sample.category),
                    signal(sample.rightTitle, sample.category)).getReasonCode());
            if (sample.expectedSame) positive++;
            if (sample.expectedSame && actual) truePositive++;
            if (!sample.expectedSame && actual) falsePositive++;
            if (sample.expectedSame && !actual) falseNegative++;
            if (sample.expectedSame != actual) failures.append("\n").append(sample.leftTitle).append(" <> ")
                    .append(sample.rightTitle).append("（").append(sample.reason).append("）");
        }

        assertEquals(0, falsePositive, "发现误合并样本：" + failures);
        assertTrue(positive > 0 && truePositive * 1.0 / positive >= 0.80,
                "正样本召回率低于 80%，FN=" + falseNegative + failures);
    }

    private RadarSignal signal(String title, String category) {
        RadarSignal signal = new RadarSignal(); signal.setTitle(title); signal.setContent(title); signal.setCategoryCode(category); return signal;
    }

    public static final class Case {
        public String category;
        public String leftTitle;
        public String rightTitle;
        public boolean expectedSame;
        public String reason;
    }
}
