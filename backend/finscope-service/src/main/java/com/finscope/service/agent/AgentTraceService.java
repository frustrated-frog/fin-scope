package com.finscope.service.agent;

import com.finscope.dao.agent.AgentRunRepository;
import com.finscope.domain.agent.AgentActionFingerprint;
import com.finscope.domain.agent.AgentNodeResult;
import com.finscope.domain.agent.AgentRun;
import com.finscope.domain.agent.AgentRunContext;
import com.finscope.domain.agent.AgentTraceSubject;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

@Service
public class AgentTraceService {
    private final AgentRunRepository agentRunRepository;

    public AgentTraceService(AgentRunRepository agentRunRepository) {
        this.agentRunRepository = agentRunRepository;
    }

    public void recordNode(Long eventId,
                           Long articleId,
                           AgentRunContext context,
                           AgentActionFingerprint fingerprint,
                           AgentNodeResult<?> result,
                           long durationMs,
                           String metadataJson) {
        recordNode(eventId == null && articleId == null ? null : AgentTraceSubject.of("RESEARCH", eventId),
                eventId, articleId, context, fingerprint, result, durationMs, metadataJson);
    }

    public void recordNode(AgentTraceSubject subject,
                           AgentRunContext context,
                           AgentActionFingerprint fingerprint,
                           AgentNodeResult<?> result,
                           long durationMs,
                           String metadataJson) {
        recordNode(subject, null, null, context, fingerprint, result, durationMs, metadataJson);
    }

    private void recordNode(AgentTraceSubject subject,
                            Long eventId,
                            Long articleId,
                            AgentRunContext context,
                            AgentActionFingerprint fingerprint,
                            AgentNodeResult<?> result,
                            long durationMs,
                            String metadataJson) {
        AgentRun run = new AgentRun();
        run.setResearchRunId(context == null ? null : context.getResearchRunId());
        run.setEventId(eventId);
        run.setArticleId(articleId);
        run.setSubjectType(subject == null ? null : subject.getType());
        run.setSubjectId(subject == null ? null : subject.getId());
        run.setNodeName(nodeName(context, fingerprint));
        run.setStatus(result == null ? "UNKNOWN" : result.getStatus());
        run.setInput(result == null ? "" : result.getInputSummary());
        run.setOutput(result == null ? "" : result.getOutputSummary());
        run.setErrorMessage(result == null ? "" : result.getErrorMessage());
        run.setDurationMs(durationMs);
        run.setStepId(fingerprint == null ? "" : fingerprint.getFingerprint());
        run.setAttempt(context == null ? 1 : Math.max(1, context.actionCount(fingerprint)));
        run.setActionFingerprint(fingerprint == null ? "" : fingerprint.getFingerprint());
        run.setInputHash(fingerprint == null ? "" : fingerprint.getInputHash());
        run.setOutputHash(hash(run.getOutput()));
        run.setErrorType(result == null ? "" : result.getErrorType());
        run.setFallbackUsed(result != null && result.isFallbackUsed());
        run.setFallbackReason(result == null ? "" : result.getFallbackReason());
        run.setTerminationReason(terminationReason(result));
        run.setProgressDelta(result == null ? 0 : result.getProgressDelta());
        run.setBudgetSnapshot(budgetSnapshot(context));
        run.setMetadataJson(metadataJson);
        agentRunRepository.record(run);
    }

    private String nodeName(AgentRunContext context, AgentActionFingerprint fingerprint) {
        if (fingerprint != null && !fingerprint.getNodeName().isEmpty()) {
            return fingerprint.getNodeName();
        }
        return context == null ? "" : context.getCurrentNodeName();
    }

    private String terminationReason(AgentNodeResult<?> result) {
        if (result == null) {
            return "";
        }
        if ("SKIPPED".equals(result.getStatus())) {
            return result.getErrorType();
        }
        if ("FAILED".equals(result.getStatus())) {
            return result.getErrorType();
        }
        return "";
    }

    private String budgetSnapshot(AgentRunContext context) {
        if (context == null) {
            return "{}";
        }
        return "{\"nodeCount\":" + context.getNodeCount()
                + ",\"llmCallCount\":" + context.getLlmCallCount()
                + ",\"warningCount\":" + context.getWarningCount()
                + "}";
    }

    private String hash(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest((value == null ? "" : value).getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder();
            for (int index = 0; index < 8 && index < bytes.length; index++) {
                builder.append(String.format("%02x", bytes[index]));
            }
            return builder.toString();
        } catch (Exception ex) {
            return Integer.toHexString((value == null ? "" : value).hashCode());
        }
    }
}
