package com.finscope.service.company;

import com.finscope.dao.instrument.InstrumentRepository;
import com.finscope.domain.company.CompanySearchResult;
import com.finscope.domain.instrument.Instrument;
import com.finscope.rpc.company.CompanyDirectoryProvider;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GlobalCompanySearchServiceTest {
    @Test
    void mergesLocalAndRemoteCompaniesWithoutLettingOneProviderFailureBreakSearch() {
        InstrumentRepository instruments = mock(InstrumentRepository.class);
        when(instruments.findAll()).thenReturn(Collections.singletonList(stock(7L, "600519", "贵州茅台", "SH")));
        CompanyDirectoryProvider sec = provider("SEC_EDGAR", result("SEC_EDGAR", "CIK0000320193", "Apple Inc."));
        CompanyDirectoryProvider unavailable = new CompanyDirectoryProvider() {
            public String providerCode() { return "UNAVAILABLE"; }
            public List<CompanySearchResult> search(String query, int limit) { throw new IllegalStateException("offline"); }
        };
        GlobalCompanySearchService service = new GlobalCompanySearchService(instruments, Arrays.asList(sec, unavailable));

        List<CompanySearchResult> apple = service.search("Apple", 8);
        List<CompanySearchResult> maotai = service.search("茅台", 8);

        assertEquals(1, apple.size());
        assertEquals("Apple Inc.", apple.get(0).getDisplayName());
        assertEquals(1, maotai.size());
        assertEquals(Long.valueOf(7L), maotai.get(0).getLocalInstrumentId());
        assertEquals("L4", maotai.get(0).getCapabilityLevel());
    }

    @Test
    void keepsPreviouslyFetchedSecCompaniesAvailableForFullLocalAnalysis() {
        InstrumentRepository instruments = mock(InstrumentRepository.class);
        Instrument apple = stock(71L, "AAPL", "Apple Inc.", "US");
        apple.setAliases("SEC_CIK:0000320193");
        when(instruments.findAll()).thenReturn(Collections.singletonList(apple));

        List<CompanySearchResult> results = new GlobalCompanySearchService(
                instruments, Collections.emptyList()).search("Apple", 8);

        assertEquals(1, results.size());
        assertEquals("L4", results.get(0).getCapabilityLevel());
        assertEquals(Long.valueOf(71L), results.get(0).getLocalInstrumentId());
    }

    private static CompanyDirectoryProvider provider(String code, CompanySearchResult result) {
        return new CompanyDirectoryProvider() {
            public String providerCode() { return code; }
            public List<CompanySearchResult> search(String query, int limit) {
                return result.getDisplayName().toLowerCase().contains(query.toLowerCase())
                        ? Collections.singletonList(result) : Collections.<CompanySearchResult>emptyList();
            }
        };
    }

    private static CompanySearchResult result(String provider, String id, String name) {
        CompanySearchResult value = new CompanySearchResult();
        value.setProviderCode(provider);
        value.setProviderCompanyId(id);
        value.setDisplayName(name);
        value.setLegalName(name);
        value.setCountryCode("US");
        value.setCapabilityLevel("L4");
        return value;
    }

    private static Instrument stock(Long id, String code, String name, String market) {
        Instrument value = new Instrument();
        value.setId(id);
        value.setCode(code);
        value.setName(name);
        value.setType("STOCK");
        value.setMarket(market);
        return value;
    }
}
