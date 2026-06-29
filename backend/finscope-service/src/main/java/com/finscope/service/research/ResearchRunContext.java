package com.finscope.service.research;

public final class ResearchRunContext {
    private static final ThreadLocal<Long> CURRENT_RUN_ID = new ThreadLocal<Long>();

    private ResearchRunContext() {
    }

    public static Long currentRunId() {
        return CURRENT_RUN_ID.get();
    }

    public static void setCurrentRunId(Long runId) {
        CURRENT_RUN_ID.set(runId);
    }

    public static void clear() {
        CURRENT_RUN_ID.remove();
    }
}
