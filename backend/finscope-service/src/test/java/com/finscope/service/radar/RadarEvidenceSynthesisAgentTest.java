package com.finscope.service.radar;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.finscope.domain.radar.RadarEvent;
import com.finscope.domain.radar.RadarEvidence;
import com.finscope.rpc.llm.LlmChatClient;
import org.junit.jupiter.api.Test;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RadarEvidenceSynthesisAgentTest {
    @Test
    void producesStrictBeginnerFriendlyEvidenceConclusion() throws Exception {
        LlmChatClient llm=mock(LlmChatClient.class);when(llm.isConfigured()).thenReturn(true);
        when(llm.complete(anyString(),anyString(),anyInt(),anyInt())).thenReturn("{\"summary\":\"公司公告确认新产品发布，量产节奏仍待披露\","
                + "\"mainDriver\":\"公司正式公告\",\"conflictOrGap\":\"尚无明确量产日期\",\"nextObservation\":\"关注后续量产公告\"}");
        RadarEvidenceSynthesisAgent agent=new RadarEvidenceSynthesisAgent(llm,new ObjectMapper(),mock(RadarAgentTraceRecorder.class));

        RadarEvidenceSynthesisAgent.Result result=agent.synthesize(event(),Collections.singletonList(evidence()));

        assertTrue(result.isGenerated());
        assertEquals("公司公告确认新产品发布，量产节奏仍待披露",result.getSummary());
        assertEquals("关注后续量产公告",result.getNextObservation());
    }

    @Test
    void investmentAdviceInOutputUsesDeterministicFallback() throws Exception {
        LlmChatClient llm=mock(LlmChatClient.class);when(llm.isConfigured()).thenReturn(true);
        when(llm.complete(anyString(),anyString(),anyInt(),anyInt())).thenReturn("{\"summary\":\"建议买入\",\"mainDriver\":\"利好\",\"conflictOrGap\":\"无\",\"nextObservation\":\"加仓\"}");
        RadarEvidenceSynthesisAgent agent=new RadarEvidenceSynthesisAgent(llm,new ObjectMapper(),mock(RadarAgentTraceRecorder.class));

        RadarEvidenceSynthesisAgent.Result result=agent.synthesize(event(),Collections.singletonList(evidence()));

        assertFalse(result.isGenerated());
        assertTrue(result.getSummary().contains("1条"));
    }

    private RadarEvent event(){RadarEvent value=new RadarEvent();value.setId(8L);value.setCanonicalTitle("宁德时代发布新电池");return value;}
    private RadarEvidence evidence(){RadarEvidence value=new RadarEvidence();value.setEvidenceType("ANNOUNCEMENT");value.setTitle("公司公告");value.setSummary("公司确认产品发布");value.setSourceName("深交所");return value;}
}
