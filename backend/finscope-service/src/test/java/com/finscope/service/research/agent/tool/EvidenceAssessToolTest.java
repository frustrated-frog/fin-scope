package com.finscope.service.research.agent.tool;

import com.finscope.domain.research.agent.ResearchToolObservation;
import com.finscope.domain.research.mission.ResearchMissionGap;
import com.finscope.service.research.mission.ResearchMissionService;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class EvidenceAssessToolTest {
    @Test
    void turnsPersistedEvidenceGapIntoStructuredObservation() {
        ResearchMissionService missions = mock(ResearchMissionService.class);
        ResearchMissionGap gap = new ResearchMissionGap();
        gap.setEvidenceCount(6);
        gap.setSourceCount(3);
        gap.setSupportCount(6);
        gap.setCounterCount(0);
        gap.setSufficient(false);
        gap.setRecommendedIntent("COUNTER");
        gap.setWarnings(Arrays.asList("缺少反方证据"));
        gap.setStateHash("6:3:6:0");
        when(missions.assess(31L, "agent_decision_8")).thenReturn(gap);

        ResearchToolObservation observation = new EvidenceAssessTool(missions).execute(
                new ResearchAgentToolContext(31L, 8L), Collections.<String, Object>emptyMap());

        assertEquals("SUCCESS", observation.getStatus());
        assertEquals("6:3:6:0", observation.getStateHash());
        assertTrue(observation.getObservationSummary().contains("建议下一意图=COUNTER"));
        assertTrue(observation.getNewInformation().contains("缺少反方证据"));
    }
}
