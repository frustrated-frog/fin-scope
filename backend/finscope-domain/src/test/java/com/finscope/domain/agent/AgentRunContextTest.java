package com.finscope.domain.agent;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentRunContextTest {
    @Test
    void recordsRepeatedActionsWithWarningAndHardThresholds() {
        AgentBudgetPolicy policy = AgentBudgetPolicy.defaults();
        AgentRunContext context = AgentRunContext.start(99L, policy);
        AgentActionFingerprint fingerprint = AgentActionFingerprint.of(
                "source-fetch", "source", "12", "source-fetch:source:12", "");

        AgentRunContext.ActionRecord first = context.recordAction(fingerprint);
        AgentRunContext.ActionRecord second = context.recordAction(fingerprint);
        AgentRunContext.ActionRecord third = context.recordAction(fingerprint);

        assertEquals(1, first.getCount());
        assertFalse(first.isWarnThresholdReached());
        assertFalse(first.isHardThresholdReached());

        assertEquals(2, second.getCount());
        assertTrue(second.isWarnThresholdReached());
        assertFalse(second.isHardThresholdReached());

        assertEquals(3, third.getCount());
        assertTrue(third.isWarnThresholdReached());
        assertTrue(third.isHardThresholdReached());
        assertEquals(1, context.getWarningCount());
    }

    @Test
    void tracksLlmAndNodeBudgetUsage() {
        AgentBudgetPolicy policy = AgentBudgetPolicy.defaults();
        AgentRunContext context = AgentRunContext.start(100L, policy);

        context.enterNode("article-interpret");
        context.recordLlmCall();

        assertEquals(100L, context.getResearchRunId());
        assertEquals("article-interpret", context.getCurrentNodeName());
        assertEquals(1, context.getNodeCount());
        assertEquals(1, context.getLlmCallCount());
        assertFalse(context.isNodeBudgetExceeded());
        assertFalse(context.isLlmBudgetExceeded());
    }
}
