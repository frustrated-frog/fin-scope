package com.finscope.service.radar;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.finscope.dao.radar.RadarAgentTraceRepository;
import com.finscope.domain.radar.RadarEvent;
import com.finscope.domain.radar.RadarEventInterpretation;
import com.finscope.domain.radar.RadarEvidence;
import com.finscope.domain.radar.RadarSignal;
import com.finscope.rpc.llm.LunaResponsesClient.Prepared;
import com.finscope.rpc.llm.LunaResponsesClient.Reply;
import com.finscope.rpc.llm.NewsAiLunaClient;
import org.junit.jupiter.api.Test;

import org.springframework.test.util.ReflectionTestUtils;

import java.net.http.HttpTimeoutException;
import java.util.Arrays;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RadarEventInterpretationAgentTest {
    @Test
    void returnsValidatedEvidenceBoundInterpretation() throws Exception {
        NewsAiLunaClient llm = configuredLlm();
        when(llm.complete(any(Prepared.class))).thenReturn(reply(validJson()));

        RadarEventInterpretation.Result result = agent(llm).interpret(event(),
                Collections.singletonList(signal()), Collections.singletonList(evidence()));

        assertEquals("公司发布新产品，两家来源确认发布事实。", result.getFactSummary());
        assertEquals(Arrays.asList("signal:1", "evidence:31"), result.getEvidenceRefs());
    }

    @Test
    void normalizesSingleTextAnalysisFieldsReturnedByTheModel() throws Exception {
        NewsAiLunaClient llm = configuredLlm();
        String response = validJson()
                .replace("[\"产品发布→量产验证→供应链订单\"]", "\"产品发布→量产验证→供应链订单\"")
                .replace("[\"价格尚未披露\"]", "\"价格尚未披露\"")
                .replace("[\"观察公司正式公告\"]", "\"观察公司正式公告\"");
        when(llm.complete(any(Prepared.class))).thenReturn(reply(response));

        RadarEventInterpretation.Result result = agent(llm).interpret(event(),
                Collections.singletonList(signal()), Collections.singletonList(evidence()));

        assertEquals(Collections.singletonList("产品发布→量产验证→供应链订单"), result.getImpactChain());
        assertEquals(Collections.singletonList("价格尚未披露"), result.getUncertainties());
        assertEquals(Collections.singletonList("观察公司正式公告"), result.getNextObservations());
    }

    @Test
    void shortReportsCanOmitSecondarySections() throws Exception {
        NewsAiLunaClient llm = configuredLlm();
        ObjectMapper json = new ObjectMapper();
        var reply = (ObjectNode) json.readTree(validJson());
        reply.put("newDevelopment", "");
        reply.putArray("impactChain");
        reply.putArray("uncertainties");
        reply.putArray("nextObservations");
        when(llm.complete(any(Prepared.class)))
                .thenReturn(reply(reply.toString()));
        var result = agent(llm).interpret(event(), Collections.singletonList(signal()), Collections.singletonList(evidence()));
        assertEquals("", result.getNewDevelopment());
        assertEquals(Collections.emptyList(), result.getImpactChain());
    }

    @Test
    void rejectsReferencesOutsideTheInputEvidenceSet() throws Exception {
        NewsAiLunaClient llm = configuredLlm();
        when(llm.complete(any(Prepared.class)))
                .thenReturn(reply(validJson().replace("evidence:31", "evidence:999")));

        RadarEventInterpretationAgent.InterpretationException error = assertThrows(
                RadarEventInterpretationAgent.InterpretationException.class,
                () -> agent(llm).interpret(event(), Collections.singletonList(signal()),
                        Collections.singletonList(evidence())));

        assertEquals("INVALID_OUTPUT", error.getCode());
    }

    @Test
    void rejectsInvestmentAdvice() throws Exception {
        NewsAiLunaClient llm = configuredLlm();
        when(llm.complete(any(Prepared.class)))
                .thenReturn(reply(validJson().replace("可能影响供应链订单预期。", "建议买入相关股票。")));

        assertThrows(RadarEventInterpretationAgent.InterpretationException.class,
                () -> agent(llm).interpret(event(), Collections.singletonList(signal()),
                        Collections.singletonList(evidence())));
    }

    @Test
    void rejectsUnknownJsonFields() throws Exception {
        NewsAiLunaClient llm = configuredLlm();
        when(llm.complete(any(Prepared.class)))
                .thenReturn(reply(validJson().replaceFirst("\\{", "{\"recommendation\":\"关注\",")));

        assertThrows(RadarEventInterpretationAgent.InterpretationException.class,
                () -> agent(llm).interpret(event(), Collections.singletonList(signal()),
                        Collections.singletonList(evidence())));
    }

    @Test
    void reportsTheModelTimeoutSeparatelyFromInvalidOutput() throws Exception {
        NewsAiLunaClient llm = configuredLlm();
        when(llm.complete(any(Prepared.class)))
                .thenThrow(new HttpTimeoutException("Read timed out"));

        RadarEventInterpretationAgent.InterpretationException error = assertThrows(
                RadarEventInterpretationAgent.InterpretationException.class,
                () -> agent(llm).interpret(event(), Collections.singletonList(signal()),
                        Collections.singletonList(evidence())));

        assertEquals("LLM_TIMEOUT", error.getCode());
        assertEquals("等待模型回复超时，请重试 AI 解读", error.getMessage());
    }

    @Test
    void reportsUnavailableWithoutCallingTheModel() {
        NewsAiLunaClient llm = mock(NewsAiLunaClient.class);
        when(llm.isConfigured()).thenReturn(false);

        RadarEventInterpretationAgent.InterpretationException error = assertThrows(
                RadarEventInterpretationAgent.InterpretationException.class,
                () -> agent(llm).interpret(event(), Collections.singletonList(signal()),
                        Collections.singletonList(evidence())));

        assertEquals("MODEL_DISABLED", error.getCode());
    }

    private RadarEventInterpretationAgent agent(NewsAiLunaClient llm) {
        return agent(llm, recorder());
    }

    private RadarEventInterpretationAgent agent(NewsAiLunaClient llm, RadarAgentTraceRecorder recorder) {
        var agent = new RadarEventInterpretationAgent();
        ReflectionTestUtils.setField(agent, "llm", llm);
        ReflectionTestUtils.setField(agent, "json", new ObjectMapper());
        ReflectionTestUtils.setField(agent, "traces", recorder);
        agent.configureJson();
        return agent;
    }

    private RadarAgentTraceRecorder recorder() {
        RadarAgentTraceRecorder recorder = new RadarAgentTraceRecorder();
        org.springframework.test.util.ReflectionTestUtils.setField(recorder, "runs", mock(RadarAgentTraceRepository.class));
        return recorder;
    }

    private NewsAiLunaClient configuredLlm() {
        NewsAiLunaClient llm = mock(NewsAiLunaClient.class);
        when(llm.isConfigured()).thenReturn(true);
        when(llm.model()).thenReturn("gpt-6-luna");
        when(llm.prepare(anyString(), anyString(), eq(2048), anyMap())).thenReturn(new Prepared());
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

    private Reply reply(String content) {
        Reply reply = new Reply();
        reply.setText(content);
        reply.setCompleted(true);
        reply.setStatus("completed");
        return reply;
    }

    @Test
    void reportsTruncationWithUsageWithoutTracingReasoningOrPartialText() throws Exception {
        NewsAiLunaClient llm = configuredLlm();
        var recorder = mock(RadarAgentTraceRecorder.class);
        var agent = agent(llm, recorder);
        Reply incomplete = reply("partial text");
        incomplete.setCompleted(false);
        incomplete.setStatus("incomplete");
        incomplete.setIncompleteReason("max_output_tokens");
        incomplete.setOutputTokens(2048L);
        when(llm.complete(any(Prepared.class))).thenReturn(incomplete);
        var error = assertThrows(RadarEventInterpretationAgent.InterpretationException.class,
                () -> agent.interpret(event(), Collections.singletonList(signal()), Collections.emptyList()));
        assertEquals("OUTPUT_LIMIT", error.getCode());
        var metadata = org.mockito.ArgumentCaptor.forClass(String.class);
        org.mockito.Mockito.verify(recorder).record(eq("radar-event-interpretation"), eq("RADAR_EVENT"), eq(10L),
                eq("FALLBACK"), anyString(), eq(""), anyString(), eq("OUTPUT_LIMIT"), org.mockito.ArgumentMatchers.anyLong(),
                metadata.capture());
        var details = new ObjectMapper().readTree(metadata.getValue());
        assertEquals(2048, details.path("outputTokens").asInt());
        assertEquals("max_output_tokens", details.path("finishReason").asText());
        assertEquals("gpt-6-luna", details.path("model").asText());
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
