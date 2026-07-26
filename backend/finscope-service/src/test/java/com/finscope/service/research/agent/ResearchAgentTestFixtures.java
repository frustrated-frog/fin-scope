package com.finscope.service.research.agent;

import com.finscope.domain.research.agent.ResearchAgentState;
import com.finscope.domain.research.mission.ResearchMission;
import com.finscope.domain.research.mission.ResearchMissionGap;

import java.util.Arrays;
import java.util.Collections;

final class ResearchAgentTestFixtures {
    private ResearchAgentTestFixtures() {
    }

    static ResearchDecisionContext counterGapContext() {
        ResearchDecisionContext context = baseContext();
        ResearchMissionGap gap = new ResearchMissionGap();
        gap.setResearchRunId(77L);
        gap.setAssessmentIndex(2);
        gap.setEvidenceCount(5);
        gap.setSourceCount(2);
        gap.setSupportCount(5);
        gap.setCounterCount(0);
        gap.setRecommendedIntent("COUNTER");
        gap.setWarnings(Collections.singletonList("缺少反方证据"));
        gap.setStateHash("5:2:5:0");
        context.setLatestGap(gap);
        return context;
    }

    static ResearchDecisionContext sufficientContext() {
        ResearchDecisionContext context = baseContext();
        ResearchMissionGap gap = new ResearchMissionGap();
        gap.setResearchRunId(77L);
        gap.setAssessmentIndex(3);
        gap.setEvidenceCount(8);
        gap.setSourceCount(4);
        gap.setSupportCount(5);
        gap.setCounterCount(3);
        gap.setSufficient(true);
        gap.setRecommendedIntent("NONE");
        gap.setStateHash("8:4:5:3");
        context.setLatestGap(gap);
        return context;
    }

    private static ResearchDecisionContext baseContext() {
        ResearchDecisionContext context = new ResearchDecisionContext();
        context.setResearchRunId(77L);
        context.setNextIteration(2);
        context.setRemainingActions(5);
        context.setPrompt("最新 Observation 表明支持证据单边，请选择下一动作。");
        context.setAttemptedFingerprints(Collections.<String>emptyList());

        ResearchMission mission = new ResearchMission();
        mission.setResearchRunId(77L);
        mission.setGoal("AI资本开支是否继续支撑光模块需求？");
        mission.setSubject("光模块");
        mission.setSuccessCriteria(Arrays.asList("至少两个独立来源", "覆盖正反证据"));
        context.setMission(mission);

        ResearchAgentState state = new ResearchAgentState();
        state.setResearchRunId(77L);
        state.setCurrentSubgoal("判断命题是否成立");
        context.setState(state);
        return context;
    }
}
