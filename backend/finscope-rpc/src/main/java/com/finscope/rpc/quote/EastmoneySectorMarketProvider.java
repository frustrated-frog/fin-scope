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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.time.Instant;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/** 东方财富行业与概念板块完整目录适配器。 */
@Component
public class EastmoneySectorMarketProvider implements SectorMarketProvider {
    private static final String ENDPOINT = "https://push2.eastmoney.com/api/qt/clist/get";
    private static final String INDUSTRY_FILTER = "m%3A90%2Bt%3A2%2Bf%3A%2150";
    private static final String CONCEPT_FILTER = "m%3A90%2Bt%3A3%2Bf%3A%2150";
    private static final String FIELDS = "f2%2Cf3%2Cf4%2Cf6%2Cf12%2Cf14%2Cf128%2Cf140%2Cf136%2Cf124";
    private static final Pattern CODE = Pattern.compile("BK\\d{4}");

    private final FinanceHttpClient http;
    private final ObjectMapper json = new ObjectMapper();
    private static final Set<MarketDataCapability> CAPABILITIES = Collections.singleton(
            MarketDataCapability.SECTOR_CATALOG);

    @Autowired
    public EastmoneySectorMarketProvider(FinanceHttpClient http) {
        this.http = http;
    }

    @Override
    public String providerCode() {
        return "EASTMONEY_SECTOR_CATALOG";
    }

    @Override
    public String providerFamily() { return "EASTMONEY"; }

    @Override
    public Set<MarketDataCapability> capabilities() { return CAPABILITIES; }

    @Override
    public int priority() { return 10; }

    @Override
    public int batchLimit() { return 1; }

    @Override
    public Duration minimumInterval() { return Duration.ofMillis(800); }

    @Override
    public Duration timeout() { return Duration.ofSeconds(10); }

    @Override
    public boolean supports(SectorCategory category) {
        return category != null;
    }

    @Override
    public SectorMarketSnapshot fetch(SectorCategory category) {
        if (!supports(category)) {
            throw new ProviderContractException("UNSUPPORTED_SECTOR_CATEGORY", "sector category is required", false);
        }
        try {
            FinanceHttpResponse response = http.get(providerCode(), requestUri(category),
                    Collections.singletonMap("Referer", "https://quote.eastmoney.com"));
            JsonNode rows = readRows(response);
            List<String> warnings = new ArrayList<String>();
            Map<String, SectorMarketEntry> byCode = new LinkedHashMap<String, SectorMarketEntry>();
            for (JsonNode row : rows) {
                SectorMarketEntry entry = parse(row, category, response.getRetrievedAt(), warnings);
                if (entry == null) continue;
                SectorMarketEntry existing = byCode.get(entry.getCode());
                if (existing == null || completeness(entry) > completeness(existing)) {
                    if (existing != null) warnings.add("duplicate sector code replaced: " + entry.getCode());
                    byCode.put(entry.getCode(), entry);
                } else {
                    warnings.add("duplicate sector code ignored: " + entry.getCode());
                }
            }
            return new SectorMarketSnapshot(category, providerCode(),
                    LocalDateTime.ofInstant(response.getRetrievedAt(), ZoneId.systemDefault()),
                    response.getPayloadHash(), new ArrayList<SectorMarketEntry>(byCode.values()), warnings);
        } catch (ProviderContractException error) {
            throw error;
        } catch (Exception error) {
            throw new ProviderContractException("SECTOR_CATALOG_FETCH_FAILED", error.getMessage(), true, error);
        }
    }

    private URI requestUri(SectorCategory category) {
        String filter = category == SectorCategory.INDUSTRY ? INDUSTRY_FILTER : CONCEPT_FILTER;
        return URI.create(ENDPOINT + "?pn=1&pz=1000&po=1&np=1&fltt=2&invt=2&fid=f3&fs="
                + filter + "&fields=" + FIELDS);
    }

    private JsonNode readRows(FinanceHttpResponse response) throws Exception {
        JsonNode data = json.readTree(response.getBody()).path("data");
        JsonNode rows = data.path("diff");
        if (data.isMissingNode() || data.isNull() || !rows.isArray()) {
            throw new ProviderContractException("SCHEMA_DRIFT", "Eastmoney sector response is missing data.diff", false);
        }
        return rows;
    }

    private SectorMarketEntry parse(JsonNode row, SectorCategory category, Instant retrievedAt, List<String> warnings) {
        String code = text(row, "f12");
        String name = text(row, "f14");
        if (code == null || !CODE.matcher(code).matches()) {
            warnings.add("invalid sector code: " + String.valueOf(code));
            return null;
        }
        if (name == null) {
            warnings.add("missing sector name: " + code);
            return null;
        }
        SectorMarketEntry entry = new SectorMarketEntry();
        entry.setCode(code);
        entry.setName(name);
        entry.setCategory(category);
        entry.setPrice(number(row, "f2"));
        entry.setChangePct(number(row, "f3"));
        entry.setChangeAmount(number(row, "f4"));
        entry.setTurnover(number(row, "f6"));
        entry.setLeaderStockName(text(row, "f128"));
        entry.setLeaderStockCode(text(row, "f140"));
        entry.setLeaderStockChangePct(number(row, "f136"));
        entry.setQuoteTime(quoteTime(row, retrievedAt));
        return entry;
    }

    private LocalDateTime quoteTime(JsonNode row, Instant retrievedAt) {
        JsonNode epoch = row.path("f124");
        Instant value = epoch.isNumber() && epoch.asLong() > 0 ? Instant.ofEpochSecond(epoch.asLong()) : retrievedAt;
        return LocalDateTime.ofInstant(value, ZoneId.systemDefault());
    }

    private String text(JsonNode row, String field) {
        JsonNode value = row.path(field);
        if (!value.isTextual()) return null;
        String text = value.asText().trim();
        return text.isEmpty() || "-".equals(text) ? null : text;
    }

    private Double number(JsonNode row, String field) {
        JsonNode value = row.path(field);
        if (value.isNumber()) return value.asDouble();
        if (value.isTextual()) {
            String text = value.asText().trim();
            if (text.isEmpty() || "-".equals(text)) return null;
            try { return Double.valueOf(text); } catch (NumberFormatException ignored) { return null; }
        }
        return null;
    }

    private int completeness(SectorMarketEntry entry) {
        int value = 2;
        if (entry.getPrice() != null) value++;
        if (entry.getChangePct() != null) value++;
        if (entry.getTurnover() != null) value++;
        if (entry.getLeaderStockName() != null) value++;
        if (entry.getLeaderStockCode() != null) value++;
        if (entry.getLeaderStockChangePct() != null) value++;
        return value;
    }
}
