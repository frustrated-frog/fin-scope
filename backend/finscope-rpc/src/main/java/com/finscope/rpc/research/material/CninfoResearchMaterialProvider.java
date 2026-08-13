package com.finscope.rpc.research.material;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.finscope.domain.research.material.ResearchMaterial;
import com.finscope.common.enums.research.ResearchMaterialType;
import com.finscope.rpc.acquisition.AcquisitionRequest;
import com.finscope.rpc.acquisition.AcquisitionResponse;
import com.finscope.rpc.acquisition.AcquisitionRuntime;
import com.finscope.rpc.marketdata.ProviderResult;
import com.finscope.rpc.marketintel.ProviderContractException;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Component
public class CninfoResearchMaterialProvider implements ResearchMaterialProvider {
    private static final URI STOCK_MAP = URI.create("https://www.cninfo.com.cn/new/data/szse_stock.json");
    private static final URI ANNOUNCEMENTS = URI.create("https://www.cninfo.com.cn/new/hisAnnouncement/query");
    private static final URI IRM_LOOKUP = URI.create("https://irm.cninfo.com.cn/newircs/index/queryKeyboardInfo");
    private static final String IRM_QUESTIONS = "https://irm.cninfo.com.cn/newircs/company/question";
    private final AcquisitionRuntime runtime;
    private final ObjectMapper json;
    private volatile Map<String, String> orgIds = Collections.emptyMap();

    public CninfoResearchMaterialProvider(AcquisitionRuntime runtime, ObjectMapper json) {
        this.runtime = runtime;
        this.json = json;
    }

    @Override public String providerCode() { return "CNINFO"; }
    @Override public String providerFamily() { return "CNINFO"; }
    @Override public int priority() { return 10; }
    @Override public int batchLimit() { return 30; }
    @Override public Duration minimumInterval() { return Duration.ofMillis(300); }
    @Override public Duration timeout() { return Duration.ofSeconds(15); }
    @Override public Set<ResearchMaterialType> materialTypes() {
        return new java.util.LinkedHashSet<ResearchMaterialType>(Arrays.asList(
                ResearchMaterialType.ANNOUNCEMENT, ResearchMaterialType.INTERACTION));
    }

    @Override
    public ProviderResult<List<ResearchMaterial>> fetch(ResearchMaterialType type, ResearchMaterialRequest request) {
        if (!supports(type, request)) {
            throw new ProviderContractException("UNSUPPORTED_CAPABILITY", "巨潮不支持该研究资料类型", false);
        }
        List<ResearchMaterial> data = type == ResearchMaterialType.ANNOUNCEMENT
                ? announcements(request) : interactions(request);
        return ProviderResult.of(data, LocalDateTime.now(), ProviderResult.hashOf(data), Collections.emptyList());
    }

    private List<ResearchMaterial> announcements(ResearchMaterialRequest request) {
        String orgId = announcementOrgId(request.getStockCode());
        Map<String, String> form = new LinkedHashMap<String, String>();
        form.put("stock", request.getStockCode() + "," + orgId);
        form.put("tabName", "fulltext");
        form.put("pageSize", String.valueOf(request.getLimit()));
        form.put("pageNum", "1");
        form.put("column", ""); form.put("category", ""); form.put("plate", "");
        form.put("seDate", ""); form.put("searchkey", ""); form.put("secid", "");
        form.put("sortName", ""); form.put("sortType", ""); form.put("isHLtitle", "true");
        AcquisitionResponse response = runtime.fetch(postForm(ANNOUNCEMENTS, form, "CNINFO_ANNOUNCEMENT"));
        try {
            JsonNode rows = json.readTree(response.getBodyText()).path("announcements");
            if (!rows.isArray()) throw invalid("巨潮公告响应缺少 announcements");
            List<ResearchMaterial> result = new ArrayList<ResearchMaterial>();
            for (JsonNode item : rows) {
                String id = text(item, "announcementId");
                String title = cleanTitle(text(item, "announcementTitle"));
                if (blank(id) || blank(title) || !matches(request.getQuery(), title)) continue;
                ResearchMaterial value = base(ResearchMaterialType.ANNOUNCEMENT, request.getStockCode(), id);
                value.setTitle(title);
                value.setContent(join(text(item, "announcementTypeName"), title));
                value.setUrl(announcementUrl(text(item, "adjunctUrl"), id));
                value.setPublishedAt(epochMillis(item.path("announcementTime").asLong(0L)));
                result.add(value);
            }
            return result;
        } catch (ProviderContractException error) {
            throw error;
        } catch (Exception error) {
            throw invalid("巨潮公告响应无法解析", error);
        }
    }

    private List<ResearchMaterial> interactions(ResearchMaterialRequest request) {
        Map<String, String> lookup = Collections.singletonMap("keyWord", request.getStockCode());
        AcquisitionResponse lookupResponse = runtime.fetch(postForm(IRM_LOOKUP, lookup, "CNINFO_INTERACTION_LOOKUP"));
        try {
            JsonNode companies = json.readTree(lookupResponse.getBodyText()).path("data");
            if (!companies.isArray() || companies.size() == 0 || blank(text(companies.get(0), "secid"))) {
                return Collections.emptyList();
            }
            String orgId = text(companies.get(0), "secid");
            String uri = IRM_QUESTIONS + "?_t=1&stockcode=" + request.getStockCode()
                    + "&orgId=" + encode(orgId) + "&pageSize=" + request.getLimit()
                    + "&pageNum=1&keyWord=&startDay=&endDay=";
            AcquisitionRequest query = AcquisitionRequest.post(URI.create(uri), "", "application/x-www-form-urlencoded")
                    .purpose("CNINFO_INTERACTION").maxResponseBytes(2 * 1024 * 1024).maxRetries(0).build();
            JsonNode rows = json.readTree(runtime.fetch(query).getBodyText()).path("rows");
            if (!rows.isArray()) throw invalid("互动易响应缺少 rows");
            List<ResearchMaterial> result = new ArrayList<ResearchMaterial>();
            for (JsonNode item : rows) {
                String answer = text(item, "attachedContent");
                String question = text(item, "mainContent");
                if (blank(answer) || !request.getStockCode().equals(text(item, "stockCode"))
                        || !matches(request.getQuery(), join(question, answer))) continue;
                String id = text(item, "questionId");
                if (blank(id)) id = ProviderResult.hashOf(join(question, answer)).substring(0, 24);
                ResearchMaterial value = base(ResearchMaterialType.INTERACTION, request.getStockCode(), id);
                value.setTitle("公司互动回复：" + compact(question, 80));
                value.setContent("问题：" + question + "\n公司回复：" + answer);
                value.setUrl("https://irm.cninfo.com.cn/ircs/company/companyDetail?stockcode="
                        + request.getStockCode() + "&orgId=" + encode(orgId) + "&questionId=" + encode(id));
                value.setPublishedAt(epochMillis(item.path("pubDate").asLong(0L)));
                result.add(value);
            }
            return result;
        } catch (ProviderContractException error) {
            throw error;
        } catch (Exception error) {
            throw invalid("互动易响应无法解析", error);
        }
    }

    private String announcementOrgId(String stockCode) {
        String cached = orgIds.get(stockCode);
        if (!blank(cached)) return cached;
        synchronized (this) {
            cached = orgIds.get(stockCode);
            if (!blank(cached)) return cached;
            try {
                AcquisitionRequest request = AcquisitionRequest.get(STOCK_MAP).purpose("CNINFO_ORG_MAP")
                        .maxResponseBytes(4 * 1024 * 1024).maxRetries(0).build();
                JsonNode stocks = json.readTree(runtime.fetch(request).getBodyText()).path("stockList");
                if (!stocks.isArray()) throw invalid("巨潮股票映射响应缺少 stockList");
                Map<String, String> loaded = new LinkedHashMap<String, String>();
                for (JsonNode item : stocks) {
                    String code = text(item, "code");
                    String orgId = text(item, "orgId");
                    if (!blank(code) && !blank(orgId)) loaded.put(code, orgId);
                }
                orgIds = Collections.unmodifiableMap(loaded);
                cached = orgIds.get(stockCode);
                if (blank(cached)) throw invalid("巨潮股票映射不存在：" + stockCode);
                return cached;
            } catch (ProviderContractException error) {
                throw error;
            } catch (Exception error) {
                throw invalid("巨潮股票映射无法解析", error);
            }
        }
    }

    private AcquisitionRequest postForm(URI uri, Map<String, String> values, String purpose) {
        StringBuilder body = new StringBuilder();
        for (Map.Entry<String, String> entry : values.entrySet()) {
            if (body.length() > 0) body.append('&');
            body.append(encode(entry.getKey())).append('=').append(encode(entry.getValue()));
        }
        return AcquisitionRequest.post(uri, body.toString(), "application/x-www-form-urlencoded")
                .purpose(purpose).header("Referer", "https://www.cninfo.com.cn/")
                .maxResponseBytes(4 * 1024 * 1024).maxRetries(0).build();
    }

    private ResearchMaterial base(ResearchMaterialType type, String code, String id) {
        ResearchMaterial value = new ResearchMaterial();
        value.setMaterialType(type); value.setStockCode(code); value.setExternalId(id);
        value.setProviderCode(providerCode()); value.setProviderFamily(providerFamily()); value.setSourceTier("T1");
        return value;
    }

    private LocalDateTime epochMillis(long value) {
        return value <= 0L ? null : LocalDateTime.ofInstant(Instant.ofEpochMilli(value), ZoneId.systemDefault());
    }
    private String text(JsonNode node, String field) { return node == null ? "" : node.path(field).asText("").trim(); }
    private String cleanTitle(String value) { return value == null ? "" : value.replaceAll("<[^>]+>", "").trim(); }
    private boolean matches(String query, String value) {
        return ResearchMaterialQueryMatcher.matchesAny(query, value);
    }
    private String announcementUrl(String adjunctUrl, String id) {
        String path = adjunctUrl == null ? "" : adjunctUrl.trim();
        if (!path.isEmpty() && !path.contains("://") && !path.contains("..")) {
            return "https://static.cninfo.com.cn/" + (path.startsWith("/") ? path.substring(1) : path);
        }
        return "https://www.cninfo.com.cn/new/disclosure/detail?annoId=" + encode(id);
    }
    private boolean blank(String value) { return value == null || value.trim().isEmpty(); }
    private String join(String left, String right) { return (left == null ? "" : left) + " " + (right == null ? "" : right); }
    private String compact(String value, int max) { String text = value == null ? "" : value.replaceAll("\\s+", " ").trim(); return text.length() <= max ? text : text.substring(0, max); }
    private String encode(String value) { try { return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8.name()); } catch (Exception e) { throw new IllegalStateException(e); } }
    private ProviderContractException invalid(String message) { return new ProviderContractException("INVALID_RESPONSE", message, false); }
    private ProviderContractException invalid(String message, Exception error) { return new ProviderContractException("INVALID_RESPONSE", message, false, error); }
}
