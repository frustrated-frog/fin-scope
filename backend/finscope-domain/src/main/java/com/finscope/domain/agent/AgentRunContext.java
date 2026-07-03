package com.finscope.domain.agent;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

public class AgentRunContext {
    private final Long researchRunId;
    private final AgentBudgetPolicy budgetPolicy;
    private final LocalDateTime startedAt;
    private final Map<String, Integer> actionCounts = new HashMap<String, Integer>();
    private String currentNodeName;
    private int nodeCount;
    private int llmCallCount;
    private int warningCount;

    private AgentRunContext(Long researchRunId, AgentBudgetPolicy budgetPolicy) {
        this.researchRunId = researchRunId;
        this.budgetPolicy = budgetPolicy == null ? AgentBudgetPolicy.defaults() : budgetPolicy;
        this.startedAt = LocalDateTime.now();
    }

    public static AgentRunContext start(Long researchRunId, AgentBudgetPolicy budgetPolicy) {
        return new AgentRunContext(researchRunId, budgetPolicy);
    }

    public void enterNode(String nodeName) {
        this.currentNodeName = nodeName;
        this.nodeCount++;
    }

    public void recordLlmCall() {
        this.llmCallCount++;
    }

    public ActionRecord recordAction(AgentActionFingerprint fingerprint) {
        String value = fingerprint == null ? "" : fingerprint.getFingerprint();
        Integer current = actionCounts.get(value);
        int count = current == null ? 1 : current + 1;
        actionCounts.put(value, count);
        boolean warn = count >= budgetPolicy.getSameActionWarnThreshold();
        boolean hard = count >= budgetPolicy.getSameActionHardThreshold();
        if (count == budgetPolicy.getSameActionWarnThreshold()) {
            warningCount++;
        }
        return new ActionRecord(value, count, warn, hard);
    }

    public int actionCount(AgentActionFingerprint fingerprint) {
        String value = fingerprint == null ? "" : fingerprint.getFingerprint();
        Integer count = actionCounts.get(value);
        return count == null ? 0 : count;
    }

    public boolean isNodeBudgetExceeded() {
        return nodeCount > budgetPolicy.getMaxNodes();
    }

    public boolean isLlmBudgetExceeded() {
        return llmCallCount > budgetPolicy.getMaxLlmCalls();
    }

    public Long getResearchRunId() {
        return researchRunId;
    }

    public AgentBudgetPolicy getBudgetPolicy() {
        return budgetPolicy;
    }

    public LocalDateTime getStartedAt() {
        return startedAt;
    }

    public String getCurrentNodeName() {
        return currentNodeName;
    }

    public int getNodeCount() {
        return nodeCount;
    }

    public int getLlmCallCount() {
        return llmCallCount;
    }

    public int getWarningCount() {
        return warningCount;
    }

    public static class ActionRecord {
        private final String fingerprint;
        private final int count;
        private final boolean warnThresholdReached;
        private final boolean hardThresholdReached;

        private ActionRecord(String fingerprint, int count, boolean warnThresholdReached, boolean hardThresholdReached) {
            this.fingerprint = fingerprint;
            this.count = count;
            this.warnThresholdReached = warnThresholdReached;
            this.hardThresholdReached = hardThresholdReached;
        }

        public String getFingerprint() {
            return fingerprint;
        }

        public int getCount() {
            return count;
        }

        public boolean isWarnThresholdReached() {
            return warnThresholdReached;
        }

        public boolean isHardThresholdReached() {
            return hardThresholdReached;
        }
    }
}
