package com.finscope.service.radar;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.finscope.dao.radar.RadarAgentTraceRepository;
import com.finscope.domain.radar.RadarEvent;
import com.finscope.domain.radar.RadarEventInterpretation;
import com.finscope.domain.radar.RadarEvidence;
import com.finscope.domain.radar.RadarSignal;
import com.finscope.rpc.llm.LlmChatClient;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RadarEventInterpretationAgentTest {
    @Test
    void returnsValidatedEvidenceBoundInterpretation() throws Exception {
        LlmChatClient llm = configuredLlm();
        when(llm.complete(anyString(), anyString(), eq(20_000), eq(900))).thenReturn(validJson());

        RadarEventInterpretation.Result result = agent(llm).interpret(event(),
                Collections.singletonList(signal()), Collections.singletonList(evidence()));

        assertEquals("公司发布新产品，两家来源确认发布事实。", result.getFactSummary());
        assertEquals(Arrays.asList("signal:1", "evidence:31"), result.getEvidenceRefs());
    }

    @Test
    void normalizesSingleTextAnalysisFieldsReturnedByTheModel() throws Exception {
        LlmChatClient llm = configuredLlm();
        String response = validJson()
                .replace("[\"产品发布→量产验证→供应链订单\"]", "\"产品发布→量产验证→供应链订单\"")
                .replace("[\"价格尚未披露\"]", "\"价格尚未披露\"")
                .replace("[\"观察公司正式公告\"]", "\"观察公司正式公告\"");
        when(llm.complete(anyString(), anyString(), eq(20_000), eq(900))).thenReturn(response);

        RadarEventInterpretation.Result result = agent(llm).interpret(event(),
                Collections.singletonList(signal()), Collections.singletonList(evidence()));

        assertEquals(Collections.singletonList("产品发布→量产验证→供应链订单"), result.getImpactChain());
        assertEquals(Collections.singletonList("价格尚未披露"), result.getUncertainties());
        assertEquals(Collections.singletonList("观察公司正式公告"), result.getNextObservations());
    }

    @Test
    void rejectsReferencesOutsideTheInputEvidenceSet() throws Exception {
        LlmChatClient llm = configuredLlm();
        when(llm.complete(anyString(), anyString(), eq(20_000), eq(900)))
                .thenReturn(validJson().replace("evidence:31", "evidence:999"));

        RadarEventInterpretationAgent.InterpretationException error = assertThrows(
                RadarEventInterpretationAgent.InterpretationException.class,
                () -> agent(llm).interpret(event(), Collections.singletonList(signal()),
                        Collections.singletonList(evidence())));

        assertEquals("INVALID_OUTPUT", error.getCode());
    }

    @Test
    void rejectsInvestmentAdvice() throws Exception {
        LlmChatClient llm = configuredLlm();
        when(llm.complete(anyString(), anyString(), eq(20_000), eq(900)))
                .thenReturn(validJson().replace("可能影响供应链订单预期。", "建议买入相关股票。"));

        assertThrows(RadarEventInterpretationAgent.InterpretationException.class,
                () -> agent(llm).interpret(event(), Collections.singletonList(signal()),
                        Collections.singletonList(evidence())));
    }

    @Test
    void rejectsUnknownJsonFields() throws Exception {
        LlmChatClient llm = configuredLlm();
        when(llm.complete(anyString(), anyString(), eq(20_000), eq(900)))
                .thenReturn(validJson().replaceFirst("\\{", "{\"recommendation\":\"关注\","));

        assertThrows(RadarEventInterpretationAgent.InterpretationException.class,
                () -> agent(llm).interpret(event(), Collections.singletonList(signal()),
                        Collections.singletonList(evidence())));
    }

    @Test
    void reportsUnavailableWithoutCallingTheModel() {
        LlmChatClient llm = mock(LlmChatClient.class);
        when(llm.isConfigured()).thenReturn(false);

        RadarEventInterpretationAgent.InterpretationException error = assertThrows(
                RadarEventInterpretationAgent.InterpretationException.class,
                () -> agent(llm).interpret(event(), Collections.singletonList(signal()),
                        Collections.singletonList(evidence())));

        assertEquals("MODEL_DISABLED", error.getCode());
    }

    private RadarEventInterpretationAgent agent(LlmChatClient llm) {
        return new RadarEventInterpretationAgent(llm, new ObjectMapper(),
                recorder());
    }

    private RadarAgentTraceRecorder recorder() {
        RadarAgentTraceRecorder recorder = new RadarAgentTraceRecorder();
        org.springframework.test.util.ReflectionTestUtils.setField(recorder, "runs", mock(RadarAgentTraceRepository.class));
        return recorder;
    }

    private LlmChatClient configuredLlm() {
        LlmChatClient llm = mock(LlmChatClient.class);
        when(llm.isConfigured()).thenReturn(true);
        when(llm.modelName()).thenReturn("test-model");
        return llm;
    }

    private RadarEvent event() {
        RadarEvent value = new RadarEvent(); value.setId(10L);
        value.setCanonicalTitle("宁德时代发布新一代电池"); value.setSummary("新品正式发布");
        value.setUncertainty("价格待确认"); value.setNextObservation("观察公司公告"); return value;
    }

    private RadarSignal signal() {
        RadarSignal value = new RadarSignal(); value.setId(1L); value.setSourceName("财联社");
        value.setTitle("宁德时代发布新一代电池"); value.setContent("新品正式发布并披露量产节奏"); return value;
    }

    private RadarEvidence evidence() {
        RadarEvidence value = new RadarEvidence(); value.setId(31L); value.setSourceName("深交所");
        value.setTitle("公司公告"); value.setSummary("公司披露新产品量产安排"); return value;
    }

    private String validJson() {
        return "{\"factSummary\":\"公司发布新产品，两家来源确认发布事实。\","
                + "\"newDevelopment\":\"新增量产时间信息。\","
                + "\"whyItMatters\":\"可能影响供应链订单预期。\","
                + "\"impactChain\":[\"产品发布→量产验证→供应链订单\"],"
                + "\"uncertainties\":[\"价格尚未披露\"],"
                + "\"nextObservations\":[\"观察公司正式公告\"],"
                + "\"evidenceRefs\":[\"signal:1\",\"evidence:31\"]}";
    }
}
