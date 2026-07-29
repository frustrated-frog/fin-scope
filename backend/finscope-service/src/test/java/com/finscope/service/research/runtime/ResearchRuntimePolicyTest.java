package com.finscope.service.research.runtime;

import com.finscope.domain.research.ResearchMode;
import com.finscope.domain.research.runtime.ResearchRuntimeCheckpoint;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ResearchRuntimePolicyTest {
    private final ResearchRuntimePolicy policy = new ResearchRuntimePolicy();

    @Test
    void projectsQuickAndDeepRuntimeBudgetsWithoutChangingLegacyDeepDefault() {
        assertEquals(5, policy.maxActions(ResearchMode.QUICK));
        assertEquals(ResearchRuntimeService.DEFAULT_MAX_ACTIONS, policy.maxActions(ResearchMode.DEEP));
    }

    @Test
    void allowsActionWithinAllRuntimeGuards() {
        RuntimeGuardDecision decision = policy.beforeAction(checkpoint(4, 12, 0, "RUNNING"), 0);

        assertTrue(decision.isAllowed());
        assertEquals(null, decision.getTerminationReason());
    }

    @Test
    void stopsWhenActionBudgetIsExhausted() {
        RuntimeGuardDecision decision = policy.beforeAction(checkpoint(12, 12, 0, "RUNNING"), 0);

        assertFalse(decision.isAllowed());
        assertEquals("BUDGET_EXHAUSTED", decision.getTerminationReason());
    }

    @Test
    void stopsAfterTwoConsecutiveNoProgressTransitions() {
        RuntimeGuardDecision decision = policy.beforeAction(checkpoint(4, 12, 2, "RUNNING"), 0);

        assertFalse(decision.isAllowed());
        assertEquals("NO_PROGRESS", decision.getTerminationReason());
    }

    @Test
    void stopsBeforeThirdEquivalentAction() {
        RuntimeGuardDecision decision = policy.beforeAction(checkpoint(4, 12, 0, "RUNNING"), 2);

        assertFalse(decision.isAllowed());
        assertEquals("REPEATED_ACTION", decision.getTerminationReason());
    }

    @Test
    void refusesAlreadyTerminalCheckpoint() {
        RuntimeGuardDecision decision = policy.beforeAction(checkpoint(4, 12, 0, "COMPLETED"), 0);

        assertFalse(decision.isAllowed());
        assertEquals("ALREADY_TERMINAL", decision.getTerminationReason());
    }

    private ResearchRuntimeCheckpoint checkpoint(int consumedActions,
                                                 int maxActions,
                                                 int noProgressCount,
                                                 String status) {
        ResearchRuntimeCheckpoint checkpoint = new ResearchRuntimeCheckpoint();
        checkpoint.setConsumedActions(consumedActions);
        checkpoint.setMaxActions(maxActions);
        checkpoint.setNoProgressCount(noProgressCount);
        checkpoint.setStatus(status);
        return checkpoint;
    }
}
