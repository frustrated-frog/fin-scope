package com.finscope.service.research.agent.tool;

import com.finscope.domain.research.ResearchMode;

public class ResearchAgentToolContext {
    private final Long researchRunId;
    private final Long decisionId;
    private final ResearchMode researchMode;

    public ResearchAgentToolContext(Long researchRunId, Long decisionId) {
        this(researchRunId, decisionId, ResearchMode.DEEP);
    }

    public ResearchAgentToolContext(Long researchRunId, Long decisionId, ResearchMode researchMode) {
        if (researchRunId == null || decisionId == null) {
            throw new IllegalArgumentException("研究运行和决策 ID 不能为空");
        }
        this.researchRunId = researchRunId;
        this.decisionId = decisionId;
        this.researchMode = ResearchMode.defaultIfNull(researchMode);
    }

    public Long getResearchRunId() { return researchRunId; }
    public Long getDecisionId() { return decisionId; }
    public ResearchMode getResearchMode() { return researchMode; }
    public String decisionTaskKey() { return "agent_decision_" + decisionId; }
}
