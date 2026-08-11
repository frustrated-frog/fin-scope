package com.finscope.rpc.search;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.finscope.domain.search.SearchResult;
import com.finscope.domain.search.WebSearchRequest;
import com.finscope.rpc.marketintel.ProviderCallDeadline;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Firecrawl Search v2 适配器。 */
public class FirecrawlWebSearchProvider implements WebSearchProvider {
    private static final String DEFAULT_BASE_URL = "https://api.firecrawl.dev";
    private static final int DEFAULT_TIMEOUT_MS = 15000;
    private static final int DEFAULT_MAX_RESPONSE_BYTES = 2 * 1024 * 1024;

    private final boolean enabled;
    private final String apiKey;
    private final String endpoint;
    private final int timeoutMs;
    private final int maxResponseBytes;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public FirecrawlWebSearchProvider(boolean enabled, String apiKey) {
        this(enabled, apiKey, DEFAULT_BASE_URL, DEFAULT_TIMEOUT_MS, DEFAULT_MAX_RESPONSE_BYTES);
    }

    public FirecrawlWebSearchProvider(boolean enabled, String apiKey, String baseUrl,
                                      int timeoutMs, int maxResponseBytes) {
        this.apiKey = apiKey == null ? "" : apiKey.trim();
        this.enabled = enabled && !this.apiKey.isEmpty();
        String normalizedBase = baseUrl == null || baseUrl.trim().isEmpty()
                ? DEFAULT_BASE_URL : baseUrl.trim().replaceAll("/+$", "");
        this.endpoint = normalizedBase + "/v2/search";
        this.timeoutMs = Math.max(1000, timeoutMs);
        this.maxResponseBytes = Math.max(1024, maxResponseBytes);
    }

    @Override
    public String providerCode() { return "FIRECRAWL"; }

    @Override
    public boolean isConfigured() { return enabled; }

    @Override
    public List<SearchResult> search(WebSearchRequest request) throws Exception {
        List<SearchResult> results = new ArrayList<SearchResult>();
        if (!enabled || request == null) {
            return results;
        }
        Map<String, Object> body = new LinkedHashMap<String, Object>();
        body.put("query", request.getQuery());
        body.put("limit", request.getMaxResults());
        Map<String, Object> scrapeOptions = new LinkedHashMap<String, Object>();
        scrapeOptions.put("formats", Collections.singletonList("markdown"));
        body.put("scrapeOptions", scrapeOptions);

        JsonNode root = objectMapper.readTree(DeadlineBoundSearchCall.execute(
                providerCode(), () -> post(objectMapper.writeValueAsString(body))));
        JsonNode items = root.path("data").path("web");
        if (!items.isArray()) {
            items = root.path("data");
        }
        if (!items.isArray()) {
            return results;
        }
        int rank = 0;
        for (JsonNode item : items) {
            String url = text(item, "url");
            if (url.isEmpty()) {
                continue;
            }
            SearchResult result = new SearchResult();
            result.setProviderCode(providerCode());
            result.setProviderRank(++rank);
            result.setTitle(text(item, "title"));
            result.setUrl(url);
            String markdown = text(item, "markdown");
            result.setContent(markdown.isEmpty() ? text(item, "description") : markdown);
            result.setSourceDomain(extractDomain(url));
            result.setSourceTier("T3");
            results.add(result);
        }
        return results;
    }

    private String post(String jsonBody) throws Exception {
        HttpURLConnection connection = null;
        try {
            int effectiveTimeout = effectiveTimeoutMs();
            connection = (HttpURLConnection) new URL(endpoint).openConnection();
            connection.setRequestMethod("POST");
            connection.setConnectTimeout(effectiveTimeout);
            connection.setReadTimeout(effectiveTimeout);
            connection.setDoOutput(true);
            connection.setRequestProperty("Content-Type", "application/json");
            connection.setRequestProperty("Accept", "application/json");
            connection.setRequestProperty("Authorization", "Bearer " + apiKey);
            try (OutputStream output = connection.getOutputStream()) {
                output.write(jsonBody.getBytes(StandardCharsets.UTF_8));
            }
            connection.setReadTimeout(effectiveTimeoutMs());
            int status = connection.getResponseCode();
            connection.setReadTimeout(effectiveTimeoutMs());
            InputStream input = status >= 200 && status < 300
                    ? connection.getInputStream() : connection.getErrorStream();
            String response = readLimited(input, connection);
            if (status < 200 || status >= 300) {
                throw new WebSearchProviderException(providerCode(), status,
                        status == 429 || status >= 500,
                        providerCode() + " request failed with HTTP " + status);
            }
            return response;
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private String readLimited(InputStream input, HttpURLConnection connection) throws Exception {
        if (input == null) {
            return "";
        }
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        byte[] chunk = new byte[4096];
        int read;
        while ((read = input.read(chunk)) >= 0) {
            connection.setReadTimeout(effectiveTimeoutMs());
            if (buffer.size() + read > maxResponseBytes) {
                throw new WebSearchProviderException(providerCode(), 0, false,
                        providerCode() + " response exceeded size limit");
            }
            buffer.write(chunk, 0, read);
        }
        return new String(buffer.toByteArray(), StandardCharsets.UTF_8);
    }

    private int effectiveTimeoutMs() throws WebSearchProviderException {
        long remaining = ProviderCallDeadline.remainingMillis();
        if (remaining <= 0L) {
            throw new WebSearchProviderException(providerCode(), 0, true,
                    providerCode() + " request exceeded provider deadline");
        }
        return (int) Math.max(1L, Math.min((long) timeoutMs, remaining));
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
