package com.finscope.rpc.quote;

import com.finscope.domain.instrument.SectorCategory;
import com.finscope.domain.instrument.SectorMarketEntry;
import com.finscope.domain.instrument.SectorMarketSnapshot;
import com.finscope.rpc.marketintel.FinanceHttpClient;
import com.finscope.rpc.marketintel.FinanceHttpResponse;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.time.Instant;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SinaSectorMarketProviderTest {

    @Test
    void parsesIndustryCatalogFromIndependentSinaEndpoint() {
        RecordingHttp http = new RecordingHttp();
        SinaSectorMarketProvider provider = new SinaSectorMarketProvider(http);

        SectorMarketSnapshot snapshot = provider.fetch(SectorCategory.INDUSTRY);

        assertEquals("SINA_SECTOR_CATALOG", snapshot.getProviderCode());
        assertEquals(1, snapshot.getEntries().size());
        SectorMarketEntry entry = snapshot.getEntries().get(0);
        assertEquals("SINA:new_blhy", entry.getCode());
        assertEquals("半导体", entry.getName());
        assertEquals(1532.18, entry.getPrice());
        assertEquals(4.21, entry.getChangePct());
        assertEquals(81230000000.0, entry.getTurnover());
        assertEquals("688981", entry.getLeaderStockCode());
        assertEquals("中芯国际", entry.getLeaderStockName());
        assertTrue(http.uri.toString().contains("newSinaHy.php"));
    }

    @Test
    void usesClassEndpointForConceptCatalog() {
        RecordingHttp http = new RecordingHttp();

        new SinaSectorMarketProvider(http).fetch(SectorCategory.CONCEPT);

        assertTrue(http.uri.toString().contains("newFLJK.php?param=class"));
    }

    private static final class RecordingHttp implements FinanceHttpClient {
        private URI uri;

        @Override
        public FinanceHttpResponse get(String providerCode, URI uri, Map<String, String> headers) {
            this.uri = uri;
            String body = "var S_Finance_bankuai_sinaindustry = {\"new_blhy\":"
                    + "\"new_blhy,半导体,152,1532.18,61.25,4.21,123456,81230000000,"
                    + "688981,12.36,88.20,9.70,中芯国际\"};";
            return new FinanceHttpResponse(200, body, Instant.parse("2026-07-28T07:00:00Z"), "sina-hash");
        }
    }
}
