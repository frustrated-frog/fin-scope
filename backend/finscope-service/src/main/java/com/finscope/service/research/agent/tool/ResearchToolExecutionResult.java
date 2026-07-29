package com.finscope.service.research.agent.tool;

import com.finscope.domain.research.agent.ResearchToolObservation;

public final class ResearchToolExecutionResult {
    private final ResearchToolObservation observation;
    private final int attemptCount;

    ResearchToolExecutionResult(ResearchToolObservation observation, int attemptCount) {
        this.observation = observation;
        this.attemptCount = attemptCount;
    }

    public ResearchToolObservation getObservation() {
        return observation;
    }

    public int getAttemptCount() {
        return attemptCount;
    }
}
