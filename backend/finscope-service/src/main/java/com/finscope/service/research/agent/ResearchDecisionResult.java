package com.finscope.service.research.agent;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.finscope.domain.research.agent.ResearchAgentDecision;

import java.util.Collections;
import java.util.Map;

public class ResearchDecisionResult {
    private static final ObjectMapper JSON = new ObjectMapper();
    private final ResearchAgentDecision decision;
    private final String fallbackReason;
    private final String fallbackDetail;

    public ResearchDecisionResult(ResearchAgentDecision decision,
                                  String fallbackReason,
                                  String fallbackDetail) {
        this.decision = decision;
        this.fallbackReason = fallbackReason;
        this.fallbackDetail = fallbackDetail;
    }

    public ResearchAgentDecision getDecision() { return decision; }
    public String getFallbackReason() { return fallbackReason; }
    public String getFallbackDetail() { return fallbackDetail; }

    public Map<String, Object> getArguments() {
        if (decision == null || decision.getArgumentsJson() == null || decision.getArgumentsJson().trim().isEmpty()) {
            return Collections.emptyMap();
        }
        try {
            return JSON.readValue(decision.getArgumentsJson(), new TypeReference<Map<String, Object>>() { });
        } catch (Exception error) {
            throw new IllegalStateException("Persisted research decision arguments are invalid", error);
        }
    }
}
