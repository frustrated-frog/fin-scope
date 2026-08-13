package com.finscope.rpc.quant;

import com.finscope.domain.quant.discovery.StockDiscoveryReport;
import com.finscope.rpc.marketintel.FinanceHttpClient;
import com.finscope.rpc.marketintel.FinanceHttpResponse;
import com.finscope.rpc.marketintel.ProviderContractException;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PythonStockDiscoveryClientTest {
    @Test
    void postsBoundedAutomaticDiscoveryRequestAndMapsSummary() {
        AtomicReference<String> body = new AtomicReference<String>();
        AtomicInteger timeout = new AtomicInteger();
        FinanceHttpClient http = new FinanceHttpClient() {
            @Override
            public FinanceHttpResponse get(String providerCode, URI uri, Map<String, String> headers) {
                throw new UnsupportedOperationException();
            }

            @Override
            public FinanceHttpResponse postJson(String providerCode, URI uri, String request,
                                                Map<String, String> headers, int requestTimeoutMs) {
                body.set(request);
                timeout.set(requestTimeoutMs);
                return new FinanceHttpResponse(200, payload(), Instant.now(), "hash");
            }
        };

        StockDiscoveryReport report = new PythonStockDiscoveryClient(
                "http://127.0.0.1:8000", http, 900_000)
                .discover(LocalDate.of(2026, 8, 14), 6000d, "stock-discovery-v1");

        assertEquals(900_000, timeout.get());
        assertEquals(true, body.get().contains("\"deepLimit\":15"));
        assertEquals("2026-08-14", report.getAsOfDate());
        assertEquals(2, report.getFinalCount());
        assertEquals("EASTMONEY", report.getSourceFamily());
    }

    @Test
    void rejectsMismatchedFinalCount() {
        FinanceHttpClient http = new FinanceHttpClient() {
            @Override
            public FinanceHttpResponse get(String providerCode, URI uri, Map<String, String> headers) {
                throw new UnsupportedOperationException();
            }

            @Override
            public FinanceHttpResponse postJson(String providerCode, URI uri, String request,
                                                Map<String, String> headers, int requestTimeoutMs) {
                return new FinanceHttpResponse(200,
                        payload().replace("\"final_count\":2", "\"final_count\":5"),
                        Instant.now(), "hash");
            }
        };

        assertThrows(ProviderContractException.class, () -> new PythonStockDiscoveryClient(
                "http://127.0.0.1:8000", http, 900_000)
                .discover(LocalDate.of(2026, 8, 14), 6000d, "stock-discovery-v1"));
    }

    private String payload() {
        return "{\"schema_version\":\"1.0.0\",\"policy_version\":\"stock-discovery-v1\","
                + "\"as_of_date\":\"2026-08-14\",\"source_code\":\"EM\",\"source_family\":\"EASTMONEY\","
                + "\"quality_status\":\"FRESH_PRIMARY\",\"retrieved_at\":\"2026-08-14T15:35:00\","
                + "\"data_fingerprint\":\"aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa\","
                + "\"budget\":6000,\"sectors\":[{\"code\":\"BK1\",\"name\":\"机器人\","
                + "\"category\":\"CONCEPT\",\"source_code\":\"EM\",\"source_family\":\"EASTMONEY\","
                + "\"period\":\"5D\",\"source_rank\":1,\"retrieved_at\":\"2026-08-14T15:35:00\"}],"
                + "\"candidates\":[],\"deep_evidence\":[],\"final_candidates\":[{\"code\":\"000001\"},"
                + "{\"code\":\"600001\"}],\"funnel\":{\"constituent_count\":88,\"admitted_count\":20,"
                + "\"quantified_count\":20,\"deep_review_count\":15,\"final_count\":2},"
                + "\"warnings\":[],\"duration_ms\":12000}";
    }
}
