package com.finscope.service.research.agent;

import com.finscope.dao.research.agent.ResearchAgentRepository;
import com.finscope.domain.research.agent.ResearchAgentDecision;
import com.finscope.domain.research.agent.ResearchAgentState;
import com.finscope.domain.research.agent.ResearchToolObservation;
import com.finscope.service.research.runtime.ResearchRuntimeService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ResearchAgentTurnService {
    private final ResearchAgentRepository repository;
    private final ResearchAgentStateReducer reducer;
    private final ResearchRuntimeService runtimeService;

    public ResearchAgentTurnService(ResearchAgentRepository repository,
                                    ResearchAgentStateReducer reducer,
                                    ResearchRuntimeService runtimeService) {
        this.repository = repository;
        this.reducer = reducer;
        this.runtimeService = runtimeService;
    }

    @Transactional
    public ResearchAgentState commitToolTurn(Long runId,
                                             String nodeId,
                                             boolean runtimeStarted,
                                             ResearchAgentState state,
                                             ResearchAgentDecision decision,
                                             ResearchToolObservation observation) {
        repository.appendObservation(observation);
        boolean failed = isFailure(observation);
        if (runtimeStarted) {
            if (failed) {
                runtimeService.failNode(runId, nodeId, safe(observation.getErrorType()),
                        safe(observation.getObservationSummary()));
            } else {
                runtimeService.completeNode(runId, nodeId, observation.getStateHash(),
                        observation.getEvidenceDelta() + observation.getSourceDelta(),
                        observation.getObservationSummary());
            }
        }
        repository.updateDecisionStatus(decision.getId(), failed ? "FAILED" : "COMPLETED",
                observation.getErrorType() == null ? decision.getValidationError() : observation.getErrorType());
        ResearchAgentState reduced = reducer.reduceAndPersist(state, decision, observation);
        if (failed) {
            reducer.recordAbort(reduced, decision);
        }
        return reduced;
    }

    private boolean isFailure(ResearchToolObservation observation) {
        return "TERMINAL_ERROR".equals(observation.getStatus())
                || "RETRYABLE_ERROR".equals(observation.getStatus());
    }

    private String safe(String value) {
        return value == null || value.trim().isEmpty() ? "TOOL_EXECUTION_FAILED" : value.trim();
    }
}
