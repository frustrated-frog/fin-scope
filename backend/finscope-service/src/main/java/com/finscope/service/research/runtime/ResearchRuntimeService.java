package com.finscope.service.research.runtime;

import com.finscope.common.exception.BusinessConflictException;
import com.finscope.common.exception.ResourceNotFoundException;
import com.finscope.dao.research.runtime.ResearchRuntimeRepository;
import com.finscope.domain.research.ResearchMode;
import com.finscope.domain.research.runtime.ResearchRuntimeCheckpoint;
import com.finscope.domain.research.runtime.ResearchRuntimeEvent;
import com.finscope.domain.research.runtime.ResearchRuntimeView;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

@Service
public class ResearchRuntimeService {
    public static final int DEFAULT_MAX_ACTIONS = 12;
    private static final Set<String> RECOVERABLE = new HashSet<String>(Arrays.asList(
            "INTERRUPTED", "FAILED", "PARTIAL_SUCCESS"));

    private final ResearchRuntimeRepository repository;
    private final ResearchRuntimePolicy policy;

    public ResearchRuntimeService(ResearchRuntimeRepository repository, ResearchRuntimePolicy policy) {
        this.repository = repository;
        this.policy = policy;
    }

    public ResearchRuntimeCheckpoint initialize(Long runId, int maxActions) {
        return repository.initialize(runId, maxActions);
    }

    public ResearchRuntimeCheckpoint initialize(Long runId, ResearchMode mode) {
        return repository.initialize(runId, policy.maxActions(mode));
    }

    public int maxActions(ResearchMode mode) {
        return policy.maxActions(mode);
    }

    @Transactional
    public RuntimeNodeStart startNode(Long runId,
                                      String nodeId,
                                      String phase,
                                      String actionFingerprint,
                                      String inputSummary) {
        if (repository.hasCompletedNode(runId, nodeId)) {
            return RuntimeNodeStart.alreadyCompleted();
        }
        ResearchRuntimeCheckpoint checkpoint = required(runId);
        boolean systemNode = actionFingerprint == null;
        boolean finalizationNode = systemNode && isFinalizationNode(nodeId);
        int repeatedCount = systemNode ? 0 : repository.countStartedActions(runId, actionFingerprint);
        RuntimeGuardDecision decision;
        if (checkpoint.getTerminationReason() != null && !finalizationNode) {
            decision = RuntimeGuardDecision.terminated(checkpoint.getTerminationReason());
        } else if (systemNode) {
            decision = checkpoint.isTerminal() && !finalizationNode
                    ? RuntimeGuardDecision.terminated("ALREADY_TERMINAL")
                    : RuntimeGuardDecision.allowed();
        } else {
            decision = policy.beforeAction(checkpoint, repeatedCount);
        }
        if (!decision.isAllowed()) {
            if (!"ALREADY_TERMINAL".equals(decision.getTerminationReason())
                    && checkpoint.getTerminationReason() == null) {
                if (!repository.terminate(runId, checkpoint.getStateVersion(), decision.getTerminationReason())) {
                    throw new BusinessConflictException("研究运行终止状态发生并发冲突：" + runId);
                }
                repository.appendEvent(event(runId, "GUARD_TRIGGERED", nodeId, "FINALIZING", actionFingerprint,
                        null, null, null, 0, decision.getTerminationReason(), null));
            }
            return RuntimeNodeStart.terminated(decision.getTerminationReason(), checkpoint);
        }
        int consumedActions = checkpoint.getConsumedActions() + (systemNode ? 0 : 1);
        if (!repository.startNode(runId, checkpoint.getStateVersion(), phase, nodeId, consumedActions,
                assessmentRound(nodeId),
                finalizationNode && checkpoint.getTerminationReason() != null)) {
            throw new BusinessConflictException("研究运行状态已被其他执行器更新，请刷新后重试：" + runId);
        }
        repository.appendEvent(event(runId, "NODE_STARTED", nodeId, "RUNNING", actionFingerprint,
                inputSummary, null, null, 0, null, null));
        return RuntimeNodeStart.started(required(runId));
    }

    @Transactional
    public ResearchRuntimeCheckpoint completeNode(Long runId,
                                                  String nodeId,
                                                  String stateHash,
                                                  int progressDelta,
                                                  String outputSummary) {
        ResearchRuntimeCheckpoint checkpoint = required(runId);
        int noProgressCount = isBudgetedNode(nodeId)
                && stateHash != null && stateHash.equals(checkpoint.getLastStateHash())
                ? checkpoint.getNoProgressCount() + 1 : 0;
        if (!repository.completeNode(runId, checkpoint.getStateVersion(), nodeId, stateHash,
                noProgressCount, progressDelta)) {
            throw new BusinessConflictException("研究节点完成状态发生并发冲突：" + nodeId);
        }
        repository.appendEvent(event(runId, "NODE_COMPLETED", nodeId, "COMPLETED", null,
                null, outputSummary, stateHash, progressDelta, null, null));
        return required(runId);
    }

    @Transactional
    public ResearchRuntimeCheckpoint failNode(Long runId, String nodeId, String errorType, String errorMessage) {
        ResearchRuntimeCheckpoint checkpoint = required(runId);
        String failedNode = checkpoint.getCurrentNode() == null ? nodeId : checkpoint.getCurrentNode();
        if (!repository.failNode(runId, checkpoint.getStateVersion(), errorMessage)) {
            throw new BusinessConflictException("研究节点失败状态发生并发冲突：" + nodeId);
        }
        ResearchRuntimeCheckpoint failed = required(runId);
        repository.appendEvent(event(runId, "NODE_FAILED", failedNode, failed.getStatus(), null,
                null, null, checkpoint.getLastStateHash(), 0, errorType, errorMessage));
        return failed;
    }

    @Transactional
    public ResearchRuntimeCheckpoint complete(Long runId) {
        ResearchRuntimeCheckpoint checkpoint = required(runId);
        if (checkpoint.isTerminal()) {
            return checkpoint;
        }
        if (!repository.completeRuntime(runId, checkpoint.getStateVersion())) {
            throw new BusinessConflictException("研究运行完成状态发生并发冲突：" + runId);
        }
        ResearchRuntimeCheckpoint completed = required(runId);
        repository.appendEvent(event(runId, "TERMINATED", "complete", completed.getStatus(), null,
                null, "runtime completed", checkpoint.getLastStateHash(), 0,
                completed.getTerminationReason(), null));
        return completed;
    }

    @Transactional
    public ResearchRuntimeCheckpoint resume(Long runId) {
        ResearchRuntimeCheckpoint checkpoint = required(runId);
        if (!isRecoverable(checkpoint)) {
            throw new BusinessConflictException("当前研究运行不可恢复：" + checkpoint.getStatus());
        }
        if (checkpoint.getConsumedActions() >= checkpoint.getMaxActions()) {
            throw new BusinessConflictException("研究运行已耗尽动作预算，不能继续恢复");
        }
        if (!repository.resume(runId, checkpoint.getStateVersion())) {
            throw new BusinessConflictException("研究运行已被其他执行器恢复：" + runId);
        }
        ResearchRuntimeCheckpoint resumed = required(runId);
        repository.appendEvent(event(runId, "RESUMED", resumed.getCurrentNode(), "RUNNING", null,
                "stateVersion=" + checkpoint.getStateVersion(), null, resumed.getLastStateHash(), 0, null, null));
        return resumed;
    }

    public Optional<ResearchRuntimeCheckpoint> findCheckpoint(Long runId) {
        return repository.findCheckpoint(runId);
    }

    public ResearchRuntimeView view(Long runId) {
        ResearchRuntimeCheckpoint checkpoint = required(runId);
        ResearchRuntimeView view = new ResearchRuntimeView();
        view.setCheckpoint(checkpoint);
        view.setEvents(repository.findEvents(runId));
        view.setRecoverable(isRecoverable(checkpoint)
                && checkpoint.getConsumedActions() < checkpoint.getMaxActions());
        return view;
    }

    private boolean isRecoverable(ResearchRuntimeCheckpoint checkpoint) {
        return checkpoint != null && RECOVERABLE.contains(checkpoint.getStatus());
    }

    private boolean isFinalizationNode(String nodeId) {
        return "compose_report".equals(nodeId) || "verify_output".equals(nodeId);
    }

    private int assessmentRound(String nodeId) {
        if (nodeId == null || !nodeId.startsWith("assess_evidence:")) {
            return 0;
        }
        try {
            return Integer.parseInt(nodeId.substring(nodeId.indexOf(':') + 1));
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }

    private boolean isBudgetedNode(String nodeId) {
        return nodeId != null && (nodeId.startsWith("mission:") || nodeId.startsWith("expand_query:")
                || nodeId.startsWith("agent_tool:"));
    }

    private ResearchRuntimeCheckpoint required(Long runId) {
        return repository.findCheckpoint(runId)
                .orElseThrow(() -> new ResourceNotFoundException("研究运行时不存在：" + runId));
    }

    private ResearchRuntimeEvent event(Long runId,
                                       String eventType,
                                       String nodeId,
                                       String status,
                                       String actionFingerprint,
                                       String inputSummary,
                                       String outputSummary,
                                       String stateHash,
                                       int progressDelta,
                                       String errorType,
                                       String errorMessage) {
        ResearchRuntimeEvent event = new ResearchRuntimeEvent();
        event.setResearchRunId(runId);
        event.setEventType(eventType);
        event.setNodeId(nodeId);
        event.setStatus(status);
        event.setActionFingerprint(actionFingerprint);
        event.setInputSummary(inputSummary);
        event.setOutputSummary(outputSummary);
        event.setStateHash(stateHash);
        event.setProgressDelta(progressDelta);
        event.setErrorType(errorType);
        event.setErrorMessage(errorMessage);
        return event;
    }
}
