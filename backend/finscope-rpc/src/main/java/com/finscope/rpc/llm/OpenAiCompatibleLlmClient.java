package com.finscope.rpc.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class OpenAiCompatibleLlmClient implements LlmChatClient {
    private final boolean enabled;
    private final String baseUrl;
    private final String apiKey;
    private final String model;
    private final int timeoutMs;
    private final double temperature;
    private final boolean jsonMode;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private static final int RETRY_OUTPUT_TOKEN_BUDGET = 4_096;
    private static final String USER_AGENT =
            "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 "
                    + "(KHTML, like Gecko) Chrome/125.0.0.0 Safari/537.36 FinScope/0.1";

    public OpenAiCompatibleLlmClient(boolean enabled,
                                     String baseUrl,
                                     String apiKey,
                                     String model,
                                     int timeoutMs,
                                     double temperature) {
        this(enabled, baseUrl, apiKey, model, timeoutMs, temperature, false);
    }

    public OpenAiCompatibleLlmClient(boolean enabled,
                                     String baseUrl,
                                     String apiKey,
                                     String model,
                                     int timeoutMs,
                                     double temperature,
                                     boolean jsonMode) {
        this.enabled = enabled;
        this.baseUrl = trim(baseUrl);
        this.apiKey = trim(apiKey);
        this.model = trim(model);
        this.timeoutMs = timeoutMs <= 0 ? 30000 : timeoutMs;
        this.temperature = temperature;
        this.jsonMode = jsonMode;
    }

    @Override
    public boolean isConfigured() {
        return enabled && !isBlank(baseUrl) && !isBlank(apiKey) && !isBlank(model);
    }

    @Override
    public String modelName() {
        return isBlank(model) ? "unconfigured" : model;
    }

    @Override
    public String complete(String systemPrompt, String userPrompt) throws Exception {
        return complete(systemPrompt, userPrompt, timeoutMs);
    }

    @Override
    public String complete(String systemPrompt, String userPrompt, int requestedTimeoutMs) throws Exception {
        return complete(systemPrompt, userPrompt, requestedTimeoutMs, 0);
    }

    @Override
    public String complete(String systemPrompt, String userPrompt,
                           int requestedTimeoutMs, int maxOutputTokens) throws Exception {
        if (!isConfigured()) {
            throw new IllegalStateException("LLM is not configured");
        }
        int actualTimeoutMs = requestedTimeoutMs <= 0 ? timeoutMs : Math.min(timeoutMs, requestedTimeoutMs);
        long deadlineNanos = System.nanoTime() + actualTimeoutMs * 1_000_000L;
        JsonNode root = requestCompletion(systemPrompt, userPrompt, maxOutputTokens, actualTimeoutMs);
        String content = messageContent(root);
        if (isBlank(content) && maxOutputTokens > 0) {
            int remainingTimeoutMs = remainingTimeoutMs(deadlineNanos);
            if (remainingTimeoutMs > 0) {
                root = requestCompletion(systemPrompt, userPrompt, retryOutputTokenBudget(maxOutputTokens),
                        remainingTimeoutMs);
                content = messageContent(root);
            }
        }
        if (isBlank(content)) {
            throw noFinalContent(root);
        }
        return content;
    }

    private JsonNode requestCompletion(String systemPrompt, String userPrompt,
                                       int maxOutputTokens, int requestTimeoutMs) throws Exception {
        byte[] requestBody = objectMapper.writeValueAsBytes(request(systemPrompt, userPrompt, maxOutputTokens));
        HttpURLConnection connection = (HttpURLConnection) new URL(endpoint()).openConnection();
        try {
            connection.setRequestMethod("POST");
            connection.setConnectTimeout(requestTimeoutMs);
            connection.setReadTimeout(requestTimeoutMs);
            connection.setDoOutput(true);
            connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
            connection.setRequestProperty("Accept", "application/json");
            connection.setRequestProperty("Authorization", "Bearer " + apiKey);
            connection.setRequestProperty("User-Agent", USER_AGENT);
            try (OutputStream output = connection.getOutputStream()) {
                output.write(requestBody);
            }
            int status = connection.getResponseCode();
            String responseBody;
            InputStream responseStream = status >= 400 ? connection.getErrorStream() : connection.getInputStream();
            try (InputStream input = responseStream) {
                responseBody = new String(readAll(input), StandardCharsets.UTF_8);
            }
            if (status >= 400) {
                throw new IllegalStateException("OpenAI compatible LLM request failed, HTTP " + status
                        + ": " + limit(responseBody, 4000));
            }
            return objectMapper.readTree(responseBody);
        } finally {
            connection.disconnect();
        }
    }

    private Map<String, Object> request(String systemPrompt, String userPrompt, int maxOutputTokens) {
        Map<String, Object> request = new LinkedHashMap<String, Object>();
        request.put("model", model);
        request.put("temperature", temperature);
        request.put("stream", false);
        request.put("messages", messages(systemPrompt, userPrompt));
        if (maxOutputTokens > 0) {
            request.put("max_tokens", maxOutputTokens);
        }
        // 仅在明确开启 json 模式时才附带 response_format。
        // 部分模型（如 GLM-5）对 response_format=json_object 支持不佳，会提前 abort 返回残缺 JSON，
        // 此时由上层的 extractJson 从纯文本中提取 JSON 更稳妥。
        if (jsonMode) {
            Map<String, String> responseFormat = new LinkedHashMap<String, String>();
            responseFormat.put("type", "json_object");
            request.put("response_format", responseFormat);
        }
        return request;
    }

    private String messageContent(JsonNode root) {
        return root.path("choices").path(0).path("message").path("content").asText();
    }

    private int retryOutputTokenBudget(int originalBudget) {
        return Math.max(RETRY_OUTPUT_TOKEN_BUDGET, originalBudget * 3);
    }

    private int remainingTimeoutMs(long deadlineNanos) {
        long remainingNanos = deadlineNanos - System.nanoTime();
        return remainingNanos <= 0L ? 0 : (int) Math.max(1L, remainingNanos / 1_000_000L);
    }

    private IllegalStateException noFinalContent(JsonNode root) {
        JsonNode choice = root.path("choices").path(0);
        boolean reasoningOnly = !choice.path("message").path("reasoning_content").asText().trim().isEmpty();
        String finishReason = choice.path("finish_reason").asText().trim();
        String detail = "OpenAI compatible LLM response completed without a final message content"
                + (reasoningOnly ? " (provider returned reasoning only)" : "");
        if (!finishReason.isEmpty()) {
            detail += ", finishReason=" + limit(finishReason, 80);
        }
        return new IllegalStateException(detail);
    }

    private List<Map<String, String>> messages(String systemPrompt, String userPrompt) {
        List<Map<String, String>> messages = new ArrayList<Map<String, String>>();
        messages.add(message("system", systemPrompt));
        messages.add(message("user", userPrompt));
        return messages;
    }

    private Map<String, String> message(String role, String content) {
        Map<String, String> message = new LinkedHashMap<String, String>();
        message.put("role", role);
        message.put("content", content == null ? "" : content);
        return message;
    }

    private String endpoint() {
        String value = baseUrl;
        if (value.endsWith("/chat/completions")) {
            return value;
        }
        while (value.endsWith("/")) {
            value = value.substring(0, value.length() - 1);
        }
        return value + "/chat/completions";
    }

    private byte[] readAll(InputStream inputStream) throws Exception {
        if (inputStream == null) {
            return new byte[0];
        }
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        byte[] chunk = new byte[4096];
        int read;
        while ((read = inputStream.read(chunk)) != -1) {
            buffer.write(chunk, 0, read);
        }
        return buffer.toByteArray();
    }

    private String trim(String value) {
        return value == null ? "" : value.trim();
    }

    private String limit(String value, int maximumLength) {
        if (value == null || value.length() <= maximumLength) {
            return value == null ? "" : value;
        }
        return value.substring(0, maximumLength) + "…";
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
