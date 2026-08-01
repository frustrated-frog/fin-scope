package com.finscope.service.radar;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.finscope.domain.radar.RadarEvent;
import com.finscope.domain.radar.RadarEventInterpretation;
import com.finscope.domain.radar.RadarEvidence;
import com.finscope.domain.radar.RadarSignal;
import com.finscope.rpc.llm.LlmChatClient;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class RadarEventInterpretationAgent {
    static final int TIMEOUT_MS = 20_000;
    static final int MAX_OUTPUT_TOKENS = 900;
    private static final int MAX_ITEMS = 8;
    private static final int MAX_LIST_ITEM_LENGTH = 180;

    private final LlmChatClient llm;
    private final ObjectMapper json;
    private final RadarAgentTraceRecorder traces;

    public RadarEventInterpretationAgent(LlmChatClient llm, ObjectMapper json, RadarAgentTraceRecorder traces) {
        this.llm = llm;
        this.json = json.copy().enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION)
                .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
        this.traces = traces;
    }

    public RadarEventInterpretation.Result interpret(RadarEvent event, List<RadarSignal> signals,
                                                     List<RadarEvidence> evidence) {
        long started = System.currentTimeMillis();
        if (llm == null || !llm.isConfigured()) {
            trace(event, "UNAVAILABLE", "MODEL_DISABLED", "MODEL_DISABLED", started, null);
            throw new InterpretationException("MODEL_DISABLED", "模型未配置");
        }
        try {
            String raw = llm.complete(systemPrompt(), json.writeValueAsString(payload(event, signals, evidence)),
                    TIMEOUT_MS, MAX_OUTPUT_TOKENS);
            RadarEventInterpretation.Result result = parseResult(raw);
            validate(result, allowedRefs(signals, evidence));
            trace(event, "SUCCESS", null, null, started, result);
            return result;
        } catch (InterpretationException error) {
            trace(event, "FALLBACK", error.getClass().getSimpleName(), error.getCode(), started, null);
            throw error;
        } catch (Exception error) {
            trace(event, "FALLBACK", error.getClass().getSimpleName(), "INVALID_OUTPUT", started, null);
            throw new InterpretationException("INVALID_OUTPUT", "模型解读输出不可用", error);
        }
    }

    public String modelName() {
        return llm == null ? "unconfigured" : llm.modelName();
    }

    private void validate(RadarEventInterpretation.Result result, Set<String> allowedRefs) {
        if (result == null || blank(result.getFactSummary()) || blank(result.getNewDevelopment())
                || blank(result.getWhyItMatters())) {
            throw new InterpretationException("INVALID_OUTPUT", "解读必填字段为空");
        }
        validateList(result.getImpactChain(), false);
        validateList(result.getUncertainties(), false);
        validateList(result.getNextObservations(), false);
        validateList(result.getEvidenceRefs(), true);
        for (String ref : safe(result.getEvidenceRefs())) {
            if (!allowedRefs.contains(ref)) {
                throw new InterpretationException("INVALID_OUTPUT", "解读引用不属于输入证据");
            }
        }
        String all = result.getFactSummary() + result.getNewDevelopment() + result.getWhyItMatters()
                + String.join("", safe(result.getImpactChain())) + String.join("", safe(result.getUncertainties()))
                + String.join("", safe(result.getNextObservations()));
        if (containsAdvice(all)) throw new InterpretationException("INVALID_OUTPUT", "解读包含投资建议");
    }

    private void validateList(List<String> values, boolean allowEmpty) {
        List<String> safeValues = safe(values);
        if ((!allowEmpty && safeValues.isEmpty()) || safeValues.size() > MAX_ITEMS) {
            throw new InterpretationException("INVALID_OUTPUT", "解读列表数量不合法");
        }
        for (String value : safeValues) {
            if (blank(value) || value.trim().length() > MAX_LIST_ITEM_LENGTH) {
                throw new InterpretationException("INVALID_OUTPUT", "解读列表内容不合法");
            }
        }
    }

    private Map<String, Object> payload(RadarEvent event, List<RadarSignal> signals, List<RadarEvidence> evidence) {
        Map<String, Object> value = new LinkedHashMap<String, Object>();
        value.put("eventTitle", event == null ? "" : event.getCanonicalTitle());
        value.put("eventSummary", compact(event == null ? "" : event.getSummary(), 600));
        value.put("existingUncertainty", compact(event == null ? "" : event.getUncertainty(), 240));
        value.put("existingNextObservation", compact(event == null ? "" : event.getNextObservation(), 240));
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
        for (RadarSignal signal : first(signals)) refs.add("signal:" + signal.getId());
        for (RadarEvidence item : first(evidence)) refs.add("evidence:" + item.getId());
        return refs;
    }

    private String systemPrompt() {
        return "你是个人研究雷达的事件解读 Agent。只能使用输入中的事件、原始信号和证据，不得补充外部事实。"
                + "输出单个纯JSON对象，只允许factSummary、newDevelopment、whyItMatters、impactChain、uncertainties、"
                + "nextObservations、evidenceRefs字段。factSummary、newDevelopment、whyItMatters必须是字符串；"
                + "impactChain、uncertainties、nextObservations、evidenceRefs必须是字符串数组，即使只有一项也必须使用数组。"
                + "清楚区分事实、影响推演与未知项；每个判断引用输入ref；"
                + "不得给出买卖、仓位或目标价建议，不得输出Markdown或思维链。";
    }

    private RadarEventInterpretation.Result parseResult(String raw) throws Exception {
        JsonNode root = json.readTree(extractJson(raw));
        if (root == null || !root.isObject()) throw new IllegalArgumentException("解读输出必须是JSON对象");
        ObjectNode object = (ObjectNode) root;
        normalizeTextArray(object, "impactChain");
        normalizeTextArray(object, "uncertainties");
        normalizeTextArray(object, "nextObservations");
        return json.treeToValue(object, RadarEventInterpretation.Result.class);
    }

    private void normalizeTextArray(ObjectNode object, String field) {
        JsonNode value = object.get(field);
        if (value != null && value.isTextual()) {
            object.set(field, json.createArrayNode().add(value.asText()));
        }
    }

    private void trace(RadarEvent event, String status, String errorType, String fallbackReason,
                       long started, RadarEventInterpretation.Result result) {
        traces.record("radar-event-interpretation", "RADAR_EVENT", event == null ? null : event.getId(),
                status, "event=" + (event == null ? "" : compact(event.getCanonicalTitle(), 120)),
                result == null ? "" : "summary=" + compact(result.getFactSummary(), 240),
                errorType, fallbackReason, System.currentTimeMillis() - started,
                "{\"model\":\"" + safeModelName() + "\"}");
    }

    private String safeModelName() {
        String value = modelName();
        return value == null ? "llm" : value.replaceAll("[^a-zA-Z0-9._-]", "_");
    }

    private String extractJson(String raw) {
        if (raw == null) return "";
        int start = raw.indexOf('{'), end = raw.lastIndexOf('}');
        return start >= 0 && end >= start ? raw.substring(start, end + 1) : raw.trim();
    }

    private <T> List<T> first(List<T> values) {
        if (values == null || values.isEmpty()) return java.util.Collections.emptyList();
        return values.size() <= MAX_ITEMS ? values : values.subList(0, MAX_ITEMS);
    }

    private List<String> safe(List<String> values) {
        return values == null ? java.util.Collections.<String>emptyList() : values;
    }

    private boolean containsAdvice(String value) {
        return value.contains("买入") || value.contains("卖出") || value.contains("加仓")
                || value.contains("减仓") || value.contains("目标价");
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
