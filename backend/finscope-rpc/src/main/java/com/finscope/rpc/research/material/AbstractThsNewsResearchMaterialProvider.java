package com.finscope.rpc.research.material;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.finscope.domain.research.material.ResearchMaterial;
import com.finscope.domain.research.material.ResearchMaterialType;
import com.finscope.rpc.acquisition.AcquisitionRequest;
import com.finscope.rpc.acquisition.AcquisitionResponse;
import com.finscope.rpc.acquisition.AcquisitionRuntime;
import com.finscope.rpc.acquisition.AcquisitionException;
import com.finscope.rpc.marketdata.ProviderResult;
import com.finscope.rpc.marketintel.ProviderContractException;

import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

abstract class AbstractThsNewsResearchMaterialProvider implements ResearchMaterialProvider {
    private static final String SCRIPT_PREFIX = "var thsRss =";
    private static final DateTimeFormatter PUBLISHED_AT = DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm");
    private static final DateTimeFormatter FEED_UPDATED_AT = DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss");
    private static final Duration STALE_AFTER = Duration.ofHours(24);

    private final AcquisitionRuntime runtime;
    private final ObjectMapper parser;
    private final Clock clock;
    private final URI endpoint;
    private final String code;
    private final int providerPriority;

    AbstractThsNewsResearchMaterialProvider(AcquisitionRuntime runtime,
                                            ObjectMapper json,
                                            Clock clock,
                                            URI endpoint,
                                            String code,
                                            int providerPriority) {
        this.runtime = runtime;
        this.parser = json.copy().configure(JsonParser.Feature.ALLOW_UNQUOTED_FIELD_NAMES, true);
        this.clock = clock;
        this.endpoint = endpoint;
        this.code = code;
        this.providerPriority = providerPriority;
    }

    @Override public String providerCode() { return code; }
    @Override public String providerFamily() { return "THS"; }
    @Override public String reliabilityFamily() { return code; }
    @Override public int priority() { return providerPriority; }
    @Override public int batchLimit() { return 50; }
    @Override public Duration minimumInterval() { return Duration.ofSeconds(1); }
    @Override public Duration timeout() { return Duration.ofSeconds(10); }
    @Override public Set<ResearchMaterialType> materialTypes() {
        return Collections.singleton(ResearchMaterialType.NEWS_FLASH);
    }

    @Override
    public ProviderResult<List<ResearchMaterial>> fetch(ResearchMaterialType type,
                                                         ResearchMaterialRequest request) {
        if (!supports(type, request)) {
            throw new ProviderContractException("UNSUPPORTED_CAPABILITY",
                    "同花顺快讯不支持该研究资料类型", false);
        }
        AcquisitionRequest acquisition = AcquisitionRequest.get(endpoint)
                .purpose(providerCode())
                .header("Accept", "application/javascript,text/javascript,*/*;q=0.8")
                .header("Referer", "https://news.10jqka.com.cn/")
                .maxResponseBytes(1024 * 1024)
                .maxRetries(0)
                .build();
        try {
            AcquisitionResponse response = runtime.fetch(acquisition);
            JsonNode root = parseRoot(response.getBodyText());
            JsonNode items = root.path("item");
            if (!items.isArray()) {
                throw invalid("同花顺快讯响应缺少 item 数组", null);
            }
            List<ResearchMaterial> result = parseItems(type, request, items);
            List<String> warnings = freshnessWarnings(root.path("pubDate").asText(""));
            return ProviderResult.of(result, LocalDateTime.now(clock), response.getBodySha256(), warnings);
        } catch (ProviderContractException error) {
            throw error;
        } catch (AcquisitionException error) {
            String errorType = error.getHttpStatus() == null
                    ? error.getErrorType().name() : "HTTP_" + error.getHttpStatus();
            throw new ProviderContractException(errorType, error.getMessage(), error.isRetryable(), error);
        } catch (Exception error) {
            throw invalid("同花顺快讯响应无法解析", error);
        }
    }

    private JsonNode parseRoot(String body) throws Exception {
        String script = body == null ? "" : body.trim();
        if (!script.startsWith(SCRIPT_PREFIX)) {
            throw invalid("同花顺快讯响应脚本包装不符合预期", null);
        }
        String objectText = script.substring(SCRIPT_PREFIX.length()).trim();
        if (objectText.endsWith(";")) {
            objectText = objectText.substring(0, objectText.length() - 1).trim();
        }
        JsonNode root = parser.readTree(objectText);
        if (root == null || !root.isObject()) {
            throw invalid("同花顺快讯响应根节点不是对象", null);
        }
        return root;
    }

    private List<ResearchMaterial> parseItems(ResearchMaterialType type,
                                               ResearchMaterialRequest request,
                                               JsonNode items) {
        List<ResearchMaterial> result = new ArrayList<ResearchMaterial>();
        for (JsonNode item : items) {
            if (result.size() >= request.getLimit()) break;
            if (!"1".equals(item.path("isvalid").asText(""))) continue;
            String title = text(item, "title");
            String content = text(item, "content");
            if (title.isEmpty() && content.isEmpty()) continue;
            if (title.isEmpty()) title = content;
            if (content.isEmpty()) content = title;
            if (!ResearchMaterialQueryMatcher.matchesAny(request.getQuery(), title + " " + content)) continue;
            LocalDateTime publishedAt = parsePublishedAt(text(item, "pubDate"));
            if (publishedAt == null) continue;

            ResearchMaterial material = new ResearchMaterial();
            material.setMaterialType(type);
            material.setStockCode(request.getStockCode());
            material.setExternalId(externalId(item, title, content));
            material.setTitle(title);
            material.setContent(content);
            material.setUrl(toHttps(firstText(item, "url", "todayInfoUrl", "curl", "clink")));
            material.setPublishedAt(publishedAt);
            material.setProviderCode(providerCode());
            material.setProviderFamily(providerFamily());
            material.setSourceTier("T2");
            result.add(material);
        }
        return result;
    }

    private List<String> freshnessWarnings(String value) {
        if (value == null || value.trim().isEmpty()) {
            return Collections.singletonList(providerCode() + "：响应缺少数据更新时间");
        }
        try {
            LocalDateTime updatedAt = LocalDateTime.parse(value.trim(), FEED_UPDATED_AT);
            Duration age = Duration.between(updatedAt, LocalDateTime.now(clock));
            if (!age.isNegative() && age.compareTo(STALE_AFTER) > 0) {
                return Collections.singletonList(providerCode() + "：数据超过 24 小时未更新，最新时间 "
                        + updatedAt.format(PUBLISHED_AT));
            }
            return Collections.emptyList();
        } catch (DateTimeParseException error) {
            return Collections.singletonList(providerCode() + "：无法识别数据更新时间");
        }
    }

    private LocalDateTime parsePublishedAt(String value) {
        if (value == null || value.trim().isEmpty()) return null;
        try {
            return LocalDateTime.parse(value.trim(), PUBLISHED_AT);
        } catch (DateTimeParseException error) {
            return null;
        }
    }

    private String externalId(JsonNode item, String title, String content) {
        String seq = text(item, "seq");
        return seq.isEmpty() ? ProviderResult.hashOf(title + "|" + content).substring(0, 24) : seq;
    }

    private String firstText(JsonNode item, String... fields) {
        for (String field : fields) {
            String value = text(item, field);
            if (!value.isEmpty()) return value;
        }
        return "";
    }

    private String text(JsonNode item, String field) {
        return item.path(field).asText("").trim();
    }

    private String toHttps(String value) {
        return value.startsWith("http://") ? "https://" + value.substring("http://".length()) : value;
    }

    private ProviderContractException invalid(String message, Exception cause) {
        return cause == null
                ? new ProviderContractException("INVALID_RESPONSE", message, false)
                : new ProviderContractException("INVALID_RESPONSE", message, false, cause);
    }
}
