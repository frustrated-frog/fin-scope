package com.finscope.rpc.company;

import com.finscope.domain.company.CompanySearchResult;
import com.finscope.rpc.marketintel.FinanceHttpClient;
import com.finscope.rpc.marketintel.FinanceHttpResponse;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SecCompanyDirectoryProviderTest {
    @Test
    void searchesSecCompaniesByCommonNameAndKeepsIssuerIdentity() {
        AtomicReference<Map<String, String>> requestedHeaders = new AtomicReference<Map<String, String>>();
        FinanceHttpClient http = (provider, uri, headers) -> {
            requestedHeaders.set(headers);
            return new FinanceHttpResponse(
                200,
                "{\"fields\":[\"cik\",\"name\",\"ticker\",\"exchange\"],\"data\":["
                        + "[320193,\"Apple Inc.\",\"AAPL\",\"Nasdaq\"],"
                        + "[1652044,\"Alphabet Inc.\",\"GOOGL\",\"Nasdaq\"],"
                        + "[1652044,\"Alphabet Inc.\",\"GOOG\",\"Nasdaq\"]]}",
                Instant.parse("2026-08-08T00:00:00Z"), "hash");
        };

        List<CompanySearchResult> results = new SecCompanyDirectoryProvider(http).search("google", 10);

        assertEquals(1, results.size());
        assertEquals("Alphabet Inc.", results.get(0).getDisplayName());
        assertEquals("CIK0001652044", results.get(0).getProviderCompanyId());
        assertEquals(2, results.get(0).getSecurities().size());
        assertEquals("GOOGL", results.get(0).getSecurities().get(0).getSymbol());
        assertEquals("US", results.get(0).getCountryCode());
        assertEquals("L2", results.get(0).getCapabilityLevel());
        assertTrue(requestedHeaders.get().containsKey("User-Agent"));
        assertTrue(requestedHeaders.get().get("User-Agent").contains("FinScope"));
    }
}
