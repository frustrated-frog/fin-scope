package com.finscope.service.research.agent;

import com.finscope.domain.research.agent.ResearchAgentDecision;
import com.finscope.domain.research.mission.ResearchMission;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

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
}
