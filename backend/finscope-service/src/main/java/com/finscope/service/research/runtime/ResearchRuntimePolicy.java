package com.finscope.service.research.runtime;

import com.finscope.domain.research.runtime.ResearchRuntimeCheckpoint;
import org.springframework.stereotype.Component;

@Component
public class ResearchRuntimePolicy {
    static final int MAX_NO_PROGRESS = 2;
    static final int MAX_REPEATED_ACTIONS = 2;

    public RuntimeGuardDecision beforeAction(ResearchRuntimeCheckpoint state, int repeatedCount) {
        if (state == null || state.isTerminal()) {
            return RuntimeGuardDecision.terminated("ALREADY_TERMINAL");
        }
        if (state.getConsumedActions() >= state.getMaxActions()) {
            return RuntimeGuardDecision.terminated("BUDGET_EXHAUSTED");
        }
        if (state.getNoProgressCount() >= MAX_NO_PROGRESS) {
            return RuntimeGuardDecision.terminated("NO_PROGRESS");
        }
        if (repeatedCount >= MAX_REPEATED_ACTIONS) {
            return RuntimeGuardDecision.terminated("REPEATED_ACTION");
        }
        return RuntimeGuardDecision.allowed();
    }
}
