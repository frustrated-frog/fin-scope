package com.finscope.rpc.search;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.finscope.domain.search.SearchResult;
import com.finscope.domain.search.WebSearchRequest;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * AnySearch 通用搜索适配器。第一阶段固定使用 general.general。
 */
public class AnySearchWebSearchProvider implements WebSearchProvider {
    private static final String DEFAULT_BASE_URL = "https://api.anysearch.com";
    private static final int DEFAULT_TIMEOUT_MS = 15000;
    private static final int DEFAULT_MAX_RESPONSE_BYTES = 2 * 1024 * 1024;

    private final boolean enabled;
    private final String apiKey;
    private final String endpoint;
    private final int timeoutMs;
    private final int maxResponseBytes;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public AnySearchWebSearchProvider(boolean enabled, String apiKey) {
        this(enabled, apiKey, DEFAULT_BASE_URL, DEFAULT_TIMEOUT_MS, DEFAULT_MAX_RESPONSE_BYTES);
    }

    public AnySearchWebSearchProvider(boolean enabled, String apiKey, String baseUrl,
                                      int timeoutMs, int maxResponseBytes) {
        this.enabled = enabled;
        this.apiKey = apiKey == null ? "" : apiKey.trim();
        String normalizedBase = baseUrl == null || baseUrl.trim().isEmpty()
                ? DEFAULT_BASE_URL : baseUrl.trim().replaceAll("/+$", "");
        this.endpoint = normalizedBase + "/v1/search";
        this.timeoutMs = Math.max(1000, timeoutMs);
        this.maxResponseBytes = Math.max(1024, maxResponseBytes);
    }

    @Override
    public String providerCode() { return "ANYSEARCH"; }

    @Override
    public boolean isConfigured() { return enabled; }

    @Override
    public List<SearchResult> search(WebSearchRequest request) throws Exception {
        List<SearchResult> results = new ArrayList<SearchResult>();
        if (!enabled || request == null) return results;

        Map<String, Object> body = new LinkedHashMap<String, Object>();
        body.put("query", request.getQuery());
        body.put("max_results", request.getMaxResults());
        body.put("tag", "general.general");
        if (!request.getZone().isEmpty()) body.put("zone", request.getZone());
        if (!request.getLanguage().isEmpty()) body.put("language", request.getLanguage());
        body.put("format", "json");

        JsonNode root = objectMapper.readTree(post(objectMapper.writeValueAsString(body)));
        JsonNode items = root.path("data");
        if (items.isObject()) items = items.path("results");
        if (!items.isArray()) items = root.path("results");
        if (!items.isArray()) return results;

        int rank = 0;
        for (JsonNode node : items) {
            String url = text(node, "url");
            if (url.isEmpty()) continue;
            SearchResult result = new SearchResult();
            result.setProviderCode(providerCode());
            result.setProviderRank(++rank);
            result.setTitle(text(node, "title"));
            result.setUrl(url);
            String content = text(node, "content");
            result.setContent(content.isEmpty() ? text(node, "snippet") : content);
            result.setSourceDomain(extractDomain(url));
            result.setSourceTier("T3");
            results.add(result);
        }
        return results;
    }

    private String post(String jsonBody) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(endpoint).openConnection();
        connection.setRequestMethod("POST");
        connection.setConnectTimeout(timeoutMs);
        connection.setReadTimeout(timeoutMs);
        connection.setDoOutput(true);
        connection.setRequestProperty("Content-Type", "application/json");
        connection.setRequestProperty("Accept", "application/json");
        if (!apiKey.isEmpty()) connection.setRequestProperty("Authorization", "Bearer " + apiKey);
        try (OutputStream output = connection.getOutputStream()) {
            output.write(jsonBody.getBytes(StandardCharsets.UTF_8));
        }
        int status = connection.getResponseCode();
        InputStream input = status >= 200 && status < 300
                ? connection.getInputStream() : connection.getErrorStream();
        String response = readLimited(input);
        connection.disconnect();
        if (status < 200 || status >= 300) {
            throw new WebSearchProviderException(providerCode(), status,
                    status == 429 || status >= 500,
                    providerCode() + " request failed with HTTP " + status);
        }
        return response;
    }

    private String readLimited(InputStream input) throws Exception {
        if (input == null) return "";
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        byte[] chunk = new byte[4096];
        int read;
        while ((read = input.read(chunk)) >= 0) {
            if (buffer.size() + read > maxResponseBytes) {
                throw new WebSearchProviderException(providerCode(), 0, false,
                        providerCode() + " response exceeded size limit");
            }
            buffer.write(chunk, 0, read);
        }
        return new String(buffer.toByteArray(), StandardCharsets.UTF_8);
    }

    private String extractDomain(String url) {
        try {
            String host = new URL(url).getHost();
            return host == null ? "" : host.toLowerCase(Locale.ROOT);
        } catch (Exception ignored) {
            return "";
        }
    }

    private String text(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return value.isMissingNode() || value.isNull() ? "" : value.asText("").trim();
    }
}
