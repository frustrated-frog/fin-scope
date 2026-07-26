package com.finscope.service.research.agent;

import com.finscope.domain.research.agent.ResearchAgentDecision;
import com.finscope.domain.research.agent.ResearchAgentTraceView;
import com.finscope.domain.research.agent.ResearchAgentTrajectoryMetrics;
import com.finscope.domain.research.agent.ResearchToolObservation;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ResearchTrajectoryEvaluatorTest {
    @Test
    void computesDeterministicTrajectoryRatesFromPersistedTrace() {
        ResearchAgentTraceView trace = new ResearchAgentTraceView();
        trace.setDecisions(Arrays.asList(
                decision(1L, 1, "TOOL_CALL", "public_news_search", "fp-1", "MODEL", "COMPLETED"),
                decision(2L, 2, "TOOL_CALL", "public_news_search", "fp-1", "DETERMINISTIC", "COMPLETED"),
                decision(3L, 3, "PLAN_PATCH", null, null, "MODEL", "COMPLETED"),
                decision(4L, 4, "FINISH", null, null, "MODEL", "COMPLETED")));
        trace.setObservations(Arrays.asList(
                observation(1L, "NO_PROGRESS"),
                observation(2L, "SUCCESS")));

        ResearchAgentTrajectoryMetrics metrics = new ResearchTrajectoryEvaluator().evaluate(trace);

        assertEquals(4, metrics.getDecisionCount());
        assertEquals(2, metrics.getObservationCount());
        assertEquals(1D, metrics.getDecisionValidityRate(), 0.001D);
        assertEquals(1D, metrics.getObservationFollowupRate(), 0.001D);
        assertEquals(0.5D, metrics.getDuplicateActionRate(), 0.001D);
        assertEquals(0.5D, metrics.getNoProgressRate(), 0.001D);
        assertEquals(1D, metrics.getReplanSuccessRate(), 0.001D);
        assertEquals(1D, metrics.getFinishFirstPassRate(), 0.001D);
        assertEquals(0.25D, metrics.getFallbackRate(), 0.001D);
        assertEquals(75, metrics.getQualityScore());
    }

    @Test
    void treatsSuccessfulToolObservationAfterPlanPatchAsEffectiveReplan() {
        ResearchAgentTraceView trace = new ResearchAgentTraceView();
        trace.setDecisions(Arrays.asList(
                decision(1L, 1, "PLAN_PATCH", null, null, "MODEL", "COMPLETED"),
                decision(2L, 2, "TOOL_CALL", "public_news_search", "fp-2", "MODEL", "COMPLETED")));
        trace.setObservations(Arrays.asList(observation(2L, "SUCCESS")));

        ResearchAgentTrajectoryMetrics metrics = new ResearchTrajectoryEvaluator().evaluate(trace);

        assertEquals(1D, metrics.getReplanSuccessRate(), 0.001D);
    }

    private ResearchAgentDecision decision(Long id,
                                           int iteration,
                                           String type,
                                           String tool,
                                           String fingerprint,
                                           String mode,
                                           String status) {
        ResearchAgentDecision value = new ResearchAgentDecision();
        value.setId(id);
        value.setIteration(iteration);
        value.setDecisionType(type);
        value.setToolCode(tool);
        value.setActionFingerprint(fingerprint);
        value.setDecisionMode(mode);
        value.setStatus(status);
        return value;
    }

    private ResearchToolObservation observation(Long decisionId, String status) {
        ResearchToolObservation value = new ResearchToolObservation();
        value.setDecisionId(decisionId);
        value.setStatus(status);
        return value;
    }
}
