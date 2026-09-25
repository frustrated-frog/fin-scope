package com.finscope.service.intake;

import com.finscope.dao.agent.AgentRunRepository;
import com.finscope.domain.intake.IntakeCandidate;
import com.finscope.domain.intake.IntakeEnums;
import com.finscope.rpc.llm.LlmChatClient;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CandidateReviewAgentModelSwitchTest {
    @Test
    void scheduledModelSwitchUsesRuleFallbackWithoutCallingConfiguredClient() throws Exception {
        LlmChatClient llm = mock(LlmChatClient.class);
        when(llm.isConfigured()).thenReturn(true);
        CandidateReviewAgent agent = new CandidateReviewAgent();
        ReflectionTestUtils.setField(agent, "llmChatClient", llm);
        ReflectionTestUtils.setField(agent, "agentRunRepository", mock(AgentRunRepository.class));
        IntakeCandidate candidate = new IntakeCandidate();
        candidate.setOriginalTitle("美联储利率决议");
        candidate.setOriginalSummary("等待人工复核");

        CandidateReviewAgent.ReviewResult result = agent.reviewWithResult(candidate, false);

        assertEquals(IntakeEnums.AGENT_FALLBACK, result.getStatus());
        assertNull(result.getErrorMessage());
        verify(llm, never()).complete(anyString(), anyString());
    }

    @Test
    void enabledModelStillReviewsManuallyFetchedCandidate() throws Exception {
        LlmChatClient llm = mock(LlmChatClient.class);
        when(llm.isConfigured()).thenReturn(true);
        when(llm.complete(anyString(), anyString())).thenReturn("{\"chineseTitle\":\"利率决议\","
                + "\"decisionSummary\":\"值得复核\",\"score\":80,\"recommendation\":\"PROMOTABLE\"}");
        CandidateReviewAgent agent = new CandidateReviewAgent();
        ReflectionTestUtils.setField(agent, "llmChatClient", llm);
        ReflectionTestUtils.setField(agent, "agentRunRepository", mock(AgentRunRepository.class));
        IntakeCandidate candidate = new IntakeCandidate();
        candidate.setOriginalTitle("美联储利率决议");

        CandidateReviewAgent.ReviewResult result = agent.reviewWithResult(candidate, true);

        assertEquals(IntakeEnums.AGENT_SUCCESS, result.getStatus());
        verify(llm).complete(anyString(), anyString());
    }
}
