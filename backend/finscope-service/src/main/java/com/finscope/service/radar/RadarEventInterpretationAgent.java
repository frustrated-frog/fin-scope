package com.finscope.service.radar;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.finscope.domain.news.NewsReading;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.finscope.domain.radar.RadarEvent;
import com.finscope.domain.radar.RadarEventInterpretation;
import com.finscope.domain.radar.RadarEvidence;
import com.finscope.domain.radar.RadarSignal;
import com.finscope.rpc.llm.LunaResponsesClient.ProviderHttpException;
import com.finscope.rpc.llm.NewsAiLunaClient;
import com.finscope.service.news.NewsReadingGuidance;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import java.net.http.HttpTimeoutException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

@Service
public class RadarEventInterpretationAgent {
    static final int MAX_OUTPUT_TOKENS = 2048;
    private static final int MAX_ITEMS = 8;
    private static final Map<String, Object> RESULT_SCHEMA = Map.of(
            "type", "object", "additionalProperties", false, "required", List.of("reading", "evidenceRefs"),
            "properties", Map.of("reading", NewsReadingGuidance.SCHEMA, "evidenceRefs",
                    Map.of("type", "array", "maxItems", MAX_ITEMS, "items", Map.of("type", "string"))));
    private static final String PROMPT = NewsReadingGuidance.PROMPT
            + "reading 是读者看到的讲解。实际用到的输入 ref 放进 evidenceRefs，原样保留编号。";

    // 对读者发出的交易指令与解释报道里的交易动作含义不同。
    private static final Pattern TRADING_ADVICE = Pattern.compile(
            "(?:建议(?:投资者|你)?|你(?:应该|可以)|不妨|适合投资者)(?:立即|择机|逢低|适当)?(?:买入|卖出|加仓|减仓)"
                    + "|(?:你的|建议的)目标价");

    private record CitedReading(NewsReading reading, List<String> evidenceRefs) {
    }

    @Resource
    private NewsAiLunaClient llm;
    @Resource
    private RadarAgentTraceRecorder traces;
    @Resource
    private ObjectMapper json;

    @PostConstruct
    void configureJson() {
        json = json.copy().enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION)
                .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
    }

    public RadarEventInterpretation.Result interpret(RadarEvent event, List<RadarSignal> signals,
                                                     List<RadarEvidence> evidence) {
        long started = System.currentTimeMillis();
        ObjectNode metadata = json.createObjectNode().put("model", safeModelName());
        if (llm == null || !llm.isConfigured()) {
            trace(event, "UNAVAILABLE", "MODEL_DISABLED", "MODEL_DISABLED", started, null, metadata);
            throw new InterpretationException("MODEL_DISABLED", "模型未配置");
        }
        try {
            var request = llm.prepare(PROMPT, json.writeValueAsString(payload(event, signals, evidence)),
                    MAX_OUTPUT_TOKENS, RESULT_SCHEMA);
            var reply = llm.complete(request);
            metadata.put("finishReason", reply.getIncompleteReason() == null ? reply.getStatus() : reply.getIncompleteReason());
            metadata.put("inputTokens", reply.getInputTokens());
            metadata.put("outputTokens", reply.getOutputTokens());
            metadata.put("reasoningTokens", reply.getReasoningTokens());
            if ("max_output_tokens".equals(reply.getIncompleteReason())) {
                throw new InterpretationException("OUTPUT_LIMIT", "模型达到输出上限，解读未写完整");
            }
            if (reply.isRefused() || !reply.isCompleted()) {
                throw new InterpretationException("INCOMPLETE_OUTPUT", "模型未返回完整解读");
            }
            if (blank(reply.getText())) {
                throw new InterpretationException("EMPTY_OUTPUT", "模型返回的解读正文为空");
            }
            RadarEventInterpretation.Result result = parseResult(reply.getText(), allowedRefs(signals, evidence));
            trace(event, "SUCCESS", null, null, started, result, metadata);
            return result;
        } catch (InterpretationException error) {
            trace(event, "FALLBACK", error.getClass().getSimpleName(), error.getCode(), started, null, metadata);
            throw error;
        } catch (ProviderHttpException error) {
            metadata.put("httpStatus", error.status());
            String code = "LLM_HTTP_" + error.status();
            trace(event, "FALLBACK", error.getClass().getSimpleName(), code, started, null, metadata);
            throw new InterpretationException(code, "模型服务返回 HTTP " + error.status() + "，解读未生成", error);
        } catch (HttpTimeoutException error) {
            trace(event, "FALLBACK", error.getClass().getSimpleName(), "LLM_TIMEOUT", started, null, metadata);
            throw new InterpretationException("LLM_TIMEOUT", "等待模型回复超时，请重试 AI 解读", error);
        } catch (JsonProcessingException | IllegalArgumentException error) {
            trace(event, "FALLBACK", error.getClass().getSimpleName(), "INVALID_OUTPUT", started, null, metadata);
            throw new InterpretationException("INVALID_OUTPUT", "模型解读输出不可用", error);
        } catch (Exception error) {
            if (error instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            trace(event, "FALLBACK", error.getClass().getSimpleName(), "LLM_REQUEST_FAILED", started, null, metadata);
            throw new InterpretationException("LLM_REQUEST_FAILED", "模型请求未完成，请重试 AI 解读", error);
        }
    }

    public String modelName() {
        return llm == null ? "unconfigured" : llm.model();
    }

    private Map<String, Object> payload(RadarEvent event, List<RadarSignal> signals, List<RadarEvidence> evidence) {
        Map<String, Object> value = new LinkedHashMap<String, Object>();
        value.put("eventTitle", event == null ? "" : event.getCanonicalTitle());
        value.put("eventSummary", compact(event == null ? "" : event.getSummary(), 600));
        List<Map<String, String>> signalRows = new ArrayList<Map<String, String>>();
        for (RadarSignal signal : first(signals)) {
            Map<String, String> row = new LinkedHashMap<String, String>();
            row.put("ref", "signal:" + signal.getId()); row.put("source", signal.getSourceName());
            row.put("title", compact(signal.getTitle(), 180)); row.put("content", compact(signal.getContent(), 500));
            signalRows.add(row);
        }
        List<Map<String, String>> evidenceRows = new ArrayList<Map<String, String>>();
        for (RadarEvidence item : first(evidence)) {
            Map<String, String> row = new LinkedHashMap<String, String>();
            row.put("ref", "evidence:" + item.getId()); row.put("source", item.getSourceName());
            row.put("title", compact(item.getTitle(), 180)); row.put("content", compact(item.getSummary(), 500));
            evidenceRows.add(row);
        }
        value.put("signals", signalRows); value.put("evidence", evidenceRows);
        return value;
    }

    private Set<String> allowedRefs(List<RadarSignal> signals, List<RadarEvidence> evidence) {
        Set<String> refs = new HashSet<String>();
        for (RadarSignal signal : first(signals)) {
            refs.add("signal:" + signal.getId());
        }
        for (RadarEvidence item : first(evidence)) {
            refs.add("evidence:" + item.getId());
        }
        return refs;
    }

    private RadarEventInterpretation.Result parseResult(String raw, Set<String> allowedRefs) throws JsonProcessingException {
        CitedReading output = json.readValue(raw, CitedReading.class);
        if (output == null || output.evidenceRefs() == null || output.evidenceRefs().size() > MAX_ITEMS
                || !allowedRefs.containsAll(output.evidenceRefs())) {
            throw new InterpretationException("INVALID_OUTPUT", "解读引用不属于输入证据");
        }
        NewsReadingGuidance.validate(output.reading());
        if (TRADING_ADVICE.matcher(raw).find()) {
            throw new InterpretationException("INVALID_OUTPUT", "解读包含投资建议");
        }
        var result = new RadarEventInterpretation.Result();
        result.setReading(output.reading());
        result.setFactSummary(output.reading().lead());
        result.setEvidenceRefs(output.evidenceRefs());
        return result;
    }

    private void trace(RadarEvent event, String status, String errorType, String fallbackReason,
                       long started, RadarEventInterpretation.Result result, ObjectNode metadata) {
        traces.record("radar-event-interpretation", "RADAR_EVENT", event == null ? null : event.getId(),
                status, "event=" + (event == null ? "" : compact(event.getCanonicalTitle(), 120)),
                result == null ? "" : "summary=" + compact(result.getFactSummary(), 240),
                errorType, fallbackReason, System.currentTimeMillis() - started,
                metadata.toString());
    }

    private String safeModelName() {
        String value = modelName();
        return value == null ? "llm" : value.replaceAll("[^a-zA-Z0-9._-]", "_");
    }

    private <T> List<T> first(List<T> values) {
        if (values == null || values.isEmpty()) return java.util.Collections.emptyList();
        return values.size() <= MAX_ITEMS ? values : values.subList(0, MAX_ITEMS);
    }

    private String compact(String value, int max) {
        String result = value == null ? "" : value.replaceAll("[\\r\\n\\t]+", " ").trim();
        return result.length() <= max ? result : result.substring(0, max);
    }

    private boolean blank(String value) { return value == null || value.trim().isEmpty(); }

    public static class InterpretationException extends RuntimeException {
        private final String code;
        InterpretationException(String code, String message) { super(message); this.code = code; }
        InterpretationException(String code, String message, Throwable cause) { super(message, cause); this.code = code; }
        public String getCode() { return code; }
    }
}
