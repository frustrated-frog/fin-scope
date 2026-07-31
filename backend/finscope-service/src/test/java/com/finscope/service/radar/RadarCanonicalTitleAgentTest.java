package com.finscope.service.radar;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.finscope.dao.agent.AgentRunRepository;
import com.finscope.domain.radar.RadarSignal;
import com.finscope.rpc.llm.LlmChatClient;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RadarCanonicalTitleAgentTest {
    @Test
    void returnsValidatedCanonicalTitle() throws Exception {
        LlmChatClient llm = mock(LlmChatClient.class);
        when(llm.isConfigured()).thenReturn(true);
        when(llm.complete(contains("事件标题编辑"), contains("存储芯片板块持续走强"), eq(15_000), eq(240)))
                .thenReturn("{\"title\":\"存储芯片板块午后集体走强\"}");
        RadarCanonicalTitleAgent agent = agent(llm);

        RadarCanonicalTitleAgent.Result result = agent.generate(Arrays.asList(
                signal(1L, "存储芯片板块持续走强"),
                signal(2L, "芯片股午后集体上涨")), "存储芯片板块持续走强");

        assertEquals("存储芯片板块午后集体走强", result.getTitle());
        assertTrue(result.isGenerated());
    }

    @Test
    void markdownTitleFallsBackToRepresentativeTitle() throws Exception {
        LlmChatClient llm = mock(LlmChatClient.class);
        when(llm.isConfigured()).thenReturn(true);
        when(llm.complete(contains("事件标题编辑"), contains("存储芯片板块持续走强"), eq(15_000), eq(240)))
                .thenReturn("{\"title\":\"## 存储芯片板块午后集体走强\"}");

        RadarCanonicalTitleAgent.Result result = agent(llm).generate(Arrays.asList(
                signal(1L, "存储芯片板块持续走强"),
                signal(2L, "芯片股午后集体上涨")), "存储芯片板块持续走强");

        assertEquals("存储芯片板块持续走强", result.getTitle());
        assertEquals("INVALID_OUTPUT", result.getFallbackReason());
    }

    private RadarCanonicalTitleAgent agent(LlmChatClient llm) {
        return new RadarCanonicalTitleAgent(llm, new ObjectMapper(),
                new RadarAgentTraceRecorder(mock(AgentRunRepository.class)));
    }

    private RadarSignal signal(Long id, String title) {
        RadarSignal value = new RadarSignal();
        value.setId(id);
        value.setItemId("NEWS:" + id);
        value.setProviderCode("SOURCE" + id);
        value.setTitle(title);
        value.setContent(title);
        return value;
    }
}
