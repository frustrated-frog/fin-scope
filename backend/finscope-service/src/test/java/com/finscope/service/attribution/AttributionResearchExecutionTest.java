package com.finscope.service.attribution;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AttributionResearchExecutionTest {
    @Test
    void successfulEmptySearchIsPartialRatherThanCompleted() {
        AttributionResearchExecution.TrackResult result =
                new AttributionResearchExecution().track("COMPANY");
        result.attempted();
        result.succeeded();

        assertEquals("PARTIAL", result.status());
        result.foundEvidence();
        assertEquals("COMPLETED", result.status());
    }
}
