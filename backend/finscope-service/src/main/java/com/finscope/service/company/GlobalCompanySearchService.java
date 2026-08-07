package com.finscope.service.company;

import com.finscope.dao.instrument.InstrumentRepository;
import com.finscope.domain.company.CompanySearchResult;
import com.finscope.domain.company.CompanySecurity;
import com.finscope.domain.instrument.Instrument;
import com.finscope.rpc.company.CompanyDirectoryProvider;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
@Slf4j
public class GlobalCompanySearchService {
    private final InstrumentRepository instruments;
    private final List<CompanyDirectoryProvider> providers;

    public GlobalCompanySearchService(InstrumentRepository instruments,
                                      List<CompanyDirectoryProvider> providers) {
        this.instruments = instruments;
        this.providers = providers;
    }

    public List<CompanySearchResult> search(String query, int limit) {
        String needle = normalize(query);
        if (needle.isEmpty()) return new ArrayList<CompanySearchResult>();
        int boundedLimit = Math.max(1, Math.min(limit, 20));
        Map<String, CompanySearchResult> results = new LinkedHashMap<String, CompanySearchResult>();
        addLocal(results, needle, boundedLimit);
        for (CompanyDirectoryProvider provider : providers) {
            if (results.size() >= boundedLimit) break;
            try {
                for (CompanySearchResult item : provider.search(query, boundedLimit - results.size())) {
                    results.putIfAbsent(identity(item), item);
                }
            } catch (RuntimeException error) {
                log.warn("公司目录查询失败 provider={} message={}", provider.providerCode(), error.getMessage());
            }
        }
        List<CompanySearchResult> list = new ArrayList<CompanySearchResult>(results.values());
        return list.size() <= boundedLimit ? list : list.subList(0, boundedLimit);
    }

    private void addLocal(Map<String, CompanySearchResult> results, String needle, int limit) {
        for (Instrument instrument : instruments.findAll()) {
            if (!"STOCK".equals(instrument.getType())) continue;
            if (!normalize(instrument.getName()).contains(needle)
                    && !normalize(instrument.getCode()).contains(needle)
                    && !normalize(instrument.getAliases()).contains(needle)) continue;
            CompanySearchResult item = new CompanySearchResult();
            item.setLocalInstrumentId(instrument.getId());
            item.setProviderCode("LOCAL");
            item.setProviderCompanyId("INSTRUMENT:" + instrument.getId());
            item.setLegalName(instrument.getName());
            item.setDisplayName(instrument.getName());
            item.setNativeName(instrument.getName());
            item.setCountryCode(country(instrument.getMarket()));
            item.setCapabilityLevel(isAshare(instrument.getMarket())
                    || hasSecIdentity(instrument) ? "L4" : "L1");
            CompanySecurity security = new CompanySecurity();
            security.setSymbol(instrument.getCode());
            security.setExchange(instrument.getMarket());
            security.setMarket(instrument.getMarket());
            item.getSecurities().add(security);
            results.put(identity(item), item);
            if (results.size() >= limit) return;
        }
    }

    private String identity(CompanySearchResult item) {
        return item.getProviderCode() + ":" + item.getProviderCompanyId();
    }

    private boolean isAshare(String market) {
        return "SH".equals(market) || "SZ".equals(market) || "BJ".equals(market);
    }

    private boolean hasSecIdentity(Instrument instrument) {
        return "US".equals(instrument.getMarket())
                && instrument.getAliases() != null
                && instrument.getAliases().toUpperCase(Locale.ROOT).contains("SEC_CIK:");
    }

    private String country(String market) {
        if (isAshare(market)) return "CN";
        if ("KR".equals(market) || "KRX".equals(market)) return "KR";
        if ("US".equals(market) || "NASDAQ".equals(market) || "NYSE".equals(market)) return "US";
        return null;
    }

    private String normalize(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}\\p{N}]", "");
    }
}
