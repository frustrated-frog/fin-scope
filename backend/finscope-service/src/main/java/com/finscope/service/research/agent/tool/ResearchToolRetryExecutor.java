package com.finscope.service.research.agent.tool;

import com.finscope.domain.research.agent.ResearchAgentDecision;
import com.finscope.domain.research.agent.ResearchToolObservation;
import org.springframework.stereotype.Component;

@Component
public class ResearchToolRetryExecutor {
    static final int MAX_ATTEMPTS = 2;

    private final ResearchToolDispatcher dispatcher;

    public ResearchToolRetryExecutor(ResearchToolDispatcher dispatcher) {
        this.dispatcher = dispatcher;
    }

    public ResearchToolExecutionResult execute(ResearchAgentDecision decision,
                                               ResearchAgentToolContext context) {
        ResearchToolObservation observation = null;
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            observation = dispatcher.dispatch(decision, context);
            if (!isRetryable(observation)) {
                if (attempt > 1) {
                    observation.setObservationSummary("第 " + attempt + " 次尝试恢复成功："
                            + safe(observation.getObservationSummary()));
                }
                return new ResearchToolExecutionResult(observation, attempt);
            }
        }
        observation.setObservationSummary("自动重试 1 次后仍失败："
                + safe(observation.getObservationSummary()));
        return new ResearchToolExecutionResult(observation, MAX_ATTEMPTS);
    }

    private boolean isRetryable(ResearchToolObservation observation) {
        return observation != null && observation.isRetryable()
                && "RETRYABLE_ERROR".equals(observation.getStatus());
    }

    private String safe(String value) {
        return value == null || value.trim().isEmpty() ? "工具没有返回错误摘要" : value.trim();
    }
}
