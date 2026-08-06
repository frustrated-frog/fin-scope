package com.finscope.service.research.agent;

import com.finscope.common.exception.BusinessException;
import com.finscope.common.exception.BizErrorCode;
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
            throw new BusinessException(BizErrorCode.RESEARCH_AGENT_STATE_INPUT_REQUIRED);
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
        state.setEvidenceSummary(evidenceSummary(observation));
        state.setMemorySummary(memory(state.getMemorySummary(), decision, observation));
        if (!repository.updateState(state, expectedVersion)) {
            throw new BusinessException(BizErrorCode.RESEARCH_AGENT_STATE_CONFLICT);
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
            throw new BusinessException(BizErrorCode.RESEARCH_AGENT_REPLAN_CONFLICT);
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
            throw new BusinessException(BizErrorCode.RESEARCH_AGENT_VERIFY_CONFLICT);
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
            throw new BusinessException(BizErrorCode.RESEARCH_AGENT_TERMINATE_CONFLICT);
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

    private String evidenceSummary(ResearchToolObservation observation) {
        int evidenceDelta = Math.max(0, observation.getEvidenceDelta());
        int sourceDelta = Math.max(0, observation.getSourceDelta());
        if ("RETRYABLE_ERROR".equals(observation.getStatus())) {
            int retries = Math.max(0, observation.getAttemptCount() - 1);
            return retries > 0
                    ? "工具自动重试 " + retries + " 次后仍未恢复；本轮没有写入新证据。"
                    : "工具遇到可恢复错误；本轮没有写入新证据。";
        }
        if ("TERMINAL_ERROR".equals(observation.getStatus()) || "FAILED".equals(observation.getStatus())) {
            return "工具执行失败且不可自动恢复；本轮没有写入新证据。";
        }
        if (evidenceDelta == 0 && sourceDelta == 0) {
            return "本轮没有获得新增证据或独立来源；建议调整查询角度。";
        }
        return "本轮新增 " + evidenceDelta + " 条证据，覆盖 " + sourceDelta
                + " 个新来源；证据账本已更新。";
    }

    private boolean hasText(String value) { return value != null && !value.trim().isEmpty(); }
    private String safe(String value) { return value == null ? "" : value.replaceAll("[\\r\\n\\t]+", " ").trim(); }
    private String limit(String value, int max) {
        return value.length() <= max ? value : value.substring(value.length() - max);
    }
}
