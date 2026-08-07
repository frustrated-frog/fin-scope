package com.finscope.rpc.company;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.finscope.domain.company.CompanySearchResult;
import com.finscope.domain.company.CompanySecurity;
import com.finscope.rpc.marketintel.FinanceHttpClient;
import com.finscope.rpc.marketintel.FinanceHttpResponse;
import com.finscope.rpc.marketintel.ProviderContractException;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Component
public class SecCompanyDirectoryProvider implements CompanyDirectoryProvider {
    private static final URI DIRECTORY_URI = URI.create("https://www.sec.gov/files/company_tickers_exchange.json");
    private static final long CACHE_MILLIS = 12L * 60L * 60L * 1000L;
    private final FinanceHttpClient http;
    private final ObjectMapper mapper = new ObjectMapper();
    private volatile List<CompanySearchResult> cached = Collections.emptyList();
    private volatile long cachedAt;

    public SecCompanyDirectoryProvider(FinanceHttpClient http) {
        this.http = http;
    }

    @Override
    public String providerCode() {
        return "SEC_EDGAR";
    }

    @Override
    public List<CompanySearchResult> search(String query, int limit) {
        String needle = normalizeAlias(query);
        List<CompanySearchResult> matches = new ArrayList<CompanySearchResult>();
        for (CompanySearchResult company : directory()) {
            if (matches(company, needle)) matches.add(company);
            if (matches.size() >= limit) break;
        }
        return matches;
    }

    private boolean matches(CompanySearchResult company, String needle) {
        if (needle.isEmpty()) return false;
        if (normalize(company.getLegalName()).contains(needle)) return true;
        for (CompanySecurity security : company.getSecurities()) {
            if (normalize(security.getSymbol()).contains(needle)) return true;
        }
        return false;
    }

    private String normalizeAlias(String value) {
        String normalized = normalize(value);
        if ("google".equals(normalized) || "谷歌".equals(normalized)) return "alphabet";
        return normalized;
    }

    private String normalize(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}\\p{N}]", "");
    }

    private List<CompanySearchResult> directory() {
        long now = System.currentTimeMillis();
        if (!cached.isEmpty() && now - cachedAt < CACHE_MILLIS) return cached;
        synchronized (this) {
            if (!cached.isEmpty() && now - cachedAt < CACHE_MILLIS) return cached;
            cached = load();
            cachedAt = now;
            return cached;
        }
    }

    private List<CompanySearchResult> load() {
        try {
            FinanceHttpResponse response = http.get(providerCode(), DIRECTORY_URI,
                    Collections.singletonMap("Accept", "application/json"));
            if (response.getStatus() < 200 || response.getStatus() >= 300) {
                throw new ProviderContractException("SEC_DIRECTORY_HTTP_ERROR", "SEC 公司目录请求失败", true);
            }
            JsonNode rows = mapper.readTree(response.getBody()).path("data");
            Map<String, CompanySearchResult> grouped = new LinkedHashMap<String, CompanySearchResult>();
            for (JsonNode row : rows) {
                String cik = String.format("CIK%010d", row.path(0).asLong());
                CompanySearchResult company = grouped.get(cik);
                if (company == null) {
                    company = company(cik, row.path(1).asText());
                    grouped.put(cik, company);
                }
                CompanySecurity security = new CompanySecurity();
                security.setSymbol(row.path(2).asText());
                security.setExchange(row.path(3).asText());
                security.setMarket("US");
                company.getSecurities().add(security);
            }
            return new ArrayList<CompanySearchResult>(grouped.values());
        } catch (ProviderContractException error) {
            throw error;
        } catch (Exception error) {
            throw new ProviderContractException("SEC_DIRECTORY_INVALID", "SEC 公司目录解析失败", true, error);
        }
    }

    private CompanySearchResult company(String cik, String name) {
        CompanySearchResult company = new CompanySearchResult();
        company.setProviderCode(providerCode());
        company.setProviderCompanyId(cik);
        company.setLegalName(name);
        company.setDisplayName(name);
        company.setCountryCode("US");
        company.setCapabilityLevel("L4");
        return company;
    }
}
