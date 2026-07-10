package com.finscope.rpc.search;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.finscope.domain.search.SearchResult;

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
 * Tavily 联网搜索实现。
 * 接口：POST https://api.tavily.com/search
 * body: {"api_key":"...","query":"...","max_results":n,"search_depth":"basic"}
 * 返回：{"results":[{"title","url","content","score","published_date"}]}
 * API Key 由外部注入（来自环境变量），不硬编码。
 */
public class TavilyWebSearchClient implements WebSearchClient {
    private static final String ENDPOINT = "https://api.tavily.com/search";
    private static final int TIMEOUT_MS = 15000;

    private final boolean enabled;
    private final String apiKey;
    private final ObjectMapper objectMapper = new ObjectMapper();

    // 来源可信度分级：T1 权威 / T2 主流媒体券商 / 其余 T3
    private static final Map<String, String> TIER_MAP = new LinkedHashMap<>();

    static {
        String[] t1 = {"gov.cn", "pbc.gov.cn", "csrc.gov.cn", "sse.com.cn", "szse.cn",
                "caixin.com", "cnstock.com", "stcn.com", "xinhuanet.com", "chinanews.com"};
        String[] t2 = {"eastmoney.com", "sina.com.cn", "10jqka.com.cn", "wallstreetcn.com",
                "yicai.com", "21jingji.com", "jrj.com.cn", "hexun.com"};
        for (String d : t1) {
            TIER_MAP.put(d, "T1");
        }
        for (String d : t2) {
            TIER_MAP.put(d, "T2");
        }
    }

    public TavilyWebSearchClient(boolean enabled, String apiKey) {
        this.apiKey = apiKey == null ? "" : apiKey.trim();
        this.enabled = enabled && !this.apiKey.isEmpty();
    }

    @Override
    public boolean isConfigured() {
        return enabled;
    }

    @Override
    public List<SearchResult> search(String query, int maxResults) throws Exception {
        List<SearchResult> results = new ArrayList<>();
        if (!enabled || query == null || query.trim().isEmpty()) {
            return results;
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("api_key", apiKey);
        body.put("query", query.trim());
        body.put("max_results", maxResults <= 0 ? 5 : maxResults);
        body.put("search_depth", "basic");
        body.put("topic", "news");

        String response = post(ENDPOINT, objectMapper.writeValueAsString(body));
        JsonNode root = objectMapper.readTree(response);
        JsonNode items = root.path("results");
        if (!items.isArray()) {
            return results;
        }
        for (JsonNode node : items) {
            SearchResult result = new SearchResult();
            result.setTitle(text(node, "title"));
            result.setUrl(text(node, "url"));
            result.setContent(text(node, "content"));
            result.setPublishedAt(text(node, "published_date"));
            if (node.has("score") && node.path("score").isNumber()) {
                result.setScore(node.path("score").asDouble());
            }
            String domain = extractDomain(result.getUrl());
            result.setSourceDomain(domain);
            result.setSourceTier(resolveTier(domain));
            results.add(result);
        }
        return results;
    }

    private String resolveTier(String domain) {
        if (domain == null || domain.isEmpty()) {
            return "T3";
        }
        String lower = domain.toLowerCase(Locale.ROOT);
        for (Map.Entry<String, String> entry : TIER_MAP.entrySet()) {
            if (lower.contains(entry.getKey())) {
                return entry.getValue();
            }
        }
        return "T3";
    }

    private String extractDomain(String url) {
        if (url == null || url.isEmpty()) {
            return "";
        }
        try {
            String host = new URL(url).getHost();
            return host == null ? "" : host.toLowerCase(Locale.ROOT);
        } catch (Exception ex) {
            return "";
        }
    }

    private String post(String urlText, String jsonBody) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(urlText).openConnection();
        connection.setRequestMethod("POST");
        connection.setConnectTimeout(TIMEOUT_MS);
        connection.setReadTimeout(TIMEOUT_MS);
        connection.setDoOutput(true);
        connection.setRequestProperty("Content-Type", "application/json");
        connection.setRequestProperty("Accept", "application/json");
        try (OutputStream os = connection.getOutputStream()) {
            os.write(jsonBody.getBytes(StandardCharsets.UTF_8));
        }
        int code = connection.getResponseCode();
        InputStream in = code >= 200 && code < 300 ? connection.getInputStream() : connection.getErrorStream();
        String responseText = readAll(in);
        connection.disconnect();
        if (code < 200 || code >= 300) {
            throw new IllegalStateException("Tavily 搜索失败 HTTP " + code + ": " + responseText);
        }
        return responseText;
    }

    private String readAll(InputStream in) throws Exception {
        if (in == null) {
            return "";
        }
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        byte[] chunk = new byte[4096];
        int read;
        while ((read = in.read(chunk)) != -1) {
            buffer.write(chunk, 0, read);
        }
        return new String(buffer.toByteArray(), StandardCharsets.UTF_8);
    }

    private String text(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return value.isMissingNode() || value.isNull() ? "" : value.asText("");
    }
}