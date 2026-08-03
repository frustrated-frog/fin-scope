package com.finscope.service.research.agent;

import com.finscope.domain.research.agent.ResearchAgentDecision;
import com.finscope.domain.research.mission.ResearchMission;
import com.finscope.domain.research.mission.ResearchMissionTask;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DeterministicResearchPolicyTest {
    private final ResearchDecisionValidator validator = new ResearchDecisionValidator();
    private final DeterministicResearchPolicy policy = new DeterministicResearchPolicy(validator);

    @Test
    void choosesCounterSearchForOneSidedEvidenceAndAvoidsAttemptedFingerprint() {
        ResearchDecisionContext context = ResearchAgentTestFixtures.counterGapContext();
        ResearchAgentDecision first = policy.decide(context);
        context.setAttemptedFingerprints(Arrays.asList(first.getActionFingerprint()));

        ResearchAgentDecision second = policy.decide(context);

        assertEquals("public_news_search", first.getToolCode());
        assertEquals("public_news_search", second.getToolCode());
        assertNotEquals(first.getActionFingerprint(), second.getActionFingerprint());
        assertNotEquals(first.getArgumentsJson(), second.getArgumentsJson());
    }

    @Test
    void requestsFinishForSufficientEvidenceAndAbortsWhenBudgetIsExhausted() {
        ResearchDecisionContext sufficient = ResearchAgentTestFixtures.sufficientContext();
        assertEquals("FINISH", policy.decide(sufficient).getDecisionType());

        sufficient.setRemainingActions(0);
        sufficient.getLatestGap().setSufficient(false);
        sufficient.getLatestGap().setRecommendedIntent("COUNTER");
        assertEquals("ABORT", policy.decide(sufficient).getDecisionType());
    }

    @Test
    void choosesStructuredPrimaryMaterialForAStockSubject() {
        ResearchDecisionContext context = ResearchAgentTestFixtures.counterGapContext();
        context.getLatestGap().setCounterCount(1);
        context.getLatestGap().setSupportCount(1);
        context.getLatestGap().setSourceCount(1);
        context.getLatestGap().setRecommendedIntent("PRIMARY");
        ResearchMission mission = context.getMission();
        mission.setSubject("平安银行（000001）");

        ResearchAgentDecision decision = policy.decide(context);

        assertEquals("research_material_search", decision.getToolCode());
        assertTrue(decision.getArgumentsJson().contains("000001"));
        assertTrue(decision.getArgumentsJson().contains("ANNOUNCEMENT"));
    }

    @Test
    void startsTheFirstReadySearchTaskBeforeAnEvidenceGapExists() {
        ResearchDecisionContext context = ResearchAgentTestFixtures.counterGapContext();
        context.setLatestGap(null);
        ResearchMissionTask task = new ResearchMissionTask();
        task.setTaskKey("search_primary");
        task.setTitle("公司一手资料搜索");
        task.setTaskType("SEARCH");
        task.setToolCode("research_material_search");
        task.setIntent("PRIMARY");
        task.setStatus("PENDING");
        task.setDependencies(Collections.<String>emptyList());
        task.setQueryText("000001 ANNOUNCEMENT 财报 经营 订单");
        task.setRationale("先核验公司披露");
        task.setExpectedEvidence("获得可追溯的一手资料");
        context.setTasks(Collections.singletonList(task));

        ResearchAgentDecision decision = policy.decide(context);

        assertEquals("TOOL_CALL", decision.getDecisionType());
        assertEquals("search_primary", decision.getMissionTaskKey());
        assertEquals("research_material_search", decision.getToolCode());
        assertTrue(decision.getArgumentsJson().contains("000001"));
        assertEquals("NO_GAP", decision.getTargetGap());
    }
}
