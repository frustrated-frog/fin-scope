package com.finscope.service.agent;

import com.finscope.domain.agent.AgentActionFingerprint;
import com.finscope.domain.agent.AgentBudgetPolicy;
import com.finscope.domain.agent.AgentNodeResult;
import com.finscope.domain.agent.AgentRunContext;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AgentHarnessTest {
    private final AgentHarness harness = new AgentHarness();

    @Test
    void skipsNodeWhenRepeatedActionReachesHardThreshold() {
        AgentRunContext context = AgentRunContext.start(101L, AgentBudgetPolicy.defaults());
        AgentActionFingerprint fingerprint = AgentActionFingerprint.of(
                "source-fetch", "source", "12", "source-fetch:source:12", "");
        AtomicInteger executions = new AtomicInteger(0);

        AgentNodeResult<String> first = harness.runNode(context, fingerprint,
                ctx -> {
                    executions.incrementAndGet();
                    return AgentNodeResult.success("ok", "source=12", "fetched", 1);
                });
        AgentNodeResult<String> second = harness.runNode(context, fingerprint,
                ctx -> {
                    executions.incrementAndGet();
                    return AgentNodeResult.success("ok", "source=12", "fetched", 1);
                });
        AgentNodeResult<String> third = harness.runNode(context, fingerprint,
                ctx -> {
                    executions.incrementAndGet();
                    return AgentNodeResult.success("ok", "source=12", "fetched", 1);
                });

        assertEquals("SUCCESS", first.getStatus());
        assertEquals("SUCCESS", second.getStatus());
        assertEquals("SKIPPED", third.getStatus());
        assertEquals("REPEATED_ACTION", third.getErrorType());
        assertEquals(2, executions.get());
        assertEquals(3, context.getNodeCount());
    }

    @Test
    void convertsUnexpectedNodeExceptionToFailedResult() {
        AgentRunContext context = AgentRunContext.start(102L, AgentBudgetPolicy.defaults());
        AgentActionFingerprint fingerprint = AgentActionFingerprint.of(
                "article-interpret", "article", "345", "article-interpret:article:345:abc", "abc");

        AgentNodeResult<String> result = harness.runNode(context, fingerprint,
                ctx -> {
                    throw new IllegalStateException("model unavailable");
                });

        assertEquals("FAILED", result.getStatus());
        assertEquals("UNKNOWN", result.getErrorType());
        assertEquals("model unavailable", result.getErrorMessage());
        assertEquals("article-interpret", context.getCurrentNodeName());
    }
}
