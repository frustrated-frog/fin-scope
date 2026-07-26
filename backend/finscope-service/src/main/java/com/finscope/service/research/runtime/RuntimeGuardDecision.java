package com.finscope.service.research.runtime;

public final class RuntimeGuardDecision {
    private final boolean allowed;
    private final String terminationReason;

    private RuntimeGuardDecision(boolean allowed, String terminationReason) {
        this.allowed = allowed;
        this.terminationReason = terminationReason;
    }

    public static RuntimeGuardDecision allowed() {
        return new RuntimeGuardDecision(true, null);
    }

    public static RuntimeGuardDecision terminated(String reason) {
        return new RuntimeGuardDecision(false, reason);
    }

    public boolean isAllowed() { return allowed; }
    public String getTerminationReason() { return terminationReason; }
}
