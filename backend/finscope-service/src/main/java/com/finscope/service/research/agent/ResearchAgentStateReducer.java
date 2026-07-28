package com.finscope.service.research.agent;

import com.finscope.dao.research.agent.ResearchAgentRepository;
import com.finscope.domain.research.agent.ResearchAgentDecision;
import com.finscope.domain.research.agent.ResearchAgentState;
import com.finscope.domain.research.agent.ResearchToolObservation;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class ResearchAgentStateReducer {
    private static final int MAX_MEMORY_CHARACTERS = 1_600;
    private final ResearchAgentRepository repository;

    public ResearchAgentStateReducer(ResearchAgentRepository repository) {
        this.repository = repository;
    }

    public ResearchAgentState reduceAndPersist(ResearchAgentState state,
                                               ResearchAgentDecision decision,
                                               ResearchToolObservation observation) {
        if (state == null || decision == null || observation == null) {
            throw new IllegalArgumentException("状态、决策和 Observation 不能为空");
        }
        int expectedVersion = state.getStateVersion();
        state.setStatus("DECIDING");
        state.setCurrentSubgoal(decision.getCurrentSubgoal());
        state.setDecisionCount(Math.max(state.getDecisionCount(), decision.getIteration()));
        state.setLastObservationId(observation.getId());
        if (hasText(decision.getActionFingerprint())) {
            List<String> attempted = new ArrayList<String>(state.getAttemptedFingerprints());
            if (!attempted.contains(decision.getActionFingerprint())) {
                attempted.add(decision.getActionFingerprint());
            }
            state.setAttemptedFingerprints(attempted);
        }
        if ("NO_PROGRESS".equals(observation.getStatus())) {
            state.setNoProgressCount(state.getNoProgressCount() + 1);
        } else if (observation.getEvidenceDelta() > 0 || observation.getSourceDelta() > 0) {
            state.setNoProgressCount(0);
            state.setFinishRejectionCount(0);
        }
        if ("DETERMINISTIC".equals(decision.getDecisionMode())) {
            state.setFallbackCount(state.getFallbackCount() + 1);
        }
        state.setEvidenceSummary("state=" + safe(observation.getStateHash())
                + ", evidenceDelta=" + observation.getEvidenceDelta()
                + ", sourceDelta=" + observation.getSourceDelta()
                + ", status=" + observation.getStatus());
        state.setMemorySummary(memory(state.getMemorySummary(), decision, observation));
        if (!repository.updateState(state, expectedVersion)) {
            throw new IllegalStateException("研究 Agent 状态发生并发更新，请从最新检查点恢复");
        }
        return state;
    }

    public ResearchAgentState recordPlanPatch(ResearchAgentState state, ResearchAgentDecision decision) {
        int expectedVersion = state.getStateVersion();
        state.setStatus("DECIDING");
        state.setCurrentSubgoal(decision.getCurrentSubgoal());
        state.setDecisionCount(Math.max(state.getDecisionCount(), decision.getIteration()));
        state.setReplanCount(state.getReplanCount() + 1);
        state.setMemorySummary(limit(safe(state.getMemorySummary()) + " | Replan："
                + safe(decision.getDecisionSummary()), MAX_MEMORY_CHARACTERS));
        if (!repository.updateState(state, expectedVersion)) {
            throw new IllegalStateException("研究 Agent 重规划状态发生并发更新");
        }
        return state;
    }

    public ResearchAgentState recordFinishVerdict(ResearchAgentState state,
                                                  ResearchAgentDecision decision,
                                                  ResearchFinishVerdict verdict) {
        int expectedVersion = state.getStateVersion();
        state.setDecisionCount(Math.max(state.getDecisionCount(), decision.getIteration()));
        state.setStatus(verdict.isAccepted() ? "COMPLETED" : "DECIDING");
        if (!verdict.isAccepted()) {
            state.setFinishRejectionCount(state.getFinishRejectionCount() + 1);
        }
        state.setMemorySummary(limit(safe(state.getMemorySummary()) + " | Finish "
                + verdict.getReasonCode() + "：" + verdict.getMissingConditions(), MAX_MEMORY_CHARACTERS));
        if (!repository.updateState(state, expectedVersion)) {
            throw new IllegalStateException("研究 Agent 完成校验状态发生并发更新");
        }
        return state;
    }

    public ResearchAgentState recordAbort(ResearchAgentState state, ResearchAgentDecision decision) {
        int expectedVersion = state.getStateVersion();
        state.setDecisionCount(Math.max(state.getDecisionCount(), decision.getIteration()));
        state.setStatus("FAILED");
        if ("DETERMINISTIC".equals(decision.getDecisionMode())) {
            state.setFallbackCount(state.getFallbackCount() + 1);
        }
        state.setMemorySummary(limit(safe(state.getMemorySummary()) + " | Abort："
                + safe(decision.getDecisionSummary()), MAX_MEMORY_CHARACTERS));
        if (!repository.updateState(state, expectedVersion)) {
            throw new IllegalStateException("研究 Agent 终止状态发生并发更新");
        }
        return state;
    }

    private String memory(String previous,
                          ResearchAgentDecision decision,
                          ResearchToolObservation observation) {
        String value = safe(previous) + " | #" + decision.getIteration() + " "
                + safe(decision.getDecisionSummary()) + " -> " + safe(observation.getObservationSummary());
        return limit(value, MAX_MEMORY_CHARACTERS);
    }

    private boolean hasText(String value) { return value != null && !value.trim().isEmpty(); }
    private String safe(String value) { return value == null ? "" : value.replaceAll("[\\r\\n\\t]+", " ").trim(); }
    private String limit(String value, int max) {
        return value.length() <= max ? value : value.substring(value.length() - max);
    }
}
