package com.finscope.service.attribution;

import com.finscope.domain.attribution.AttributionResearchRun;
import com.finscope.domain.attribution.AttributionResearchStep;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AttributionResearchRunViewTest {
    @Test
    void summarizesOnlyActivatedAndSettledTracksAsRealProgress() {
        AttributionResearchRun run = new AttributionResearchRun();
        run.setCurrentStep("web-search");
        AttributionService.AttributionResearchRunView view = new AttributionService.AttributionResearchRunView(
                run, Arrays.asList(
                        step("company", "COMPANY", "PLANNED"),
                        step("industry", "INDUSTRY", "RUNNING"),
                        step("macro", "MACRO", "COMPLETED"),
                        step("market", "MARKET", "PENDING")));

        AttributionService.AttributionResearchProgress progress = view.getProgress();
        assertEquals(4, progress.getPlannedTracks());
        assertEquals(2, progress.getActivatedTracks());
        assertEquals(1, progress.getSettledTracks());
        assertEquals("INDUSTRY", progress.getCurrentTrack());
        assertEquals("web-search", progress.getCurrentStep());
    }

    private AttributionResearchStep step(String stepId, String track, String status) {
        AttributionResearchStep step = new AttributionResearchStep();
        step.setStepId(stepId);
        step.setTrack(track);
        step.setStatus(status);
        return step;
    }
}
