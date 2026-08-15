package com.finscope.service.globalexpectations;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.finscope.domain.globalexpectations.GlobalExpectationEventGroup;
import com.finscope.domain.globalexpectations.GlobalExpectationInterpretation;
import com.finscope.rpc.llm.LlmChatClient;
import com.finscope.service.radar.RadarAgentTraceRecorder;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;

/** 对已经成立的异动事实做短解释；失败只返回增强状态，不影响市场监控。 */
@Component
public class GlobalExpectationInterpretationAgent {
    private static final int TIMEOUT_MS = 15000;
    private static final int MAX_OUTPUT_TOKENS = 600;

    @Resource
    private LlmChatClient llmChatClient;
    @Resource
    private ObjectMapper objectMapper;
    @Resource
    private RadarAgentTraceRecorder traceRecorder;

    public GlobalExpectationInterpretation interpret(GlobalExpectationEventGroup group) {
        long started = System.currentTimeMillis();
        if (llmChatClient == null || !llmChatClient.isConfigured()) {
            GlobalExpectationInterpretation unavailable = failure("UNAVAILABLE", "AI 解读未启用");
            trace(group, unavailable, "MODEL_DISABLED", started);
            return unavailable;
        }
        try {
            String input = objectMapper.writeValueAsString(group);
            String raw = llmChatClient.complete(systemPrompt(), input, TIMEOUT_MS, MAX_OUTPUT_TOKENS);
            GlobalExpectationInterpretation result = parse(raw);
            result.setStatus("READY");
            trace(group, result, null, started);
            return result;
        } catch (Exception error) {
            GlobalExpectationInterpretation failed = failure("FAILED", "AI 解读暂时不可用");
            trace(group, failed, error.getClass().getSimpleName(), started);
            return failed;
        }
    }

    private GlobalExpectationInterpretation parse(String raw) throws Exception {
        JsonNode root = objectMapper.readTree(extractJson(raw));
        if (root == null || !root.isObject()) {
            throw new IllegalArgumentException("AI 解读必须返回 JSON 对象");
        }
        GlobalExpectationInterpretation result = new GlobalExpectationInterpretation();
        result.setHappened(required(root, "happened"));
        result.setMeaning(required(root, "meaning"));
        result.setRelatedVariables(required(root, "relatedVariables"));
        result.setNextObservation(required(root, "nextObservation"));
        return result;
    }

    private String required(JsonNode root, String field) {
        String value = root.path(field).asText("").replaceAll("[\\r\\n\\t]+", " ").trim();
        if (value.isBlank()) {
            throw new IllegalArgumentException("AI 解读缺少字段: " + field);
        }
        return value.length() <= 240 ? value : value.substring(0, 240);
    }

    private GlobalExpectationInterpretation failure(String status, String message) {
        GlobalExpectationInterpretation result = new GlobalExpectationInterpretation();
        result.setStatus(status);
        result.setFailureMessage(message);
        return result;
    }

    private String extractJson(String raw) {
        if (raw == null) {
            return "";
        }
        int start = raw.indexOf('{');
        int end = raw.lastIndexOf('}');
        return start >= 0 && end >= start ? raw.substring(start, end + 1) : raw.trim();
    }

    private String systemPrompt() {
        return "你是全球预期监控的短解读组件。只能使用输入中的概率、成交量、排名变化和本地 Radar 匹配，"
                + "不得补充外部事实，不得给出买卖建议。输出纯 JSON，且只包含 happened、meaning、"
                + "relatedVariables、nextObservation 四个简短字符串字段。事实和推演必须明确区分。";
    }

    private void trace(GlobalExpectationEventGroup group, GlobalExpectationInterpretation result,
                       String errorType, long started) {
        if (traceRecorder == null) {
            return;
        }
        traceRecorder.record("global-expectation-interpretation", "GLOBAL_EXPECTATION", null,
                result.getStatus(), "group=" + group.getId() + ",title=" + group.getTitle(),
                result.getHappened(), errorType,
                "READY".equals(result.getStatus()) ? null : result.getFailureMessage(),
                System.currentTimeMillis() - started,
                "{\"model\":\"" + safeModelName() + "\"}");
    }

    private String safeModelName() {
        if (llmChatClient == null || llmChatClient.modelName() == null) {
            return "llm";
        }
        return llmChatClient.modelName().replaceAll("[^a-zA-Z0-9._-]", "_");
    }
}
