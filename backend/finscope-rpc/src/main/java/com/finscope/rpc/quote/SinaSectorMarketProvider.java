package com.finscope.rpc.quote;

import com.fasterxml.jackson.core.type.TypeReference;
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
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** 新浪行业与概念板块目录；作为与东方财富故障域独立的在线备源。 */
@Component
public class SinaSectorMarketProvider implements SectorMarketProvider {
    private static final URI INDUSTRY = URI.create(
            "https://vip.stock.finance.sina.com.cn/q/view/newSinaHy.php");
    private static final URI CONCEPT = URI.create(
            "https://vip.stock.finance.sina.com.cn/q/view/newFLJK.php?param=class");
    private static final Set<MarketDataCapability> CAPABILITIES = Collections.singleton(
            MarketDataCapability.SECTOR_CATALOG);

    private final FinanceHttpClient http;
    private final ObjectMapper json = new ObjectMapper();

    @Autowired
    public SinaSectorMarketProvider(FinanceHttpClient http) {
        this.http = http;
    }

    @Override public String providerCode() { return "SINA_SECTOR_CATALOG"; }
    @Override public String providerFamily() { return "SINA"; }
    @Override public Set<MarketDataCapability> capabilities() { return CAPABILITIES; }
    @Override public int priority() { return 20; }
    @Override public int batchLimit() { return 1; }
    @Override public Duration minimumInterval() { return Duration.ofMillis(500); }
    @Override public Duration timeout() { return Duration.ofSeconds(6); }
    @Override public boolean supports(SectorCategory category) { return category != null; }

    @Override
    public SectorMarketSnapshot fetch(SectorCategory category) {
        if (!supports(category)) {
            throw new ProviderContractException(
                    "UNSUPPORTED_SECTOR_CATEGORY", "sector category is required", false);
        }
        try {
            FinanceHttpResponse response = http.get(providerCode(),
                    category == SectorCategory.INDUSTRY ? INDUSTRY : CONCEPT,
                    Collections.singletonMap("Referer", "https://finance.sina.com.cn/stock/"));
            Map<String, String> rows = parseJavascriptObject(response.getBody());
            List<SectorMarketEntry> entries = new ArrayList<SectorMarketEntry>();
            List<String> warnings = new ArrayList<String>();
            for (Map.Entry<String, String> row : rows.entrySet()) {
                SectorMarketEntry entry = parse(row.getKey(), row.getValue(), category,
                        LocalDateTime.ofInstant(response.getRetrievedAt(), ZoneId.systemDefault()));
                if (entry == null) warnings.add("invalid Sina sector row: " + row.getKey());
                else entries.add(entry);
            }
            if (entries.isEmpty()) {
                throw new ProviderContractException(
                        "EMPTY_SECTOR_CATALOG", "Sina sector response has no valid rows", true);
            }
            warnings.add("SINA_SECTOR_CODE_REQUIRES_BK_MAPPING");
            return new SectorMarketSnapshot(category, providerCode(),
                    LocalDateTime.ofInstant(response.getRetrievedAt(), ZoneId.systemDefault()),
                    response.getPayloadHash(), entries, warnings);
        } catch (ProviderContractException error) {
            throw error;
        } catch (Exception error) {
            throw new ProviderContractException(
                    "SINA_SECTOR_FETCH_FAILED", error.getMessage(), true, error);
        }
    }

    private Map<String, String> parseJavascriptObject(String body) throws Exception {
        int start = body == null ? -1 : body.indexOf('{');
        int end = body == null ? -1 : body.lastIndexOf('}');
        if (start < 0 || end <= start) {
            throw new ProviderContractException(
                    "SCHEMA_DRIFT", "Sina sector response is missing its data object", false);
        }
        return json.readValue(body.substring(start, end + 1),
                new TypeReference<LinkedHashMap<String, String>>() { });
    }

    private SectorMarketEntry parse(String key, String value, SectorCategory category,
                                    LocalDateTime quoteTime) {
        if (key == null || value == null) return null;
        String[] fields = value.split(",", -1);
        if (fields.length < 13 || blank(fields[1])) return null;
        SectorMarketEntry entry = new SectorMarketEntry();
        entry.setCode("SINA:" + key.trim());
        entry.setName(fields[1].trim());
        entry.setCategory(category);
        entry.setPrice(number(fields[3]));
        entry.setChangeAmount(number(fields[4]));
        entry.setChangePct(number(fields[5]));
        entry.setTurnover(number(fields[7]));
        entry.setLeaderStockCode(text(fields[8]));
        entry.setLeaderStockChangePct(number(fields[9]));
        entry.setLeaderStockName(text(fields[12]));
        entry.setQuoteTime(quoteTime);
        return entry;
    }

    private boolean blank(String value) { return value == null || value.trim().isEmpty(); }
    private String text(String value) { return blank(value) || "-".equals(value.trim()) ? null : value.trim(); }
    private Double number(String value) {
        String text = text(value);
        if (text == null) return null;
        try { return Double.valueOf(text); } catch (NumberFormatException ignored) { return null; }
    }
}
