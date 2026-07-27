package com.finscope.service.research.agent.tool;

import com.finscope.domain.research.agent.ResearchToolObservation;
import com.finscope.domain.research.mission.ResearchMissionGap;
import com.finscope.domain.research.mission.ResearchToolDescriptor;
import com.finscope.service.research.mission.ResearchMissionService;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.Map;

@Component
public class EvidenceAssessTool implements ResearchAgentTool {
    private final ResearchMissionService missionService;

    public EvidenceAssessTool(ResearchMissionService missionService) {
        this.missionService = missionService;
    }

    @Override
    public ResearchToolDescriptor descriptor() {
        ResearchToolDescriptor value = new ResearchToolDescriptor();
        value.setCode("evidence_assess");
        value.setName("证据缺口评估");
        value.setDescription("计算当前证据数量、独立来源、正反分布和下一推荐意图");
        value.setInputSchema(Collections.<String, String>emptyMap());
        value.setOutputSchema(Collections.singletonMap("gap", "结构化证据缺口"));
        value.setTimeoutMs(2_000);
        value.setReadOnly(true);
        value.setParallelizable(false);
        value.setRiskLevel("LOW");
        value.setBudgetType("INTERNAL_ACTION");
        return value;
    }

    @Override
    public void validate(Map<String, Object> arguments) {
        if (arguments != null && !arguments.isEmpty()) {
            throw new IllegalArgumentException("证据缺口评估不接受参数");
        }
    }

    @Override
    public ResearchToolObservation execute(ResearchAgentToolContext context, Map<String, Object> arguments) {
        validate(arguments);
        ResearchMissionGap gap = missionService.assess(context.getResearchRunId(), context.decisionTaskKey());
        ResearchToolObservation value = new ResearchToolObservation();
        value.setStatus("SUCCESS");
        value.setObservationSummary("证据评估完成：证据=" + gap.getEvidenceCount()
                + "，独立来源=" + gap.getSourceCount() + "，支持=" + gap.getSupportCount()
                + "，反方=" + gap.getCounterCount() + "，充分=" + gap.isSufficient()
                + "，建议下一意图=" + gap.getRecommendedIntent());
        value.setNewInformation(gap.getWarnings().isEmpty()
                ? "当前证据没有新增告警"
                : String.join("；", gap.getWarnings()));
        value.setEvidenceDelta(0);
        value.setSourceDelta(0);
        value.setStateHash(gap.getStateHash());
        value.setDataRefs(gap.getId() == null
                ? Collections.<String>emptyList()
                : Collections.singletonList("mission-gap:" + gap.getId()));
        return value;
    }
}
