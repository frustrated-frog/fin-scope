package com.finscope.service.research.agent;

import com.finscope.domain.research.agent.ResearchAgentDecision;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

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
}
