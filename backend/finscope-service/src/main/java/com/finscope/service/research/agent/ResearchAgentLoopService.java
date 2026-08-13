package com.finscope.service.research.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.finscope.dao.research.agent.ResearchAgentRepository;
import com.finscope.domain.research.agent.ResearchAgentDecision;
import com.finscope.common.enums.research.ResearchMode;
import com.finscope.domain.research.agent.ResearchAgentState;
import com.finscope.domain.research.agent.ResearchToolObservation;
import com.finscope.domain.research.mission.ResearchMissionGap;
import com.finscope.service.research.agent.tool.ResearchAgentToolContext;
import com.finscope.service.research.agent.tool.ResearchToolExecutionResult;
import com.finscope.service.research.agent.tool.ResearchToolRetryExecutor;
import com.finscope.service.research.mission.ResearchMissionService;
import com.finscope.service.research.mission.ResearchPlanPatch;
import com.finscope.service.research.runtime.ResearchRuntimeService;
import com.finscope.service.research.runtime.RuntimeNodeStart;
import org.springframework.stereotype.Service;

@Service
public class ResearchAgentLoopService {
    static final int MAX_CONTROL_ITERATIONS = 24;
    static final int MAX_REPLANS = 3;

    private final ResearchAgentRepository repository;
    private final ResearchAgentContextBuilder contextBuilder;
    private final ResearchDecisionAgent decisionAgent;
    private final ResearchToolRetryExecutor toolExecutor;
    private final ResearchAgentTurnService turnService;
    private final ResearchAgentStateReducer reducer;
    private final ResearchFinishVerifier finishVerifier;
    private final ResearchMissionService missionService;
    private final ResearchRuntimeService runtimeService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public ResearchAgentLoopService(ResearchAgentRepository repository,
                                    ResearchAgentContextBuilder contextBuilder,
                                    ResearchDecisionAgent decisionAgent,
                                    ResearchToolRetryExecutor toolExecutor,
                                    ResearchAgentTurnService turnService,
                                    ResearchAgentStateReducer reducer,
                                    ResearchFinishVerifier finishVerifier,
                                    ResearchMissionService missionService,
                                    ResearchRuntimeService runtimeService) {
        this.repository = repository;
        this.contextBuilder = contextBuilder;
        this.decisionAgent = decisionAgent;
        this.toolExecutor = toolExecutor;
        this.turnService = turnService;
        this.reducer = reducer;
        this.finishVerifier = finishVerifier;
        this.missionService = missionService;
        this.runtimeService = runtimeService;
    }

    public ResearchAgentLoopResult run(Long runId) {
        return run(runId, ResearchMode.DEEP);
    }

    public ResearchAgentLoopResult run(Long runId, ResearchMode requestedMode) {
        ResearchMode mode = ResearchMode.defaultIfNull(requestedMode);
        ResearchAgentState state = repository.findState(runId)
                .orElseGet(() -> repository.initialize(runId, "按最新证据缺口和 Observation 选择下一动作"));
        if ("COMPLETED".equals(state.getStatus())) {
            return ResearchAgentLoopResult.finished(state.getDecisionCount(), countExternalActions(runId));
        }
        int externalActions = countExternalActions(runId);
        int controlLimit = Math.min(MAX_CONTROL_ITERATIONS, mode.getMaxIterations());
        for (int control = 0; control < controlLimit; control++) {
            if (externalActions >= mode.getSearchActionBudget()) {
                return finishAtEvidenceBoundary(runId, state, externalActions);
            }
            ResearchDecisionContext context = contextBuilder.build(runId);
            ResearchDecisionResult result = decisionAgent.decide(context);
            ResearchAgentDecision decision = result.getDecision();
            if (result.getFallbackReason() != null) {
                decision.setValidationError(result.getFallbackReason()
                        + (result.getFallbackDetail() == null ? "" : "：" + result.getFallbackDetail()));
            }
            repository.appendDecision(decision);
            state = repository.findState(runId).orElseThrow(
                    () -> new IllegalStateException("研究 Agent 状态在决策后丢失：" + runId));

            if ("TOOL_CALL".equals(decision.getDecisionType())) {
                ToolStep step = executeTool(runId, state, decision, mode);
                externalActions += step.externalActionDelta;
                state = step.state;
                if (step.terminationReason != null) {
                    return ResearchAgentLoopResult.aborted(state.getDecisionCount(), externalActions,
                            step.terminationReason);
                }
                continue;
            }
            if ("PLAN_PATCH".equals(decision.getDecisionType())) {
                if (state.getReplanCount() >= MAX_REPLANS) {
                    repository.updateDecisionStatus(decision.getId(), "REJECTED", "REPLAN_LIMIT_REACHED");
                    reducer.recordAbort(state, decision);
                    return ResearchAgentLoopResult.aborted(state.getDecisionCount(), externalActions,
                            "REPLAN_LIMIT_REACHED");
                }
                ResearchPlanPatch patch = objectMapper.convertValue(result.getArguments(), ResearchPlanPatch.class);
                missionService.applyPatch(runId, patch);
                repository.updateDecisionStatus(decision.getId(), "COMPLETED", decision.getValidationError());
                reducer.recordPlanPatch(state, decision);
                continue;
            }
            if ("FINISH".equals(decision.getDecisionType())) {
                ResearchFinishVerdict verdict = finishVerifier.verify(runId);
                repository.updateDecisionStatus(decision.getId(),
                        verdict.isAccepted() ? "COMPLETED" : "REJECTED",
                        verdict.isAccepted() ? decision.getValidationError()
                                : verdict.getReasonCode() + "：" + verdict.getMissingConditions());
                reducer.recordFinishVerdict(state, decision, verdict);
                if (verdict.isAccepted()) {
                    return ResearchAgentLoopResult.finished(decision.getIteration(), externalActions);
                }
                if (state.getFinishRejectionCount() >= 2) {
                    reducer.recordAbort(state, decision);
                    return ResearchAgentLoopResult.aborted(decision.getIteration(), externalActions,
                            "REPEATED_FINISH_REJECTED:" + verdict.getReasonCode());
                }
                continue;
            }
            repository.updateDecisionStatus(decision.getId(), "COMPLETED", decision.getValidationError());
            reducer.recordAbort(state, decision);
            return ResearchAgentLoopResult.aborted(decision.getIteration(), externalActions,
                    decision.getDecisionSummary());
        }
        return abortForControlLimit(runId, externalActions);
    }

    private ResearchAgentLoopResult finishAtEvidenceBoundary(Long runId,
                                                               ResearchAgentState state,
                                                               int externalActions) {
        ResearchMissionGap gap = missionService.assess(runId, "agent-search-budget-boundary");
        if (gap != null && gap.isSufficient()) {
            ResearchAgentDecision decision = terminalDecision(runId, state, "FINISH",
                    "在搜索预算边界校验研究完成条件",
                    "搜索额度已完成且最新证据达到门槛，提交独立完成校验");
            repository.appendDecision(decision);
            ResearchFinishVerdict verdict = finishVerifier.verify(runId);
            repository.updateDecisionStatus(decision.getId(), verdict.isAccepted() ? "COMPLETED" : "REJECTED",
                    verdict.isAccepted() ? null : verdict.getReasonCode() + "：" + verdict.getMissingConditions());
            reducer.recordFinishVerdict(state, decision, verdict);
            if (verdict.isAccepted()) {
                return ResearchAgentLoopResult.finished(decision.getIteration(), externalActions);
            }
            state = repository.findState(runId).orElse(state);
        }
        ResearchAgentDecision decision = terminalDecision(runId, state, "ABORT",
                "在证据边界结束扩展研究",
                "搜索额度已经完成，保留现有证据生成带局限声明的研究报告");
        repository.appendDecision(decision);
        repository.updateDecisionStatus(decision.getId(), "COMPLETED", null);
        reducer.recordAbort(state, decision);
        return ResearchAgentLoopResult.aborted(decision.getIteration(), externalActions,
                "EVIDENCE_LIMIT_REACHED");
    }

    private ResearchAgentDecision terminalDecision(Long runId,
                                                    ResearchAgentState state,
                                                    String type,
                                                    String subgoal,
                                                    String summary) {
        ResearchAgentDecision decision = new ResearchAgentDecision();
        decision.setResearchRunId(runId);
        decision.setIteration(state.getDecisionCount() + 1);
        decision.setDecisionType(type);
        decision.setCurrentSubgoal(subgoal);
        decision.setArgumentsJson("{}");
        decision.setDecisionSummary(summary);
        decision.setConfidence(1D);
        decision.setDecisionMode("CONTROLLED");
        decision.setStatus("PROPOSED");
        return decision;
    }

    private ToolStep executeTool(Long runId,
                                 ResearchAgentState state,
                                 ResearchAgentDecision decision,
                                 ResearchMode mode) {
        boolean external = isExternalTool(decision.getToolCode());
        String nodeId = "agent_tool:" + decision.getActionFingerprint();
        RuntimeNodeStart start = runtimeService.startNode(runId, nodeId, "EXPAND",
                external ? decision.getActionFingerprint() : null,
                "decision=" + decision.getId() + ", tool=" + decision.getToolCode()
                        + ", subgoal=" + decision.getCurrentSubgoal());
        if (!start.isStarted() && !start.isAlreadyCompleted()) {
            repository.updateDecisionStatus(decision.getId(), "REJECTED", start.getTerminationReason());
            reducer.recordAbort(state, decision);
            return new ToolStep(state, 0, start.getTerminationReason());
        }
        ResearchToolObservation observation;
        if (start.isAlreadyCompleted()) {
            observation = new ResearchToolObservation();
            observation.setStatus("NO_PROGRESS");
            observation.setObservationSummary("相同动作已由 Runtime 完成，本次未重复执行");
            observation.setStateHash(contextStateHash(state));
        } else {
            ResearchToolExecutionResult execution = toolExecutor.execute(decision,
                    new ResearchAgentToolContext(runId, decision.getId(), mode));
            observation = execution.getObservation();
        }
        ResearchAgentState reduced = turnService.commitToolTurn(runId, nodeId, start.isStarted(),
                state, decision, observation);
        if (external && !"TERMINAL_ERROR".equals(observation.getStatus())
                && !"RETRYABLE_ERROR".equals(observation.getStatus())
                && !"FAILED".equals(observation.getStatus())) {
            missionService.assess(runId, "agent-decision-" + decision.getId());
        }
        if ("TERMINAL_ERROR".equals(observation.getStatus())
                || "RETRYABLE_ERROR".equals(observation.getStatus())) {
            return new ToolStep(reduced, external && start.isStarted() ? 1 : 0,
                    observation.getErrorType() == null ? "TOOL_EXECUTION_FAILED" : observation.getErrorType());
        }
        return new ToolStep(reduced, external && start.isStarted() ? 1 : 0, null);
    }

    private ResearchAgentLoopResult abortForControlLimit(Long runId, int externalActions) {
        ResearchAgentState state = repository.findState(runId).orElseThrow(
                () -> new IllegalStateException("研究 Agent 状态不存在：" + runId));
        ResearchAgentDecision decision = new ResearchAgentDecision();
        decision.setResearchRunId(runId);
        decision.setIteration(state.getDecisionCount() + 1);
        decision.setDecisionType("ABORT");
        decision.setCurrentSubgoal("停止循环空转");
        decision.setArgumentsJson("{}");
        decision.setDecisionSummary("控制循环达到上限，保留当前轨迹并安全终止");
        decision.setConfidence(1D);
        decision.setDecisionMode("CONTROLLED");
        decision.setStatus("COMPLETED");
        repository.appendDecision(decision);
        reducer.recordAbort(state, decision);
        return ResearchAgentLoopResult.aborted(decision.getIteration(), externalActions,
                "CONTROL_ITERATION_LIMIT");
    }

    private int countExternalActions(Long runId) {
        int count = 0;
        for (ResearchAgentDecision decision : repository.findDecisions(runId)) {
            if (isExternalTool(decision.getToolCode())
                    && ("COMPLETED".equals(decision.getStatus()) || "FAILED".equals(decision.getStatus()))) {
                count++;
            }
        }
        return count;
    }

    private boolean isExternalTool(String toolCode) {
        return "public_news_search".equals(toolCode) || "research_material_search".equals(toolCode);
    }

    private String contextStateHash(ResearchAgentState state) {
        return state.getEvidenceSummary() == null ? "UNCHANGED" : state.getEvidenceSummary();
    }

    private static class ToolStep {
        private final ResearchAgentState state;
        private final int externalActionDelta;
        private final String terminationReason;

        private ToolStep(ResearchAgentState state, int externalActionDelta, String terminationReason) {
            this.state = state;
            this.externalActionDelta = externalActionDelta;
            this.terminationReason = terminationReason;
        }
    }
}
