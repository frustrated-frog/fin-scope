package com.finscope.service.research.agent;

import com.finscope.dao.research.agent.ResearchAgentRepository;
import com.finscope.dao.research.mission.ResearchMissionRepository;
import com.finscope.dao.research.runtime.ResearchRuntimeRepository;
import com.finscope.domain.research.agent.ResearchAgentDecision;
import com.finscope.domain.research.agent.ResearchAgentState;
import com.finscope.domain.research.agent.ResearchAgentTraceView;
import com.finscope.domain.research.agent.ResearchToolObservation;
import com.finscope.domain.research.mission.ResearchMission;
import com.finscope.domain.research.mission.ResearchMissionGap;
import com.finscope.domain.research.runtime.ResearchRuntimeCheckpoint;
import com.finscope.service.research.mission.ResearchToolRegistry;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ResearchAgentContextBuilderTest {
    @Test
    void buildsBoundedContextWithLatestGapAndFourMostRecentObservationPairs() {
        ResearchMissionRepository missions = mock(ResearchMissionRepository.class);
        ResearchAgentRepository agents = mock(ResearchAgentRepository.class);
        ResearchRuntimeRepository runtimes = mock(ResearchRuntimeRepository.class);
        ResearchAgentContextBuilder builder = new ResearchAgentContextBuilder(
                missions, agents, runtimes, new ResearchToolRegistry());

        when(missions.findMission(44L)).thenReturn(Optional.of(mission()));
        when(missions.findGaps(44L)).thenReturn(Arrays.asList(gap(1, "SUPPORT"), gap(2, "COUNTER")));
        when(agents.findState(44L)).thenReturn(Optional.of(state()));
        when(agents.findTrace(44L)).thenReturn(trace());
        when(runtimes.findCheckpoint(44L)).thenReturn(Optional.of(checkpoint()));

        ResearchDecisionContext context = builder.build(44L);

        assertEquals(7, context.getNextIteration());
        assertEquals(6, context.getRemainingActions());
        assertEquals("COUNTER", context.getLatestGap().getRecommendedIntent());
        assertTrue(context.getPrompt().contains("observation-6-latest"));
        assertTrue(context.getPrompt().contains("observation-3"));
        assertFalse(context.getPrompt().contains("observation-2-old"));
        assertTrue(context.getPrompt().contains("public_news_search"));
        assertTrue(context.getPrompt().contains("evidence_assess"));
        assertTrue(context.getPrompt().contains(
                "public_news_search：使用 Tavily 补充本次研究证据，搜索材料不进入文章库；"
                        + "input={query=不含协议头的公开搜索词, "
                        + "intent=证据意图（SUPPORT/COUNTER/PRIMARY/UPDATE）}"));
        assertTrue(context.getPrompt().contains(
                "evidence_assess：评估证据数量、独立来源和正反覆盖；input={}"));
        assertFalse(context.getPrompt().contains("queryText"));
        assertFalse(context.getPrompt().contains("researchRunId"));
        assertTrue(context.getPrompt().length() <= ResearchAgentContextBuilder.MAX_PROMPT_CHARACTERS);
    }

    private ResearchMission mission() {
        ResearchMission value = new ResearchMission();
        value.setResearchRunId(44L);
        value.setGoal("AI资本开支是否继续支撑光模块需求？");
        value.setSubject("光模块");
        value.setScopeSummary("需求、供给、兑现和风险");
        value.setSuccessCriteria(Arrays.asList("至少两个独立来源", "同时包含正反证据"));
        value.setPlanVersion(2);
        value.setMaxActions(12);
        return value;
    }

    private ResearchAgentState state() {
        ResearchAgentState value = new ResearchAgentState();
        value.setResearchRunId(44L);
        value.setStatus("DECIDING");
        value.setCurrentSubgoal("补齐反方证据");
        value.setPlanSummary("基线扫描后按证据缺口选择动作");
        value.setMemorySummary(repeat("早期轨迹已经压缩。", 900));
        value.setEvidenceSummary("evidence=6,sources=3,support=6,counter=0");
        value.setAttemptedFingerprints(Arrays.asList("fingerprint-1", "fingerprint-2"));
        value.setDecisionCount(6);
        return value;
    }

    private ResearchRuntimeCheckpoint checkpoint() {
        ResearchRuntimeCheckpoint value = new ResearchRuntimeCheckpoint();
        value.setResearchRunId(44L);
        value.setConsumedActions(6);
        value.setMaxActions(12);
        return value;
    }

    private ResearchMissionGap gap(int index, String intent) {
        ResearchMissionGap value = new ResearchMissionGap();
        value.setAssessmentIndex(index);
        value.setEvidenceCount(index + 4);
        value.setSourceCount(3);
        value.setSupportCount(5);
        value.setCounterCount(index == 2 ? 0 : 1);
        value.setRecommendedIntent(intent);
        value.setWarnings(Collections.singletonList("缺少反方证据"));
        value.setStateHash("gap-" + index);
        return value;
    }

    private ResearchAgentTraceView trace() {
        List<ResearchAgentDecision> decisions = new ArrayList<ResearchAgentDecision>();
        List<ResearchToolObservation> observations = new ArrayList<ResearchToolObservation>();
        for (int index = 1; index <= 6; index++) {
            ResearchAgentDecision decision = new ResearchAgentDecision();
            decision.setId((long) index);
            decision.setIteration(index);
            decision.setDecisionType("TOOL_CALL");
            decision.setToolCode("public_news_search");
            decision.setDecisionSummary("decision-" + index + " " + repeat("研究摘要", 700));
            decisions.add(decision);

            ResearchToolObservation observation = new ResearchToolObservation();
            observation.setDecisionId((long) index);
            observation.setStatus("SUCCESS");
            observation.setObservationSummary(index == 2
                    ? "observation-2-old"
                    : index == 6 ? "observation-6-latest" : "observation-" + index);
            observations.add(observation);
        }
        ResearchAgentTraceView value = new ResearchAgentTraceView();
        value.setDecisions(decisions);
        value.setObservations(observations);
        return value;
    }

    private String repeat(String value, int count) {
        StringBuilder result = new StringBuilder();
        for (int index = 0; index < count; index++) {
            result.append(value);
        }
        return result.toString();
    }
}
