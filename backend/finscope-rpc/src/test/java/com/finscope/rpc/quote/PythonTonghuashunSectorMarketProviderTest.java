package com.finscope.rpc.quote;

import com.finscope.domain.instrument.SectorCategory;
import com.finscope.domain.instrument.SectorMarketEntry;
import com.finscope.domain.instrument.SectorMarketSnapshot;
import com.finscope.rpc.marketintel.FinanceHttpClient;
import com.finscope.rpc.marketintel.FinanceHttpResponse;
import com.finscope.rpc.marketintel.ProviderContractException;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.net.URI;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PythonTonghuashunSectorMarketProviderTest {

    @Test
    void mapsTonghuashunIndustryRankingContract() throws Exception {
        AtomicReference<URI> requested = new AtomicReference<URI>();
        FinanceHttpClient http = (providerCode, uri, headers) -> {
            requested.set(uri);
            return new FinanceHttpResponse(200, validPayload(),
                    Instant.parse("2026-08-19T01:45:00Z"), "payload-hash");
        };
        PythonTonghuashunSectorMarketProvider provider = provider(http);

        SectorMarketSnapshot snapshot = provider.fetch(SectorCategory.INDUSTRY);

        assertEquals("/v1/sectors/INDUSTRY", requested.get().getPath());
        assertEquals("PYTHON_TONGHUASHUN_SECTOR", snapshot.getProviderCode());
        assertEquals(1, snapshot.getEntries().size());
        SectorMarketEntry entry = snapshot.getEntries().get(0);
        assertEquals("881121", entry.getCode());
        assertEquals(1, entry.getSourceRank());
        assertEquals(1_200_000_000D, entry.getMainNetInflow());
        assertEquals(2.4D, entry.getChangePct());
        assertEquals("中芯国际", entry.getLeaderStockName());
        assertEquals(48, entry.getAdvanceCount());
        assertEquals(12, entry.getDeclineCount());
        assertEquals(0, entry.getFlatCount());
        assertEquals(0.8D, entry.getBreadthRatio());
    }

    @Test
    void rejectsResponseFromAnyNonTonghuashunFamily() throws Exception {
        FinanceHttpClient http = (providerCode, uri, headers) -> new FinanceHttpResponse(200,
                validPayload().replace("TONGHUASHUN", "EASTMONEY"), Instant.now(), "hash");

        ProviderContractException error = assertThrows(ProviderContractException.class,
                () -> provider(http).fetch(SectorCategory.INDUSTRY));

        assertEquals("SECTOR_SOURCE_DRIFT", error.getErrorType());
    }

    @Test
    void rejectsCategoryMismatch() throws Exception {
        FinanceHttpClient http = (providerCode, uri, headers) -> new FinanceHttpResponse(200,
                validPayload().replace("\"category\":\"INDUSTRY\"", "\"category\":\"CONCEPT\""),
                Instant.now(), "hash");

        ProviderContractException error = assertThrows(ProviderContractException.class,
                () -> provider(http).fetch(SectorCategory.INDUSTRY));

        assertEquals("SECTOR_CATEGORY_DRIFT", error.getErrorType());
    }

    private PythonTonghuashunSectorMarketProvider provider(FinanceHttpClient http) {
        PythonTonghuashunSectorMarketProvider provider = new PythonTonghuashunSectorMarketProvider();
        ReflectionTestUtils.setField(provider, "baseUrl", "http://python-market-data:8000");
        ReflectionTestUtils.setField(provider, "http", http);
        return provider;
    }

    private String validPayload() {
        return "{\"schema_version\":\"sector-market-v1\","
                + "\"source_code\":\"AKSHARE_TONGHUASHUN_SECTOR\","
                + "\"source_family\":\"TONGHUASHUN\",\"category\":\"INDUSTRY\","
                + "\"retrieved_at\":\"2026-08-19T09:45:00\",\"entries\":[{"
                + "\"code\":\"881121\",\"name\":\"半导体\",\"category\":\"INDUSTRY\","
                + "\"source_rank\":1,\"change_pct\":2.4,\"main_net_inflow\":1200000000,"
                + "\"leader_stock_name\":\"中芯国际\",\"advance_count\":48,"
                + "\"decline_count\":12,\"flat_count\":0,\"breadth_ratio\":0.8}],\"warnings\":[]}";
    }
}
