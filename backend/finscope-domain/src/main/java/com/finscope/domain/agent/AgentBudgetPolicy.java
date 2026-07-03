package com.finscope.domain.agent;

public class AgentBudgetPolicy {
    private int maxNodes = 80;
    private int maxLlmCalls = 30;
    private int maxRetriesPerNode = 1;
    private int maxRunSeconds = 300;
    private int maxPromptChars = 9000;
    private int maxOutputChars = 12000;
    private int sameActionWarnThreshold = 2;
    private int sameActionHardThreshold = 3;
    private int noProgressLimit = 3;

    public static AgentBudgetPolicy defaults() {
        return new AgentBudgetPolicy();
    }

    public int getMaxNodes() {
        return maxNodes;
    }

    public void setMaxNodes(int maxNodes) {
        this.maxNodes = maxNodes;
    }

    public int getMaxLlmCalls() {
        return maxLlmCalls;
    }

    public void setMaxLlmCalls(int maxLlmCalls) {
        this.maxLlmCalls = maxLlmCalls;
    }

    public int getMaxRetriesPerNode() {
        return maxRetriesPerNode;
    }

    public void setMaxRetriesPerNode(int maxRetriesPerNode) {
        this.maxRetriesPerNode = maxRetriesPerNode;
    }

    public int getMaxRunSeconds() {
        return maxRunSeconds;
    }

    public void setMaxRunSeconds(int maxRunSeconds) {
        this.maxRunSeconds = maxRunSeconds;
    }

    public int getMaxPromptChars() {
        return maxPromptChars;
    }

    public void setMaxPromptChars(int maxPromptChars) {
        this.maxPromptChars = maxPromptChars;
    }

    public int getMaxOutputChars() {
        return maxOutputChars;
    }

    public void setMaxOutputChars(int maxOutputChars) {
        this.maxOutputChars = maxOutputChars;
    }

    public int getSameActionWarnThreshold() {
        return sameActionWarnThreshold;
    }

    public void setSameActionWarnThreshold(int sameActionWarnThreshold) {
        this.sameActionWarnThreshold = sameActionWarnThreshold;
    }

    public int getSameActionHardThreshold() {
        return sameActionHardThreshold;
    }

    public void setSameActionHardThreshold(int sameActionHardThreshold) {
        this.sameActionHardThreshold = sameActionHardThreshold;
    }

    public int getNoProgressLimit() {
        return noProgressLimit;
    }

    public void setNoProgressLimit(int noProgressLimit) {
        this.noProgressLimit = noProgressLimit;
    }
}
