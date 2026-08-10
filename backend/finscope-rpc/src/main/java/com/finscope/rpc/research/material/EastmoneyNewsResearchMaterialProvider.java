package com.finscope.rpc.research.material;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.finscope.domain.research.material.ResearchMaterial;
import com.finscope.domain.research.material.ResearchMaterialType;
import com.finscope.rpc.acquisition.AcquisitionException;
import com.finscope.rpc.acquisition.AcquisitionRequest;
import com.finscope.rpc.acquisition.AcquisitionResponse;
import com.finscope.rpc.acquisition.AcquisitionRuntime;
import com.finscope.rpc.marketdata.ProviderResult;
import com.finscope.rpc.marketintel.ProviderContractException;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

@Component
public class EastmoneyNewsResearchMaterialProvider implements ResearchMaterialProvider {
    private static final String SCRIPT_PREFIX = "var ajaxResult=";
    private static final String ENDPOINT_TEMPLATE =
            "https://newsapi.eastmoney.com/kuaixun/v1/getlist_102_ajaxResult_%d_1_.html";
    private static final DateTimeFormatter PUBLISHED_AT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final AcquisitionRuntime runtime;
    private final ObjectMapper json;

    public EastmoneyNewsResearchMaterialProvider(AcquisitionRuntime runtime, ObjectMapper json) {
        this.runtime = runtime;
        this.json = json;
    }

    @Override public String providerCode() { return "EASTMONEY_NEWS_FLASH"; }
    @Override public String providerFamily() { return "EASTMONEY"; }
    @Override public String reliabilityFamily() { return "EASTMONEY_NEWS_FLASH"; }
    @Override public int priority() { return 12; }
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
                    "东方财富快讯不支持该研究资料类型", false);
        }
        URI endpoint = URI.create(String.format(ENDPOINT_TEMPLATE, request.getLimit()));
        AcquisitionRequest acquisition = AcquisitionRequest.get(endpoint)
                .purpose(providerCode())
                .header("Accept", "application/javascript,text/javascript,*/*;q=0.8")
                .header("Referer", "https://kuaixun.eastmoney.com/")
                .maxResponseBytes(2 * 1024 * 1024)
                .maxRetries(0)
                .build();
        try {
            AcquisitionResponse response = runtime.fetch(acquisition);
            JsonNode root = parseRoot(response.getBodyText());
            JsonNode items = root.path("LivesList");
            if (!items.isArray()) {
                throw invalid("东方财富快讯响应缺少 LivesList 数组", null);
            }
            List<ResearchMaterial> result = parseItems(type, request, items);
            return ProviderResult.of(result, LocalDateTime.now(), response.getBodySha256(),
                    Collections.emptyList());
        } catch (ProviderContractException error) {
            throw error;
        } catch (AcquisitionException error) {
            String errorType = error.getHttpStatus() == null
                    ? error.getErrorType().name() : "HTTP_" + error.getHttpStatus();
            throw new ProviderContractException(errorType, error.getMessage(),
                    error.isRetryable(), error);
        } catch (Exception error) {
            throw invalid("东方财富快讯响应无法解析", error);
        }
    }

    private JsonNode parseRoot(String body) throws Exception {
        String script = body == null ? "" : body.trim();
        if (!script.startsWith(SCRIPT_PREFIX)) {
            throw invalid("东方财富快讯响应脚本包装不符合预期", null);
        }
        String objectText = script.substring(SCRIPT_PREFIX.length()).trim();
        if (objectText.endsWith(";")) {
            objectText = objectText.substring(0, objectText.length() - 1).trim();
        }
        JsonNode root = json.readTree(objectText);
        if (root == null || !root.isObject()) {
            throw invalid("东方财富快讯响应根节点不是对象", null);
        }
        return root;
    }

    private List<ResearchMaterial> parseItems(ResearchMaterialType type,
                                               ResearchMaterialRequest request,
                                               JsonNode items) {
        List<ResearchMaterial> result = new ArrayList<ResearchMaterial>();
        for (JsonNode item : items) {
            if (result.size() >= request.getLimit()) {
                break;
            }
            String externalId = firstText(item, "id", "newsid");
            String title = firstText(item, "title", "simtitle");
            if (externalId.isEmpty() || title.isEmpty()) {
                continue;
            }
            String content = firstText(item, "digest", "simdigest");
            if (content.isEmpty()) {
                content = title;
            }
            if (!ResearchMaterialQueryMatcher.matchesAny(request.getQuery(), title + " " + content)) {
                continue;
            }

            ResearchMaterial material = new ResearchMaterial();
            material.setMaterialType(type);
            material.setStockCode(request.getStockCode());
            material.setExternalId(externalId);
            material.setTitle(title);
            material.setContent(content);
            material.setUrl(toHttps(firstText(item, "url_unique", "url_w", "url_m")));
            material.setPublishedAt(parsePublishedAt(firstText(item, "showtime", "ordertime")));
            material.setProviderCode(providerCode());
            material.setProviderFamily(providerFamily());
            material.setSourceTier("T2");
            result.add(material);
        }
        return result;
    }

    private LocalDateTime parsePublishedAt(String value) {
        if (value.isEmpty()) {
            return null;
        }
        try {
            return LocalDateTime.parse(value, PUBLISHED_AT);
        } catch (DateTimeParseException error) {
            return null;
        }
    }

    private String firstText(JsonNode item, String... fields) {
        for (String field : fields) {
            String value = item.path(field).asText("").trim();
            if (!value.isEmpty()) {
                return value;
            }
        }
        return "";
    }

    private String toHttps(String value) {
        if (value.startsWith("http://")) {
            return "https://" + value.substring("http://".length());
        }
        return value;
    }

    private ProviderContractException invalid(String message, Throwable cause) {
        return new ProviderContractException("INVALID_RESPONSE", message, false, cause);
    }
}
