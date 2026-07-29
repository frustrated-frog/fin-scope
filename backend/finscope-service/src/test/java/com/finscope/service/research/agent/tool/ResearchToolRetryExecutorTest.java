package com.finscope.service.research.agent.tool;

import com.finscope.domain.research.agent.ResearchAgentDecision;
import com.finscope.domain.research.agent.ResearchToolObservation;
import com.finscope.domain.research.mission.ResearchToolDescriptor;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ResearchToolRetryExecutorTest {

    @Test
    void retriesOneRetryableFailureAndReturnsRecoveredObservation() {
        AtomicInteger calls = new AtomicInteger();
        ResearchToolRetryExecutor executor = executor((context, arguments) -> {
            if (calls.incrementAndGet() == 1) {
                return observation("RETRYABLE_ERROR", true, "搜索服务暂时不可用");
            }
            return observation("SUCCESS", false, "新增一手证据");
        });

        ResearchToolExecutionResult result = executor.execute(decision(), new ResearchAgentToolContext(7L, 9L));

        assertEquals(2, result.getAttemptCount());
        assertEquals("SUCCESS", result.getObservation().getStatus());
        assertTrue(result.getObservation().getObservationSummary().contains("第 2 次尝试恢复成功"));
        assertEquals(2, calls.get());
    }

    @Test
    void doesNotRetryTerminalFailure() {
        AtomicInteger calls = new AtomicInteger();
        ResearchToolRetryExecutor executor = executor((context, arguments) -> {
            calls.incrementAndGet();
            return observation("TERMINAL_ERROR", false, "参数不受支持");
        });

        ResearchToolExecutionResult result = executor.execute(decision(), new ResearchAgentToolContext(7L, 9L));

        assertEquals(1, result.getAttemptCount());
        assertEquals("TERMINAL_ERROR", result.getObservation().getStatus());
        assertEquals(1, calls.get());
    }

    @Test
    void stopsAfterBoundedRetryIsExhausted() {
        AtomicInteger calls = new AtomicInteger();
        ResearchToolRetryExecutor executor = executor((context, arguments) -> {
            calls.incrementAndGet();
            return observation("RETRYABLE_ERROR", true, "上游持续超时");
        });

        ResearchToolExecutionResult result = executor.execute(decision(), new ResearchAgentToolContext(7L, 9L));

        assertEquals(2, result.getAttemptCount());
        assertEquals("RETRYABLE_ERROR", result.getObservation().getStatus());
        assertTrue(result.getObservation().getObservationSummary().contains("自动重试 1 次后仍失败"));
        assertEquals(2, calls.get());
    }

    private ResearchToolRetryExecutor executor(ToolBehavior behavior) {
        ResearchAgentTool tool = new ResearchAgentTool() {
            @Override
            public ResearchToolDescriptor descriptor() {
                ResearchToolDescriptor descriptor = new ResearchToolDescriptor();
                descriptor.setCode("public_news_search");
                return descriptor;
            }

            @Override
            public void validate(Map<String, Object> arguments) {
            }

            @Override
            public ResearchToolObservation execute(ResearchAgentToolContext context, Map<String, Object> arguments) {
                return behavior.execute(context, arguments);
            }
        };
        ResearchAgentToolRegistry registry = new ResearchAgentToolRegistry(
                Collections.<ResearchAgentTool>singletonList(tool));
        return new ResearchToolRetryExecutor(new ResearchToolDispatcher(registry));
    }

    private ResearchAgentDecision decision() {
        ResearchAgentDecision decision = new ResearchAgentDecision();
        decision.setDecisionType("TOOL_CALL");
        decision.setToolCode("public_news_search");
        decision.setArgumentsJson("{}");
        return decision;
    }

    private ResearchToolObservation observation(String status, boolean retryable, String summary) {
        ResearchToolObservation observation = new ResearchToolObservation();
        observation.setStatus(status);
        observation.setRetryable(retryable);
        observation.setObservationSummary(summary);
        observation.setStateHash("state");
        return observation;
    }

    private interface ToolBehavior {
        ResearchToolObservation execute(ResearchAgentToolContext context, Map<String, Object> arguments);
    }
}
