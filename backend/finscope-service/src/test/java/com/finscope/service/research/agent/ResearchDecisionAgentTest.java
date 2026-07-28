package com.finscope.service.research.agent;

import com.finscope.rpc.llm.LlmChatClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.SocketTimeoutException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ResearchDecisionAgentTest {
    private LlmChatClient llm;
    private ResearchDecisionAgent agent;

    @BeforeEach
    void setUp() {
        llm = mock(LlmChatClient.class);
        ResearchDecisionValidator validator = new ResearchDecisionValidator();
        agent = new ResearchDecisionAgent(llm, validator, new DeterministicResearchPolicy(validator));
    }

    @Test
    void acceptsStrictModelDecisionWithBoundedCall() throws Exception {
        when(llm.isConfigured()).thenReturn(true);
        when(llm.complete(anyString(), anyString(), eq(20000), eq(1200))).thenReturn(validDecisionJson());

        ResearchDecisionResult result = agent.decide(ResearchAgentTestFixtures.counterGapContext());

        assertEquals("MODEL", result.getDecision().getDecisionMode());
        assertEquals("public_news_search", result.getDecision().getToolCode());
        assertEquals("COUNTER", result.getArguments().get("intent"));
        assertNull(result.getFallbackReason());
        verify(llm).complete(anyString(), anyString(), eq(20000), eq(1200));
    }

    @Test
    void rejectsUnknownJsonFieldAndFallsBackToGapDirectedPolicy() throws Exception {
        when(llm.isConfigured()).thenReturn(true);
        when(llm.complete(anyString(), anyString(), eq(20000), eq(1200)))
                .thenReturn(validDecisionJson().replace("\"confidence\":0.83", "\"confidence\":0.83,\"chainOfThought\":\"hidden\""));

        ResearchDecisionResult result = agent.decide(ResearchAgentTestFixtures.counterGapContext());

        assertEquals("DETERMINISTIC", result.getDecision().getDecisionMode());
        assertEquals("public_news_search", result.getDecision().getToolCode());
        assertEquals("COUNTER", result.getArguments().get("intent"));
        assertEquals("DECISION_REJECTED", result.getFallbackReason());
        assertTrue(result.getFallbackDetail().contains("Unrecognized field"));
    }

    @Test
    void classifiesModelTimeoutSeparatelyFromRejectedDecision() throws Exception {
        when(llm.isConfigured()).thenReturn(true);
        when(llm.complete(anyString(), anyString(), eq(20000), eq(1200)))
                .thenThrow(new SocketTimeoutException("Read timed out"));

        ResearchDecisionResult result = agent.decide(ResearchAgentTestFixtures.counterGapContext());

        assertEquals("DETERMINISTIC", result.getDecision().getDecisionMode());
        assertEquals("MODEL_TIMEOUT", result.getFallbackReason());
        assertEquals("模型决策响应超时，已切换规则决策", result.getFallbackDetail());
    }

    @Test
    void fallsBackWhenModelIsDisabled() {
        when(llm.isConfigured()).thenReturn(false);

        ResearchDecisionResult result = agent.decide(ResearchAgentTestFixtures.counterGapContext());

        assertEquals("DETERMINISTIC", result.getDecision().getDecisionMode());
        assertEquals("MODEL_DISABLED", result.getFallbackReason());
        assertEquals("COUNTER", result.getArguments().get("intent"));
    }

    private String validDecisionJson() {
        return "{"
                + "\"decisionType\":\"TOOL_CALL\","
                + "\"currentSubgoal\":\"补齐反方证据\","
                + "\"toolCode\":\"public_news_search\","
                + "\"arguments\":{\"query\":\"AI资本开支 下调 风险\",\"intent\":\"COUNTER\"},"
                + "\"targetGap\":\"缺少反方证据\","
                + "\"expectedObservation\":\"获得独立反方来源\","
                + "\"decisionSummary\":\"当前材料单边，优先验证反方事实\","
                + "\"confidence\":0.83} ";
    }
}
