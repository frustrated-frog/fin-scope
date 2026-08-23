package com.finscope.rpc.quote;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.finscope.domain.instrument.SectorCategory;
import com.finscope.domain.instrument.SectorMarketEntry;
import com.finscope.domain.instrument.SectorMarketSnapshot;
import com.finscope.domain.marketdata.MarketDataCapability;
import com.finscope.rpc.marketintel.FinanceHttpClient;
import com.finscope.rpc.marketintel.FinanceHttpResponse;
import com.finscope.rpc.marketintel.ProviderContractException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.net.URI;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/** 从本地 Python 市场数据服务读取同花顺板块目录和每日行业资金榜。 */
@Component
public class PythonTonghuashunSectorMarketProvider implements SectorMarketProvider {
    private static final String PROVIDER_CODE = "PYTHON_TONGHUASHUN_SECTOR";
    private static final String SCHEMA_VERSION = "sector-market-v1";
    private static final String SOURCE_FAMILY = "TONGHUASHUN";
    private static final Pattern CODE = Pattern.compile("\\d{6}");
    private static final Set<MarketDataCapability> CAPABILITIES = Collections.singleton(
            MarketDataCapability.SECTOR_CATALOG);

    @Resource
    private FinanceHttpClient http;

    @Value("${finscope.python-market-data.base-url:http://127.0.0.1:8000}")
    private String baseUrl;

    private final ObjectMapper json = new ObjectMapper();

    @Override
    public String providerCode() {
        return PROVIDER_CODE;
    }

    @Override
    public String providerFamily() {
        return SOURCE_FAMILY;
    }

    @Override
    public Set<MarketDataCapability> capabilities() {
        return CAPABILITIES;
    }

    @Override
    public int priority() {
        return 1;
    }

    @Override
    public int batchLimit() {
        return 1;
    }

    @Override
    public Duration minimumInterval() {
        return Duration.ofMillis(500);
    }

    @Override
    public Duration timeout() {
        return Duration.ofSeconds(45);
    }

    @Override
    public boolean supports(SectorCategory category) {
        return category != null;
    }

    @Override
    public SectorMarketSnapshot fetch(SectorCategory category) {
        if (!supports(category)) {
            throw new ProviderContractException(
                    "UNSUPPORTED_SECTOR_CATEGORY", "sector category is required", false);
        }
        try {
            URI uri = URI.create(trimTrailingSlash(baseUrl) + "/v1/sectors/" + category.name());
            FinanceHttpResponse response = http.get(PROVIDER_CODE, uri, Collections.<String, String>emptyMap());
            return parse(response, category);
        } catch (ProviderContractException error) {
            throw error;
        } catch (Exception error) {
            throw new ProviderContractException(
                    "TONGHUASHUN_SECTOR_FETCH_FAILED", message(error), true, error);
        }
    }

    private SectorMarketSnapshot parse(FinanceHttpResponse response, SectorCategory requested) throws Exception {
        JsonNode root = json.readTree(response.getBody());
        if (!SCHEMA_VERSION.equals(text(root, "schema_version"))) {
            throw new ProviderContractException(
                    "SECTOR_SCHEMA_DRIFT", "unsupported sector schema version", false);
        }
        if (!SOURCE_FAMILY.equals(text(root, "source_family"))) {
            throw new ProviderContractException(
                    "SECTOR_SOURCE_DRIFT", "sector source family must be TONGHUASHUN", false);
        }
        if (!requested.name().equals(text(root, "category"))) {
            throw new ProviderContractException(
                    "SECTOR_CATEGORY_DRIFT", "sector response category does not match request", false);
        }
        JsonNode rows = root.path("entries");
        if (!rows.isArray() || rows.isEmpty()) {
            throw new ProviderContractException(
                    "EMPTY_SECTOR_CATALOG", "Tonghuashun sector catalog is empty", true);
        }
        List<SectorMarketEntry> entries = new ArrayList<SectorMarketEntry>();
        List<String> warnings = warnings(root.path("warnings"));
        for (JsonNode row : rows) {
            SectorMarketEntry entry = entry(row, requested);
            if (entry == null) {
                warnings.add("同花顺板块条目缺少有效代码或名称");
                continue;
            }
            entries.add(entry);
        }
        if (entries.isEmpty()) {
            throw new ProviderContractException(
                    "EMPTY_SECTOR_CATALOG", "Tonghuashun sector catalog has no valid entries", true);
        }
        LocalDateTime retrievedAt = LocalDateTime.parse(requiredText(root, "retrieved_at"));
        return new SectorMarketSnapshot(requested, PROVIDER_CODE, retrievedAt,
                response.getPayloadHash(), entries, warnings);
    }

    private SectorMarketEntry entry(JsonNode row, SectorCategory category) {
        String code = text(row, "code");
        String name = text(row, "name");
        if (code == null || !CODE.matcher(code).matches() || name == null
                || !category.name().equals(text(row, "category"))) {
            return null;
        }
        SectorMarketEntry entry = new SectorMarketEntry();
        entry.setCode(code);
        entry.setName(name);
        entry.setCategory(category);
        entry.setSourceRank(integer(row, "source_rank"));
        entry.setMainNetInflow(number(row, "main_net_inflow"));
        entry.setChangePct(number(row, "change_pct"));
        entry.setLeaderStockName(text(row, "leader_stock_name"));
        entry.setAdvanceCount(integer(row, "advance_count"));
        entry.setDeclineCount(integer(row, "decline_count"));
        entry.setFlatCount(integer(row, "flat_count"));
        entry.setBreadthRatio(number(row, "breadth_ratio"));
        return entry;
    }

    private List<String> warnings(JsonNode rows) {
        List<String> values = new ArrayList<String>();
        if (rows.isArray()) {
            for (JsonNode row : rows) {
                if (row.isTextual() && !row.asText().trim().isEmpty()) {
                    values.add(row.asText().trim());
                }
            }
        }
        return values;
    }

    private String requiredText(JsonNode node, String field) {
        String value = text(node, field);
        if (value == null) {
            throw new ProviderContractException(
                    "SECTOR_SCHEMA_DRIFT", "sector response is missing " + field, false);
        }
        return value;
    }

    private String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || !value.isValueNode() || value.isNull()) {
            return null;
        }
        String text = value.asText().trim();
        return text.isEmpty() ? null : text;
    }

    private Double number(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value != null && value.isNumber() ? value.asDouble() : null;
    }

    private Integer integer(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value != null && value.isIntegralNumber() ? value.asInt() : null;
    }

    private String trimTrailingSlash(String value) {
        String normalized = value == null || value.trim().isEmpty()
                ? "http://127.0.0.1:8000" : value.trim();
        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }

    private String message(Exception error) {
        return error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage();
    }
}
