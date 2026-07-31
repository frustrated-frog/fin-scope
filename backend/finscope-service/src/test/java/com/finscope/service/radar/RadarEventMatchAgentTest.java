package com.finscope.service.radar;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.finscope.dao.agent.AgentRunRepository;
import com.finscope.domain.agent.AgentRun;
import com.finscope.domain.radar.RadarSignal;
import com.finscope.rpc.llm.LlmChatClient;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RadarEventMatchAgentTest {
    @Test
    void acceptsStrictSameEventDecisionAndRecordsTrace() throws Exception {
        LlmChatClient llm = mock(LlmChatClient.class);
        AgentRunRepository runs = mock(AgentRunRepository.class);
        when(llm.isConfigured()).thenReturn(true);
        when(llm.complete(contains("同一现实事件"), contains("存储芯片板块持续走强"), eq(15_000), eq(320)))
                .thenReturn("{\"sameEvent\":true,\"confidence\":0.91,"
                        + "\"reason\":\"同一交易时段、同一板块和相同上涨动作\"}");
        RadarEventMatchAgent agent = agent(llm, runs);

        RadarEventMatchAgent.Decision result = agent.decide(
                signal(1L, "存储芯片板块持续走强"), signal(2L, "芯片股午后集体上涨"));

        assertTrue(result.isSameEvent());
        assertEquals(0.91D, result.getConfidence(), 0.001D);
        assertEquals("AGENT", result.getSource());
        ArgumentCaptor<AgentRun> trace = ArgumentCaptor.forClass(AgentRun.class);
        verify(runs).record(trace.capture());
        assertEquals("radar-event-match", trace.getValue().getNodeName());
        assertEquals("RADAR_CLUSTER", trace.getValue().getSubjectType());
        assertEquals("SUCCESS", trace.getValue().getStatus());
    }

    @Test
    void modelDisabledFallsBackToConservativeSplit() {
        LlmChatClient llm = mock(LlmChatClient.class);
        AgentRunRepository runs = mock(AgentRunRepository.class);
        when(llm.isConfigured()).thenReturn(false);

        RadarEventMatchAgent.Decision result = agent(llm, runs).decide(
                signal(1L, "存储芯片板块持续走强"), signal(2L, "芯片股午后集体上涨"));

        assertFalse(result.isSameEvent());
        assertEquals("FALLBACK", result.getSource());
        assertEquals("MODEL_DISABLED", result.getFallbackReason());
    }

    @Test
    void unknownJsonFieldIsRejectedAndFallsBack() throws Exception {
        LlmChatClient llm = mock(LlmChatClient.class);
        AgentRunRepository runs = mock(AgentRunRepository.class);
        when(llm.isConfigured()).thenReturn(true);
        when(llm.complete(contains("同一现实事件"), contains("存储芯片板块持续走强"), eq(15_000), eq(320)))
                .thenReturn("{\"sameEvent\":true,\"confidence\":0.91,\"reason\":\"相同\",\"chainOfThought\":\"hidden\"}");

        RadarEventMatchAgent.Decision result = agent(llm, runs).decide(
                signal(1L, "存储芯片板块持续走强"), signal(2L, "芯片股午后集体上涨"));

        assertFalse(result.isSameEvent());
        assertEquals("INVALID_OUTPUT", result.getFallbackReason());
    }

    @Test
    void traceWriteFailureDoesNotBreakAgentDecision() throws Exception {
        LlmChatClient llm = mock(LlmChatClient.class);
        AgentRunRepository runs = mock(AgentRunRepository.class);
        when(llm.isConfigured()).thenReturn(true);
        when(llm.complete(contains("同一现实事件"), contains("存储芯片板块持续走强"), eq(15_000), eq(320)))
                .thenReturn("{\"sameEvent\":true,\"confidence\":0.91,\"reason\":\"同一现实事件\"}");
        doThrow(new IllegalStateException("trace database unavailable")).when(runs).record(org.mockito.ArgumentMatchers.any(AgentRun.class));

        RadarEventMatchAgent.Decision result = agent(llm, runs).decide(
                signal(1L, "存储芯片板块持续走强"), signal(2L, "芯片股午后集体上涨"));

        assertTrue(result.isSameEvent());
    }

    private RadarEventMatchAgent agent(LlmChatClient llm, AgentRunRepository runs) {
        return new RadarEventMatchAgent(llm, new ObjectMapper(), new RadarAgentTraceRecorder(runs));
    }

    private RadarSignal signal(Long id, String title) {
        RadarSignal value = new RadarSignal();
        value.setId(id);
        value.setItemId("NEWS:" + id);
        value.setProviderCode("SOURCE" + id);
        value.setCategoryCode("MARKET_MOVE");
        value.setTitle(title);
        value.setContent(title + "，盘中成交活跃，更多信息待确认。");
        value.setPublishedAt(LocalDateTime.of(2026, 7, 31, 14, 0).plusMinutes(id));
        return value;
    }
}
