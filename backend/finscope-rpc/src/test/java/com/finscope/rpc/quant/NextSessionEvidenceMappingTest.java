package com.finscope.rpc.quant;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.finscope.domain.quant.forecast.NextSessionPrediction;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class NextSessionEvidenceMappingTest {
    @Test
    void preservesDirectionAndAdaptiveEvidenceWithoutChangingProbability() throws Exception {
        String payload = "{\"upProbability\":0.54,\"directionEvaluation\":{\"balancedAccuracy\":0.51},"
                + "\"jointModel\":{\"adaptationEvidence\":{\"periodCount\":4},"
                + "\"directionEvaluation\":{\"dayCount\":60}}}";
        NextSessionPrediction result = new ObjectMapper().readValue(payload, NextSessionPrediction.class);
        assertEquals(0.54d, result.getUpProbability());
        assertEquals(0.51d, result.getDirectionEvaluation().get("balancedAccuracy"));
        assertEquals(4, result.getJointModel().getAdaptationEvidence().get("periodCount"));
        assertEquals(60, result.getJointModel().getDirectionEvaluation().get("dayCount"));
    }
}
