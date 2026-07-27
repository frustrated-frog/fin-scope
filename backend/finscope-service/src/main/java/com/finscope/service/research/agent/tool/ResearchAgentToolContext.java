package com.finscope.service.research.agent.tool;

public class ResearchAgentToolContext {
    private final Long researchRunId;
    private final Long decisionId;

    public ResearchAgentToolContext(Long researchRunId, Long decisionId) {
        if (researchRunId == null || decisionId == null) {
            throw new IllegalArgumentException("研究运行和决策 ID 不能为空");
        }
        this.researchRunId = researchRunId;
        this.decisionId = decisionId;
    }

    public Long getResearchRunId() { return researchRunId; }
    public Long getDecisionId() { return decisionId; }
    public String decisionTaskKey() { return "agent_decision_" + decisionId; }
}
