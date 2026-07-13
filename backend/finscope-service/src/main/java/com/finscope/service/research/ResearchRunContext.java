package com.finscope.service.research;

import com.finscope.domain.agent.AgentBudgetPolicy;
import com.finscope.domain.agent.AgentRunContext;

public final class ResearchRunContext {
    private static final ThreadLocal<AgentRunContext> CURRENT_CONTEXT = new ThreadLocal<AgentRunContext>();

    private ResearchRunContext() {
    }

    public static Long currentRunId() {
        AgentRunContext context = CURRENT_CONTEXT.get();
        return context == null ? null : context.getResearchRunId();
    }

    public static AgentRunContext currentContext() {
        return CURRENT_CONTEXT.get();
    }

    public static boolean isBatchResearch() {
        return currentRunId() != null;
    }

    public static void setCurrentRunId(Long runId) {
        CURRENT_CONTEXT.set(AgentRunContext.start(runId, AgentBudgetPolicy.defaults()));
    }

    public static void setCurrentContext(AgentRunContext context) {
        CURRENT_CONTEXT.set(context);
    }

    public static void clear() {
        CURRENT_CONTEXT.remove();
    }
}
