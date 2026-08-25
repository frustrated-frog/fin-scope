package com.finscope.rpc.quote;

import com.finscope.domain.instrument.FundHoldingDisclosure;
import com.finscope.rpc.marketintel.FinanceHttpClient;
import com.finscope.rpc.marketintel.FinanceHttpResponse;
import com.finscope.rpc.marketintel.ProviderContractException;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.net.URI;
import java.time.Instant;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FuyaoFundHoldingProviderTest {

    @Test
    void mapsStructuredStockHoldingsAndConvertsRawUnits() {
        RecordingClient client = new RecordingClient("{\"code\":0,\"message\":\"success\","
                + "\"data\":{\"timestamp\":1787673600000,\"item\":["
                + "{\"thscode\":\"600519.SH\",\"ticker\":\"600519\","
                + "\"stock_name\":\"贵州茅台\",\"hold_ratio\":4.67,"
                + "\"asset_type\":\"stock\",\"position_capital\":123456789.12,"
                + "\"position_count\":1234567,\"investment_rank\":1,"
                + "\"end_date_ms\":1785513600000},"
                + "{\"thscode\":\"019547.SH\",\"ticker\":\"019547\","
                + "\"stock_name\":\"示例国债\",\"hold_ratio\":2.35,"
                + "\"asset_type\":\"bond\",\"position_capital\":62500000,"
                + "\"position_count\":100000,\"investment_rank\":2,"
                + "\"end_date_ms\":1785513600000}]}}");
        FuyaoFundHoldingProvider provider = provider(client, "test-key");

        FundHoldingDisclosure result = provider.fetch("510300");

        assertEquals("510300", result.getFundCode());
        assertEquals(1, result.getHoldings().size());
        assertEquals("600519", result.getHoldings().get(0).getStockCode());
        assertEquals(4.67d, result.getHoldings().get(0).getWeightPct());
        assertEquals(123.4567d, result.getHoldings().get(0).getSharesTenThousand(), 0.0000001d);
        assertEquals(12345.678912d,
                result.getHoldings().get(0).getMarketValueTenThousand(), 0.0000001d);
        assertEquals("https", client.uri.getScheme());
        assertEquals("/api/fund/portfolio/holdings", client.uri.getPath());
        assertTrue(client.uri.getQuery().contains("fund_type=exchange"));
        assertTrue(client.uri.getQuery().contains("thscode=510300.SH"));
        assertEquals("test-key", client.headers.get("X-api-key"));
    }

    @Test
    void mapsBusinessFailureWithoutLeakingApiKey() {
        FuyaoFundHoldingProvider provider = provider(
                new RecordingClient("{\"code\":4001,\"message\":\"Frequency limit exceeded\",\"data\":null}"),
                "secret-test-key");

        ProviderContractException error = assertThrows(
                ProviderContractException.class,
                () -> provider.fetch("000001"));

        assertEquals("FUYAO_RATE_LIMITED", error.getErrorType());
        assertTrue(error.isRetryable());
        assertTrue(!error.getMessage().contains("secret-test-key"));
    }

    @Test
    void rejectsCallsWhenApiKeyIsNotConfigured() {
        FuyaoFundHoldingProvider provider = provider(new RecordingClient("{}"), "");

        ProviderContractException error = assertThrows(
                ProviderContractException.class,
                () -> provider.fetch("000001"));

        assertEquals("FUYAO_NOT_CONFIGURED", error.getErrorType());
        assertTrue(!provider.isConfigured());
    }

    private FuyaoFundHoldingProvider provider(FinanceHttpClient client, String apiKey) {
        FuyaoFundHoldingProvider provider = new FuyaoFundHoldingProvider();
        ReflectionTestUtils.setField(provider, "http", client);
        ReflectionTestUtils.setField(provider, "baseUrl", "https://fuyao.aicubes.cn");
        ReflectionTestUtils.setField(provider, "apiKey", apiKey);
        return provider;
    }

    private static final class RecordingClient implements FinanceHttpClient {
        private final String body;
        private URI uri;
        private Map<String, String> headers;

        private RecordingClient(String body) {
            this.body = body;
        }

        @Override
        public FinanceHttpResponse get(String providerCode, URI uri,
                                       Map<String, String> headers) {
            this.uri = uri;
            this.headers = headers;
            return new FinanceHttpResponse(200, body, Instant.parse("2026-08-26T02:00:00Z"), "hash");
        }
    }
}
