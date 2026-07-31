package com.finscope.service.radar;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.finscope.domain.radar.RadarSignal;
import com.finscope.rpc.llm.LlmChatClient;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class RadarEventMatchAgent {
    static final int TIMEOUT_MS = 15_000;
    static final int MAX_OUTPUT_TOKENS = 320;
    private static final int CONTENT_LIMIT = 500;
    private static final int REASON_LIMIT = 160;

    private final LlmChatClient llm;
    private final ObjectMapper json;
    private final RadarAgentTraceRecorder traces;

    public RadarEventMatchAgent(LlmChatClient llm, ObjectMapper json, RadarAgentTraceRecorder traces) {
        this.llm = llm;
        this.json = json.copy()
                .enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION)
                .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
        this.traces = traces;
    }

    public Decision decide(RadarSignal left, RadarSignal right) {
        long started = System.currentTimeMillis();
        String input = inputSummary(left, right);
        if (llm == null || !llm.isConfigured()) {
            Decision fallback = Decision.fallback("MODEL_DISABLED", "模型未配置，灰区信号保守拆分");
            trace(left, fallback, input, started, null);
            return fallback;
        }
        try {
            String raw = llm.complete(systemPrompt(), userPrompt(left, right), TIMEOUT_MS, MAX_OUTPUT_TOKENS);
            if (raw == null || raw.trim().isEmpty() || raw.length() > 2_000) {
                throw new IllegalArgumentException("模型返回为空或超过长度限制");
            }
            Draft draft = json.readValue(extractJson(raw), Draft.class);
            validate(draft);
            Decision decision = new Decision(draft.sameEvent, draft.confidence, draft.reason.trim(), "AGENT", null);
            trace(left, decision, input, started, null);
            return decision;
        } catch (Exception error) {
            Decision fallback = Decision.fallback("INVALID_OUTPUT", "模型判断不可用，灰区信号保守拆分");
            trace(left, fallback, input, started, error.getClass().getSimpleName());
            return fallback;
        }
    }

    private void validate(Draft draft) {
        if (draft == null || draft.sameEvent == null || draft.confidence == null
                || draft.confidence < 0D || draft.confidence > 1D
                || blank(draft.reason) || draft.reason.trim().length() > REASON_LIMIT) {
            throw new IllegalArgumentException("模型判定字段不合法");
        }
    }

    private String systemPrompt() {
        return "你是金融资讯事件匹配 Agent。判断两条资讯是否描述同一现实事件，而不是仅仅主题相近。"
                + "只有主体、核心动作、时间窗口和关键事实相容时才能判为同一事件。"
                + "输出单个纯 JSON 对象，只允许 sameEvent、confidence、reason 三个字段；"
                + "不得输出 Markdown、思维链或额外字段。";
    }

    private String userPrompt(RadarSignal left, RadarSignal right) throws Exception {
        Map<String, Object> payload = new LinkedHashMap<String, Object>();
        payload.put("left", signalPayload(left));
        payload.put("right", signalPayload(right));
        return json.writeValueAsString(payload);
    }

    private Map<String, Object> signalPayload(RadarSignal signal) {
        Map<String, Object> value = new LinkedHashMap<String, Object>();
        value.put("itemId", signal == null ? null : signal.getItemId());
        value.put("category", signal == null ? null : signal.getCategoryCode());
        value.put("title", signal == null ? null : signal.getTitle());
        value.put("content", signal == null ? null : limit(signal.getContent(), CONTENT_LIMIT));
        value.put("publishedAt", signal == null || signal.getPublishedAt() == null
                ? null : signal.getPublishedAt().toString());
        return value;
    }

    private void trace(RadarSignal left, Decision decision, String input, long started, String errorType) {
        traces.record("radar-event-match", "RADAR_CLUSTER", left == null ? null : left.getId(),
                "AGENT".equals(decision.source) ? "SUCCESS" : "FALLBACK", input,
                "sameEvent=" + decision.sameEvent + ", confidence=" + decision.confidence
                        + ", reason=" + decision.reason,
                errorType, decision.fallbackReason, System.currentTimeMillis() - started,
                "{\"model\":\"" + safeModelName() + "\"}");
    }

    private String inputSummary(RadarSignal left, RadarSignal right) {
        return "left=" + safeId(left) + " " + limit(left == null ? null : left.getTitle(), 160)
                + "; right=" + safeId(right) + " " + limit(right == null ? null : right.getTitle(), 160);
    }

    private String safeId(RadarSignal signal) { return signal == null ? "" : String.valueOf(signal.getItemId()); }
    private String safeModelName() {
        String value = llm == null ? null : llm.modelName();
        if (blank(value)) return "llm";
        return value.replaceAll("[^a-zA-Z0-9._-]", "_");
    }
    private String extractJson(String raw) {
        int start = raw.indexOf('{');
        int end = raw.lastIndexOf('}');
        return start >= 0 && end >= start ? raw.substring(start, end + 1) : raw.trim();
    }
    private String limit(String value, int max) {
        if (value == null) return "";
        String compact = value.replaceAll("[\\r\\n\\t]+", " ").trim();
        return compact.length() <= max ? compact : compact.substring(0, max);
    }
    private boolean blank(String value) { return value == null || value.trim().isEmpty(); }

    private static final class Draft {
        public Boolean sameEvent;
        public Double confidence;
        public String reason;
    }

    public static final class Decision {
        private final boolean sameEvent;
        private final double confidence;
        private final String reason;
        private final String source;
        private final String fallbackReason;

        private Decision(boolean sameEvent, double confidence, String reason, String source, String fallbackReason) {
            this.sameEvent = sameEvent;
            this.confidence = confidence;
            this.reason = reason;
            this.source = source;
            this.fallbackReason = fallbackReason;
        }

        static Decision fallback(String fallbackReason, String reason) {
            return new Decision(false, 0D, reason, "FALLBACK", fallbackReason);
        }

        public static Decision agent(boolean sameEvent, double confidence, String reason) {
            return new Decision(sameEvent, confidence, reason, "AGENT", null);
        }

        public boolean isSameEvent() { return sameEvent; }
        public double getConfidence() { return confidence; }
        public String getReason() { return reason; }
        public String getSource() { return source; }
        public String getFallbackReason() { return fallbackReason; }
    }
}
