package com.finscope.service.radar;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.finscope.domain.radar.RadarEvent;
import com.finscope.domain.radar.RadarEvidencePlan;
import com.finscope.domain.radar.RadarSignal;
import com.finscope.rpc.llm.LlmChatClient;
import org.junit.jupiter.api.Test;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RadarEvidencePlanAgentTest {
    @Test
    void acceptsStrictWhitelistedPlan() throws Exception {
        LlmChatClient llm = mock(LlmChatClient.class);
        when(llm.isConfigured()).thenReturn(true);
        when(llm.complete(anyString(), anyString(), anyInt(), anyInt())).thenReturn(
                "{\"eventType\":\"COMPANY_EVENT\",\"subject\":\"宁德时代\",\"stockCode\":\"300750\","
                        + "\"actions\":[{\"toolCode\":\"research_material_search\",\"materialType\":\"ANNOUNCEMENT\","
                        + "\"stockCode\":\"300750\",\"query\":\"新电池 量产\"},{\"toolCode\":\"public_news_search\","
                        + "\"materialType\":null,\"stockCode\":null,\"query\":\"宁德时代 新电池\"}]}");
        RadarEvidencePlanAgent agent = new RadarEvidencePlanAgent(llm, new ObjectMapper(), mock(RadarAgentTraceRecorder.class));

        RadarEvidencePlan result = agent.plan(event(), Collections.singletonList(signal()));

        assertEquals(2, result.getActions().size());
        assertEquals("research_material_search", result.getActions().get(0).getToolCode());
    }

    @Test
    void unknownToolFallsBackToFixedPersonalPlan() throws Exception {
        LlmChatClient llm = mock(LlmChatClient.class);
        when(llm.isConfigured()).thenReturn(true);
        when(llm.complete(anyString(), anyString(), anyInt(), anyInt())).thenReturn(
                "{\"eventType\":\"X\",\"subject\":\"X\",\"stockCode\":\"\",\"actions\":[{"
                        + "\"toolCode\":\"shell_exec\",\"materialType\":null,\"stockCode\":null,\"query\":\"任意\"}]}");
        RadarEvidencePlanAgent agent = new RadarEvidencePlanAgent(llm, new ObjectMapper(), mock(RadarAgentTraceRecorder.class));

        RadarEvidencePlan result = agent.plan(event(), Collections.singletonList(signal()));

        assertEquals(2, result.getActions().size());
        assertEquals("300750", result.getStockCode());
        assertEquals("research_material_search", result.getActions().get(0).getToolCode());
        assertEquals("public_news_search", result.getActions().get(1).getToolCode());
    }

    @Test
    void stockCodeInventedByModelCannotEnableStructuredSearch() throws Exception {
        LlmChatClient llm=mock(LlmChatClient.class);when(llm.isConfigured()).thenReturn(true);
        when(llm.complete(anyString(),anyString(),anyInt(),anyInt())).thenReturn("{\"eventType\":\"INDUSTRY\",\"subject\":\"AI\",\"stockCode\":\"300750\","
                + "\"actions\":[{\"toolCode\":\"research_material_search\",\"materialType\":\"ANNOUNCEMENT\",\"stockCode\":\"300750\",\"query\":\"AI公告\"}]}");
        RadarEvidencePlanAgent agent=new RadarEvidencePlanAgent(llm,new ObjectMapper(),mock(RadarAgentTraceRecorder.class));
        RadarSignal noCode=new RadarSignal();noCode.setTitle("AI行业出现新变化");noCode.setContent("多家公司更新产品");

        RadarEvidencePlan result=agent.plan(event(),Collections.singletonList(noCode));

        assertEquals(1,result.getActions().size());
        assertEquals("public_news_search",result.getActions().get(0).getToolCode());
    }

    private RadarEvent event() { RadarEvent value=new RadarEvent();value.setId(9L);value.setCanonicalTitle("宁德时代发布新一代电池");value.setSummary("公司公布量产计划");return value; }
    private RadarSignal signal() { RadarSignal value=new RadarSignal();value.setTitle("新电池发布");value.setContent("股票代码300750，计划量产");return value; }
}
