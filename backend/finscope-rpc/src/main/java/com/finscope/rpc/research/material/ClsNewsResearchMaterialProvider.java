package com.finscope.rpc.research.material;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.finscope.domain.research.material.ResearchMaterial;
import com.finscope.domain.research.material.ResearchMaterialType;
import com.finscope.rpc.acquisition.AcquisitionRequest;
import com.finscope.rpc.acquisition.AcquisitionRuntime;
import com.finscope.rpc.marketdata.ProviderResult;
import com.finscope.rpc.marketintel.ProviderContractException;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

@Component
public class ClsNewsResearchMaterialProvider implements ResearchMaterialProvider {
    private final AcquisitionRuntime runtime;
    private final ObjectMapper json;

    public ClsNewsResearchMaterialProvider(AcquisitionRuntime runtime, ObjectMapper json) {
        this.runtime = runtime;
        this.json = json;
    }

    @Override public String providerCode() { return "CLS_NEWS_FLASH"; }
    @Override public String providerFamily() { return "CLS"; }
    @Override public int priority() { return 10; }
    @Override public int batchLimit() { return 50; }
    @Override public Duration minimumInterval() { return Duration.ofMillis(500); }
    @Override public Duration timeout() { return Duration.ofSeconds(10); }
    @Override public Set<ResearchMaterialType> materialTypes() { return Collections.singleton(ResearchMaterialType.NEWS_FLASH); }

    @Override
    public ProviderResult<List<ResearchMaterial>> fetch(ResearchMaterialType type, ResearchMaterialRequest request) {
        if (!supports(type, request)) {
            throw new ProviderContractException("UNSUPPORTED_CAPABILITY", "财联社不支持该研究资料类型", false);
        }
        String query = "appName=CailianpressWeb&last_time=&os=web&refresh_type=1&rn="
                + Math.min(50, request.getLimit()) + "&sv=7.7.5";
        String sign = md5(sha1(query));
        URI uri = URI.create("https://www.cls.cn/v1/roll/get_roll_list?" + query + "&sign=" + sign);
        AcquisitionRequest acquisition = AcquisitionRequest.get(uri).purpose("CLS_NEWS_FLASH")
                .header("Referer", "https://www.cls.cn/").maxResponseBytes(2 * 1024 * 1024)
                .maxRetries(0).build();
        try {
            JsonNode rows = json.readTree(runtime.fetch(acquisition).getBodyText()).path("data").path("roll_data");
            if (!rows.isArray()) throw new ProviderContractException("INVALID_RESPONSE", "财联社响应缺少 roll_data", false);
            List<ResearchMaterial> result = new ArrayList<ResearchMaterial>();
            for (JsonNode item : rows) {
                String title = item.path("title").asText(item.path("brief").asText("")).trim();
                String content = item.path("content").asText(item.path("brief").asText("")).trim();
                if (title.isEmpty() || !matches(request.getQuery(), title + " " + content)) continue;
                ResearchMaterial value = new ResearchMaterial();
                value.setMaterialType(type); value.setStockCode(request.getStockCode());
                value.setExternalId(item.path("id").asText(ProviderResult.hashOf(title).substring(0, 24)));
                value.setTitle(title); value.setContent(content); value.setUrl("https://www.cls.cn/telegraph");
                long seconds = item.path("ctime").asLong(0L);
                if (seconds > 0L) value.setPublishedAt(LocalDateTime.ofInstant(Instant.ofEpochSecond(seconds), ZoneId.systemDefault()));
                value.setProviderCode(providerCode()); value.setProviderFamily(providerFamily()); value.setSourceTier("T2");
                result.add(value);
            }
            return ProviderResult.of(result, LocalDateTime.now(), ProviderResult.hashOf(result), Collections.emptyList());
        } catch (ProviderContractException error) {
            throw error;
        } catch (Exception error) {
            throw new ProviderContractException("INVALID_RESPONSE", "财联社快讯响应无法解析", false, error);
        }
    }

    private boolean matches(String query, String value) { return query == null || query.trim().isEmpty() || value.contains(query.trim()); }
    private String sha1(String value) { return digest("SHA-1", value); }
    private String md5(String value) { return digest("MD5", value); }
    private String digest(String algorithm, String value) {
        try {
            byte[] bytes = MessageDigest.getInstance(algorithm).digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte item : bytes) hex.append(String.format("%02x", item & 0xff));
            return hex.toString();
        } catch (Exception error) {
            throw new IllegalStateException("JDK 缺少摘要算法：" + algorithm, error);
        }
    }
}
