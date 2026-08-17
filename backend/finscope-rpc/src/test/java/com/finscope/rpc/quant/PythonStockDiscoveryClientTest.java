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
                .discover(LocalDate.of(2026, 8, 14), 6000d, "stock-discovery-v2");

        assertEquals(900_000, timeout.get());
        assertEquals(true, body.get().contains("\"deepLimit\":15"));
        assertEquals("2026-08-14", report.getAsOfDate());
        assertEquals(2, report.getFinalCount());
        assertEquals("TONGHUASHUN", report.getSourceFamily());
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
                .discover(LocalDate.of(2026, 8, 14), 6000d, "stock-discovery-v2"));
    }

    @Test
    void rejectsMalformedDatesAndNonHexFingerprints() {
        assertContractRejected(payload().replace("2026-08-14", "2026-8-14"));
        assertContractRejected(payload().replace(
                "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
                "zzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzz"));
    }

    @Test
    void rejectsFinalCandidateThatDidNotPassTheDeepGate() {
        assertContractRejected(payload().replace("\"qualified\":true", "\"qualified\":false"));
    }

    @Test
    void rejectsNonTonghuashunRankingAuthority() {
        assertContractRejected(payload().replace(
                "\"source_family\":\"TONGHUASHUN\"",
                "\"source_family\":\"EASTMONEY\""));
    }

    @Test
    void rejectsInconsistentTradingScopeFunnel() {
        assertContractRejected(payload().replace(
                "\"scope_excluded_count\":2",
                "\"scope_excluded_count\":1"));
    }

    private void assertContractRejected(String responseBody) {
        FinanceHttpClient http = new FinanceHttpClient() {
            @Override
            public FinanceHttpResponse get(String providerCode, URI uri, Map<String, String> headers) {
                throw new UnsupportedOperationException();
            }

            @Override
            public FinanceHttpResponse postJson(String providerCode, URI uri, String request,
                                                Map<String, String> headers, int requestTimeoutMs) {
                return new FinanceHttpResponse(200, responseBody, Instant.now(), "hash");
            }
        };
        assertThrows(ProviderContractException.class, () -> new PythonStockDiscoveryClient(
                "http://127.0.0.1:8000", http, 900_000)
                .discover(LocalDate.of(2026, 8, 14), 6000d, "stock-discovery-v2"));
    }

    private String payload() {
        return "{\"schema_version\":\"1.0.0\",\"policy_version\":\"stock-discovery-v2\","
                + "\"as_of_date\":\"2026-08-14\",\"source_code\":\"THS\",\"source_family\":\"TONGHUASHUN\","
                + "\"quality_status\":\"FRESH_PRIMARY\",\"retrieved_at\":\"2026-08-14T15:35:00\","
                + "\"data_fingerprint\":\"aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa\","
                + "\"budget\":6000,\"constituent_source_families\":[\"TONGHUASHUN\"],"
                + "\"constituent_quality_status\":\"COMPLETE\",\"sectors\":[{\"code\":\"881125\","
                + "\"name\":\"机器人\",\"category\":\"INDUSTRY\",\"source_code\":\"THS\","
                + "\"source_family\":\"TONGHUASHUN\",\"period\":\"1D\",\"source_rank\":1,"
                + "\"expected_constituent_count\":4,\"resolved_constituent_count\":4,"
                + "\"constituent_source_family\":\"TONGHUASHUN\","
                + "\"constituent_quality_status\":\"COMPLETE\",\"constituent_coverage\":1.0,"
                + "\"retrieved_at\":\"2026-08-14T15:35:00\"}],"
                + "\"candidates\":[{\"code\":\"000001\"},{\"code\":\"600001\"}],"
                + "\"deep_evidence\":[{\"code\":\"000001\"},{\"code\":\"600001\"}],"
                + "\"final_candidates\":[{\"code\":\"000001\",\"final_rank\":1,"
                + "\"qualified\":true,\"health_status\":\"HEALTHY\",\"conclusion\":\"ROBUST\","
                + "\"calibrated_probability\":0.64,\"probability_lower_bound\":0.55,"
                + "\"evidence\":[\"locked test\"],\"risks\":[],\"forecast_report\":{}},{"
                + "\"code\":\"600001\",\"final_rank\":2,\"qualified\":true,"
                + "\"health_status\":\"HEALTHY\",\"conclusion\":\"CONDITIONALLY_EFFECTIVE\","
                + "\"calibrated_probability\":0.61,\"probability_lower_bound\":0.53,"
                + "\"evidence\":[\"locked test\"],\"risks\":[],\"forecast_report\":{}}],"
                + "\"funnel\":{\"raw_constituent_count\":4,\"scope_excluded_count\":2,"
                + "\"star_market_excluded_count\":1,\"beijing_market_excluded_count\":1,"
                + "\"unsupported_scope_excluded_count\":0,\"constituent_count\":2,\"admitted_count\":2,"
                + "\"quantified_count\":2,\"deep_review_count\":2,\"final_count\":2},"
                + "\"warnings\":[],\"duration_ms\":12000}";
    }
}
