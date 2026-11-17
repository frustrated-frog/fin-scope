package com.finscope.rpc.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.finscope.domain.llm.LunaProviderConfig;
import lombok.Data;
import javax.annotation.Resource;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;

/** 共用 Responses 适配器：一次发送，无隐藏重试，不影响其他功能的模型客户端。 */
public abstract class LunaResponsesClient {
    @Resource
    private LunaProviderConfig config;
    @Resource
    private ObjectMapper mapper;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

    public boolean isConfigured() {
        return config.getApiKey() != null && !config.getApiKey().isBlank();
    }

    public String model() { return config.getModel(); }

    public Prepared prepare(String system, String input, int maxOutputTokens, Map<String, Object> schema) {
        return prepare(system, input, maxOutputTokens, schema, "none");
    }

    public Prepared prepare(String system, String input, int maxOutputTokens, Map<String, Object> schema, String effort) {
        try {
            Map<String, Object> body = Map.of("model", config.getModel(), "store", false,
                    "reasoning", Map.of("effort", effort), "max_output_tokens", maxOutputTokens,
                    "input", List.of(Map.of("role", "system", "content", system), Map.of("role", "user", "content", input)),
                    "text", Map.of("format", Map.of("type", "json_schema", "name", "news_understanding", "strict", true, "schema", schema)));
            Prepared value = new Prepared();
            value.setBody(mapper.writeValueAsBytes(body));
            // UTF-8 字节数加协议余量作无缓存输入上界，不把中文字符数当作 token 数。
            value.setInputTokenUpperBound(value.getBody().length + 1024L);
            return value;
        } catch (Exception error) {
            throw new IllegalArgumentException("Luna 请求序列化失败", error);
        }
    }

    public Reply complete(Prepared prepared) throws Exception {
        if (!isConfigured()) {
            throw new IllegalStateException("Luna 尚未配置 OpenAI 密钥");
        }
        String base = config.getBaseUrl().replaceAll("/+$", "");
        HttpRequest request = HttpRequest.newBuilder(URI.create(base + "/responses"))
                .timeout(Duration.ofMillis(config.getTimeoutMs()))
                .header("Authorization", "Bearer " + config.getApiKey()).header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofByteArray(prepared.getBody())).build();
        HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (response.statusCode() >= 400) {
            // 不输出供应商原始错误正文，避免密钥或原文回显；由账本保留保守预留费用。
            throw new IllegalStateException("Luna HTTP " + response.statusCode());
        }
        JsonNode root = mapper.readTree(response.body());
        Reply reply = new Reply();
        JsonNode input = root.path("usage").path("input_tokens");
        JsonNode output = root.path("usage").path("output_tokens");
        if (input.isIntegralNumber() && output.isIntegralNumber() && input.asLong() >= 0 && output.asLong() >= 0) {
            reply.setInputTokens(input.asLong()); reply.setOutputTokens(output.asLong());
        }
        reply.setCompleted("completed".equals(root.path("status").asText()));
        StringBuilder text = new StringBuilder();
        for (JsonNode item : root.path("output")) {
            if (!"message".equals(item.path("type").asText())) {
                continue;
            }
            for (JsonNode content : item.path("content")) {
                if ("output_text".equals(content.path("type").asText())) {
                    text.append(content.path("text").asText());
                }
            }
        }
        reply.setText(text.toString());
        return reply;
    }

    @Data
    public static class Prepared {
        private byte[] body;
        private long inputTokenUpperBound;
    }

    @Data
    public static class Reply {
        private String text;
        private Long inputTokens;
        private Long outputTokens;
        private boolean completed;
    }
}
