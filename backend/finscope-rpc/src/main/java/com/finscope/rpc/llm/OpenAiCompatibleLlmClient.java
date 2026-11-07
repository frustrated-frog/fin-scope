package com.finscope.rpc.llm;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.io.JsonEOFException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.net.ConnectException;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class OpenAiCompatibleLlmClient implements LlmChatClient {
    private final boolean enabled;
    private final String baseUrl;
    private final String apiKey;
    private final String model;
    private final int timeoutMs;
    private final double temperature;
    private final boolean jsonMode;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final HttpClient httpClient = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).build();
    private static final int RETRY_OUTPUT_TOKEN_BUDGET = 4_096;
    private static final int MAX_TRANSIENT_ATTEMPTS = 2;
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
        JsonNode root = requestCompletionWithTransientRetry(
                systemPrompt, userPrompt, maxOutputTokens, deadlineNanos);
        String content = messageContent(root);
        if (needsExpandedOutputRetry(root, content, maxOutputTokens)) {
            int remainingTimeoutMs = remainingTimeoutMs(deadlineNanos);
            if (remainingTimeoutMs > 0) {
                root = requestCompletionWithTransientRetry(
                        systemPrompt, userPrompt, retryOutputTokenBudget(maxOutputTokens), deadlineNanos);
                content = messageContent(root);
            }
        }
        if (isBlank(content)) {
            throw noFinalContent(root);
        }
        if (maxOutputTokens > 0 && hasIncompleteJsonContent(content)) {
            throw new IllegalStateException("OpenAI compatible LLM response completed with incomplete JSON content");
        }
        return content;
    }

    private JsonNode requestCompletionWithTransientRetry(String systemPrompt,
                                                         String userPrompt,
                                                         int maxOutputTokens,
                                                         long deadlineNanos) throws Exception {
        Exception lastFailure = null;
        for (int attempt = 1; attempt <= MAX_TRANSIENT_ATTEMPTS; attempt++) {
            int remaining = remainingTimeoutMs(deadlineNanos);
            if (remaining <= 0) break;
            try {
                return requestCompletion(systemPrompt, userPrompt, maxOutputTokens, deadlineNanos);
            } catch (Exception error) {
                lastFailure = error;
                if (attempt >= MAX_TRANSIENT_ATTEMPTS || !isTransient(error)) throw error;
                waitBeforeRetry(error, deadlineNanos);
            }
        }
        if (lastFailure != null) throw lastFailure;
        throw new SocketTimeoutException("OpenAI compatible LLM request exceeded its timeout budget");
    }

    private JsonNode requestCompletion(String systemPrompt, String userPrompt,
                                       int maxOutputTokens, long deadlineNanos) throws Exception {
        byte[] requestBody = objectMapper.writeValueAsBytes(request(systemPrompt, userPrompt, maxOutputTokens));
        HttpRequest request = HttpRequest.newBuilder(URI.create(endpoint()))
                .header("Content-Type", "application/json; charset=utf-8")
                .header("Accept", "application/json")
                .header("Authorization", "Bearer " + apiKey)
                .header("User-Agent", USER_AGENT)
                .POST(HttpRequest.BodyPublishers.ofByteArray(requestBody))
                .build();
        if (remainingTimeoutMs(deadlineNanos) <= 0) {
            throw new SocketTimeoutException("OpenAI compatible LLM request exceeded its timeout budget");
        }
        // ofString 完成时才收齐正文。等待整个响应使用同一截止时间，持续到达的分块也不能延长预算。
        CompletableFuture<HttpResponse<String>> pending = httpClient.sendAsync(request,
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        HttpResponse<String> response;
        try {
            response = pending.get(remainingTimeoutMs(deadlineNanos), TimeUnit.MILLISECONDS);
        } catch (TimeoutException error) {
            pending.cancel(true);
            SocketTimeoutException timeout = new SocketTimeoutException("OpenAI compatible LLM request exceeded its timeout budget");
            timeout.initCause(error);
            throw timeout;
        } catch (InterruptedException error) {
            pending.cancel(true);
            Thread.currentThread().interrupt();
            throw error;
        } catch (ExecutionException error) {
            if (error.getCause() instanceof Exception cause) {
                throw cause;
            }
            throw error;
        }
        int status = response.statusCode();
        if (status >= 400) {
            String requestId = firstHeader(response.headers(), "x-request-id", "request-id", "trace-id");
            throw new ProviderHttpException(status, retryAfterMillis(response.headers().firstValue("Retry-After").orElse(null)),
                    "OpenAI compatible LLM request failed, HTTP " + status
                            + (isBlank(requestId) ? "" : ", requestId=" + limit(requestId, 160)));
        }
        return objectMapper.readTree(response.body());
    }

    private boolean isTransient(Exception error) {
        if (error instanceof ProviderHttpException) {
            int status = ((ProviderHttpException) error).status;
            return status == 408 || status == 429 || (status >= 500 && status <= 599);
        }
        return error instanceof SocketTimeoutException
                || error instanceof ConnectException
                || error instanceof SocketException;
    }

    private void waitBeforeRetry(Exception error, long deadlineNanos) throws Exception {
        long delayMs = error instanceof ProviderHttpException
                ? ((ProviderHttpException) error).retryAfterMillis : 100L;
        delayMs = Math.max(0L, delayMs);
        long remainingMs = remainingTimeoutMs(deadlineNanos);
        if (delayMs <= 0L || remainingMs <= 1L) return;
        try {
            Thread.sleep(Math.min(delayMs, remainingMs - 1L));
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw interrupted;
        }
    }

    private long retryAfterMillis(String value) {
        if (isBlank(value)) return 100L;
        try {
            return Math.max(0L, Long.parseLong(value.trim()) * 1_000L);
        } catch (NumberFormatException ignored) {
            try {
                long delay = java.time.Duration.between(ZonedDateTime.now(),
                        ZonedDateTime.parse(value.trim(), DateTimeFormatter.RFC_1123_DATE_TIME)).toMillis();
                return Math.max(0L, delay);
            } catch (DateTimeParseException invalidDate) {
                return 100L;
            }
        }
    }

    private String firstHeader(HttpHeaders headers, String... names) {
        for (String name : names) {
            String value = headers.firstValue(name).orElse("");
            if (!isBlank(value)) {
                return value.trim();
            }
        }
        return "";
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
        JsonNode choice = root.path("choices").path(0);
        JsonNode content = choice.path("message").path("content");
        if (content.isTextual()) return content.asText();
        if (content.isArray()) {
            StringBuilder text = new StringBuilder();
            for (JsonNode part : content) {
                if (part.isTextual()) {
                    text.append(part.asText());
                } else if (part.path("text").isTextual()) {
                    text.append(part.path("text").asText());
                }
            }
            return text.toString();
        }
        if (content.path("text").isTextual()) return content.path("text").asText();
        return choice.path("text").asText();
    }

    private boolean needsExpandedOutputRetry(JsonNode root, String content, int maxOutputTokens) {
        return maxOutputTokens > 0 && (isBlank(content)
                || "length".equalsIgnoreCase(root.path("choices").path(0).path("finish_reason").asText())
                || hasIncompleteJsonContent(content));
    }

    private boolean hasIncompleteJsonContent(String content) {
        String candidate = trim(content);
        if (!(candidate.startsWith("{") || candidate.startsWith("["))) {
            return false;
        }
        try {
            objectMapper.readTree(candidate);
            return false;
        } catch (JsonEOFException ignored) {
            return true;
        } catch (JsonProcessingException ignored) {
            return false;
        }
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

    private static final class ProviderHttpException extends IllegalStateException {
        private final int status;
        private final long retryAfterMillis;

        private ProviderHttpException(int status, long retryAfterMillis, String message) {
            super(message);
            this.status = status;
            this.retryAfterMillis = retryAfterMillis;
        }
    }
}
