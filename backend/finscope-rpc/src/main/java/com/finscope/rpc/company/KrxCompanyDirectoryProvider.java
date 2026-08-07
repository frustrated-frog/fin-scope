package com.finscope.rpc.company;

import com.finscope.domain.company.CompanySearchResult;
import com.finscope.domain.company.CompanySecurity;
import com.finscope.rpc.marketintel.FinanceHttpClient;
import com.finscope.rpc.marketintel.FinanceHttpResponse;
import com.finscope.rpc.marketintel.ProviderContractException;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

@Component
public class KrxCompanyDirectoryProvider implements CompanyDirectoryProvider {
    private static final URI DIRECTORY_URI = URI.create(
            "https://kind.krx.co.kr/corpgeneral/corpList.do?method=download&searchType=13");
    private static final long CACHE_MILLIS = 12L * 60L * 60L * 1000L;
    private final FinanceHttpClient http;
    private volatile List<CompanySearchResult> cached = Collections.emptyList();
    private volatile long cachedAt;

    public KrxCompanyDirectoryProvider(FinanceHttpClient http) {
        this.http = http;
    }

    @Override
    public String providerCode() {
        return "KRX_KIND";
    }

    @Override
    public List<CompanySearchResult> search(String query, int limit) {
        String needle = normalizeAlias(query);
        List<CompanySearchResult> matches = new ArrayList<CompanySearchResult>();
        for (CompanySearchResult company : directory()) {
            if (normalize(company.getNativeName()).contains(needle)
                    || normalize(company.getDisplayName()).contains(needle)
                    || normalize(company.getSecurities().get(0).getSymbol()).contains(needle)) {
                matches.add(company);
            }
        }
        matches.sort(Comparator
                .comparingInt((CompanySearchResult company) -> relevance(company, needle))
                .thenComparingInt(company -> normalize(company.getNativeName()).length()));
        return matches.size() <= limit ? matches : new ArrayList<CompanySearchResult>(matches.subList(0, limit));
    }

    private int relevance(CompanySearchResult company, String needle) {
        String nativeName = normalize(company.getNativeName());
        String displayName = normalize(company.getDisplayName());
        if (nativeName.equals(needle) || displayName.equals(needle)) return 0;
        if ("sk".equals(needle) && "sk하이닉스".equals(nativeName)) return 1;
        if (nativeName.startsWith(needle) || displayName.startsWith(needle)) return 2;
        return 3;
    }

    private String normalizeAlias(String value) {
        String normalized = normalize(value);
        if ("海力士".equals(normalized) || "hynix".equals(normalized)) return normalize("SK하이닉스");
        return normalized;
    }

    private String normalize(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT).replaceAll("\\s+", "");
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
                    Collections.singletonMap("Accept", "text/html"));
            if (response.getStatus() < 200 || response.getStatus() >= 300) {
                throw new ProviderContractException("KRX_DIRECTORY_HTTP_ERROR", "KRX 公司目录请求失败", true);
            }
            List<CompanySearchResult> result = new ArrayList<CompanySearchResult>();
            for (Element row : Jsoup.parse(response.getBody()).select("tr")) {
                Elements cells = row.select("td");
                if (cells.size() < 4) continue;
                String nativeName = cells.get(0).text().trim();
                String symbol = cells.get(2).text().trim();
                if (!symbol.matches("\\d{6}")) continue;
                CompanySearchResult company = new CompanySearchResult();
                company.setProviderCode(providerCode());
                company.setProviderCompanyId("KRX:" + symbol);
                company.setLegalName(nativeName);
                company.setNativeName(nativeName);
                company.setDisplayName(englishName(nativeName));
                company.setCountryCode("KR");
                company.setIndustry(cells.get(3).text().trim());
                company.setCapabilityLevel("L2");
                CompanySecurity security = new CompanySecurity();
                security.setSymbol(symbol);
                security.setExchange("KRX");
                security.setMarket("KR");
                company.getSecurities().add(security);
                result.add(company);
            }
            return result;
        } catch (ProviderContractException error) {
            throw error;
        } catch (Exception error) {
            throw new ProviderContractException("KRX_DIRECTORY_INVALID", "KRX 公司目录解析失败", true, error);
        }
    }

    private String englishName(String nativeName) {
        if ("SK하이닉스".equals(nativeName)) return "SK hynix Inc.";
        if ("SK".equals(nativeName)) return "SK Inc.";
        return nativeName;
    }
}
