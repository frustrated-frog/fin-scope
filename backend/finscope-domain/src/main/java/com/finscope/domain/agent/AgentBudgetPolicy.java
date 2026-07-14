package com.finscope.domain.agent;

public class AgentBudgetPolicy {
    /**
     * 最大节点数。
     */
    private int maxNodes = 80;
    /**
     * 最大大模型调用次数。
     */
    private int maxLlmCalls = 30;
    /**
     * 单节点最大重试次数。
     */
    private int maxRetriesPerNode = 1;
    /**
     * 单次运行最长秒数。
     */
    private int maxRunSeconds = 300;
    /**
     * 提示词最大字符数。
     */
    private int maxPromptChars = 9000;
    /**
     * 输出最大字符数。
     */
    private int maxOutputChars = 12000;
    /**
     * 相同动作告警阈值。
     */
    private int sameActionWarnThreshold = 2;
    /**
     * 相同动作硬停止阈值。
     */
    private int sameActionHardThreshold = 3;
    /**
     * 无进展次数上限。
     */
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
