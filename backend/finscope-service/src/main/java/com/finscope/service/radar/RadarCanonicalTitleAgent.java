package com.finscope.service.radar;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.finscope.domain.radar.RadarSignal;
import com.finscope.rpc.llm.LlmChatClient;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class RadarCanonicalTitleAgent {
    static final int TIMEOUT_MS = 15_000;
    static final int MAX_OUTPUT_TOKENS = 240;
    private static final int TITLE_LIMIT = 48;

    private final LlmChatClient llm;
    private final ObjectMapper json;
    private final RadarAgentTraceRecorder traces;

    public RadarCanonicalTitleAgent(LlmChatClient llm, ObjectMapper json, RadarAgentTraceRecorder traces) {
        this.llm = llm;
        this.json = json.copy()
                .enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION)
                .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
        this.traces = traces;
    }

    public Result generate(List<RadarSignal> signals, String fallbackTitle) {
        long started = System.currentTimeMillis();
        String safeFallback = compact(fallbackTitle, TITLE_LIMIT);
        if (llm == null || !llm.isConfigured()) {
            Result fallback = Result.fallback(safeFallback, "MODEL_DISABLED");
            trace(signals, fallback, started, null);
            return fallback;
        }
        try {
            String raw = llm.complete(systemPrompt(), userPrompt(signals), TIMEOUT_MS, MAX_OUTPUT_TOKENS);
            Draft draft = json.readValue(extractJson(raw), Draft.class);
            String title = draft == null ? "" : compact(draft.title, TITLE_LIMIT + 1);
            if (!valid(title)) throw new IllegalArgumentException("规范标题不符合约束");
            Result result = new Result(title, true, null);
            trace(signals, result, started, null);
            return result;
        } catch (Exception error) {
            Result fallback = Result.fallback(safeFallback, "INVALID_OUTPUT");
            trace(signals, fallback, started, error.getClass().getSimpleName());
            return fallback;
        }
    }

    private String systemPrompt() {
        return "你是金融事件标题编辑。根据同一事件的多条来源信号，生成一个中性、事实化、适合初学者的规范标题。"
                + "不得加入原文没有的原因、影响、情绪或投资建议。输出单个纯 JSON 对象，只允许 title 字段。"
                + "标题不超过48个字符，不得包含 Markdown。";
    }

    private String userPrompt(List<RadarSignal> signals) throws Exception {
        List<Map<String, Object>> items = new ArrayList<Map<String, Object>>();
        if (signals != null) for (RadarSignal signal : signals) {
            Map<String, Object> item = new LinkedHashMap<String, Object>();
            item.put("itemId", signal.getItemId());
            item.put("source", signal.getSourceName());
            item.put("title", signal.getTitle());
            item.put("content", compact(signal.getContent(), 240));
            items.add(item);
        }
        return json.writeValueAsString(items);
    }

    private boolean valid(String title) {
        return title != null && !title.isEmpty() && title.length() <= TITLE_LIMIT
                && !title.contains("#") && !title.contains("*") && !title.contains("`")
                && !title.contains("[") && !title.contains("]")
                && !title.contains("买入") && !title.contains("卖出");
    }

    private void trace(List<RadarSignal> signals, Result result, long started, String errorType) {
        RadarSignal first = signals == null || signals.isEmpty() ? null : signals.get(0);
        traces.record("radar-canonical-title", "RADAR_CLUSTER", first == null ? null : first.getId(),
                result.generated ? "SUCCESS" : "FALLBACK",
                "signals=" + (signals == null ? 0 : signals.size()), "title=" + result.title,
                errorType, result.fallbackReason, System.currentTimeMillis() - started, "{}");
    }

    private String extractJson(String raw) {
        if (raw == null) return "";
        int start = raw.indexOf('{');
        int end = raw.lastIndexOf('}');
        return start >= 0 && end >= start ? raw.substring(start, end + 1) : raw.trim();
    }

    private String compact(String value, int max) {
        if (value == null) return "";
        String compact = value.replaceAll("[\\r\\n\\t]+", " ").trim();
        return compact.length() <= max ? compact : compact.substring(0, max);
    }

    private static final class Draft { public String title; }

    public static final class Result {
        private final String title;
        private final boolean generated;
        private final String fallbackReason;

        private Result(String title, boolean generated, String fallbackReason) {
            this.title = title;
            this.generated = generated;
            this.fallbackReason = fallbackReason;
        }

        static Result fallback(String title, String reason) { return new Result(title, false, reason); }
        public String getTitle() { return title; }
        public boolean isGenerated() { return generated; }
        public String getFallbackReason() { return fallbackReason; }
    }
}
