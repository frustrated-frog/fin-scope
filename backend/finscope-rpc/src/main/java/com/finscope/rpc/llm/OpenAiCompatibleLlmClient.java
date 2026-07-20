package com.finscope.rpc.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.cert.X509Certificate;
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
    private static final String USER_AGENT =
            "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 "
                    + "(KHTML, like Gecko) Chrome/125.0.0.0 Safari/537.36 FinScope/0.1";

    static {
        // 初始化SSL上下文,解决Java 8的TLS兼容性问题
        try {
            SSLContext sslContext = SSLContext.getInstance("TLSv1.2");
            sslContext.init(null, new TrustManager[] {
                new X509TrustManager() {
                    public X509Certificate[] getAcceptedIssuers() {
                        return null;
                    }
                    public void checkClientTrusted(X509Certificate[] certs, String authType) {
                    }
                    public void checkServerTrusted(X509Certificate[] certs, String authType) {
                    }
                }
            }, new java.security.SecureRandom());
            HttpsURLConnection.setDefaultSSLSocketFactory(sslContext.getSocketFactory());
            HttpsURLConnection.setDefaultHostnameVerifier((hostname, session) -> true);
        } catch (Exception e) {
            // 如果初始化失败,使用默认配置
        }
    }

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
        byte[] requestBody = objectMapper.writeValueAsBytes(
                request(systemPrompt, userPrompt, maxOutputTokens));
        HttpURLConnection connection = (HttpURLConnection) new URL(endpoint()).openConnection();
        connection.setRequestMethod("POST");
        connection.setConnectTimeout(actualTimeoutMs);
        connection.setReadTimeout(actualTimeoutMs);
        connection.setDoOutput(true);
        connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
        connection.setRequestProperty("Accept", "application/json");
        connection.setRequestProperty("Authorization", "Bearer " + apiKey);
        connection.setRequestProperty("User-Agent", USER_AGENT);
        try (OutputStream output = connection.getOutputStream()) {
            output.write(requestBody);
        }
        int status = connection.getResponseCode();
        InputStream input = status >= 400 ? connection.getErrorStream() : connection.getInputStream();
        String responseBody = new String(readAll(input), StandardCharsets.UTF_8);
        if (status >= 400) {
            throw new IllegalStateException("OpenAI compatible LLM request failed, HTTP " + status + ": " + responseBody);
        }
        JsonNode root = objectMapper.readTree(responseBody);
        JsonNode content = root.path("choices").path(0).path("message").path("content");
        if (content.isMissingNode() || isBlank(content.asText())) {
            throw new IllegalStateException("OpenAI compatible LLM response has no message content: " + responseBody);
        }
        return content.asText();
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

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
