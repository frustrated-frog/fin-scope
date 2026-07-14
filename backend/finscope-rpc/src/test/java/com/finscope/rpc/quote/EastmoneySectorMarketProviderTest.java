package com.finscope.rpc.quote;

import com.finscope.domain.instrument.SectorCategory;
import com.finscope.domain.instrument.SectorMarketEntry;
import com.finscope.domain.instrument.SectorMarketSnapshot;
import com.finscope.rpc.marketintel.FinanceHttpClient;
import com.finscope.rpc.marketintel.FinanceHttpResponse;
import com.finscope.rpc.marketintel.ProviderContractException;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EastmoneySectorMarketProviderTest {

    @Test
    void parsesSectorCatalogAndDropsMalformedRows() throws Exception {
        RecordingFixtureHttpClient http = new RecordingFixtureHttpClient();
        EastmoneySectorMarketProvider provider = new EastmoneySectorMarketProvider(http);

        SectorMarketSnapshot snapshot = provider.fetch(SectorCategory.INDUSTRY);

        assertEquals("EASTMONEY", snapshot.getProviderCode());
        assertEquals(SectorCategory.INDUSTRY, snapshot.getCategory());
        assertEquals("fixture-hash", snapshot.getPayloadFingerprint());
        assertEquals(2, snapshot.getEntries().size());
        SectorMarketEntry first = snapshot.getEntries().get(0);
        assertEquals("BK1036", first.getCode());
        assertEquals("半导体", first.getName());
        assertEquals(1532.18, first.getPrice());
        assertEquals(61.25, first.getChangeAmount());
        assertEquals(4.21, first.getChangePct());
        assertEquals(81230000000.0, first.getTurnover());
        assertEquals("688981", first.getLeaderStockCode());
        assertEquals("中芯国际", first.getLeaderStockName());
        assertEquals(12.36, first.getLeaderStockChangePct());
        assertEquals(1, snapshot.getWarnings().size());
        assertTrue(snapshot.getWarnings().get(0).contains("invalid sector code"));
        assertTrue(http.request.getRawQuery().contains("fs=m%3A90%2Bt%3A2%2Bf%3A%2150"));
        assertTrue(http.request.getRawQuery().contains("f6"));
    }

    @Test
    void rejectsSchemaWithoutDiffArray() {
        FinanceHttpClient malformed = (provider, uri, headers) ->
                new FinanceHttpResponse(200, "{\"data\":{}}", Instant.EPOCH, "hash");

        ProviderContractException error = assertThrows(ProviderContractException.class,
                () -> new EastmoneySectorMarketProvider(malformed).fetch(SectorCategory.CONCEPT));

        assertEquals("SCHEMA_DRIFT", error.getErrorType());
    }

    @Test
    void rejectsUnsupportedNullCategory() {
        EastmoneySectorMarketProvider provider = new EastmoneySectorMarketProvider(new RecordingFixtureHttpClient());

        ProviderContractException error = assertThrows(ProviderContractException.class,
                () -> provider.fetch(null));

        assertEquals("UNSUPPORTED_SECTOR_CATEGORY", error.getErrorType());
    }

    private static class RecordingFixtureHttpClient implements FinanceHttpClient {
        private URI request;

        @Override
        public FinanceHttpResponse get(String provider, URI uri, Map<String, String> headers) throws Exception {
            request = uri;
            byte[] bytes = Files.readAllBytes(Paths.get(EastmoneySectorMarketProviderTest.class.getClassLoader()
                    .getResource("quote/eastmoney-sector-industry.json").toURI()));
            return new FinanceHttpResponse(200, new String(bytes, StandardCharsets.UTF_8), Instant.EPOCH, "fixture-hash");
        }
    }
}
