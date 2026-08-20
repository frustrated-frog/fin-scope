package com.finscope.rpc.quant;

import com.finscope.domain.quant.discovery.StockDiscoveryAccuracyReport;
import com.finscope.domain.quant.discovery.StockDiscoveryEvaluationRequest;
import com.finscope.rpc.marketintel.FinanceHttpClient;
import com.finscope.rpc.marketintel.FinanceHttpResponse;
import com.finscope.rpc.marketintel.ProviderContractException;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PythonStockDiscoveryEvaluationClientTest {
    @Test
    void postsFrozenObservationsAndMapsAccuracyReport() {
        AtomicReference<String> body = new AtomicReference<>();
        FinanceHttpClient http = http(body, payload());
        StockDiscoveryEvaluationRequest request = new StockDiscoveryEvaluationRequest();
        request.setPendingCount(3);
        StockDiscoveryEvaluationRequest.OutcomeObservation observation =
                new StockDiscoveryEvaluationRequest.OutcomeObservation();
        observation.setRunId(7L);
        observation.setInstrumentCode("600001.SH");
        observation.setAsOfDate("2026-08-14");
        observation.setHorizonDays(5);
        observation.setAdmitted(true);
        observation.setActualNetReturn(0.03d);
        observation.setActualDirection("UP");
        request.getObservations().add(observation);

        StockDiscoveryAccuracyReport report = new PythonStockDiscoveryEvaluationClient(
                "http://127.0.0.1:8000", http, 30000).evaluate(request);

        assertTrue(body.get().contains("\"instrumentCode\":\"600001.SH\""));
        assertEquals("ACCUMULATING", report.getStatus());
        assertEquals(3, report.getPendingCount());
        assertEquals(1, report.getMaturedCandidateCount());
    }

    @Test
    void rejectsMetricShapeDrift() {
        FinanceHttpClient http = http(new AtomicReference<>(),
                payload().replace("\"window_days\":180", "\"window_days\":365"));
        StockDiscoveryEvaluationRequest request = new StockDiscoveryEvaluationRequest();
        request.setPendingCount(3);
        request.getObservations().add(new StockDiscoveryEvaluationRequest.OutcomeObservation());

        assertThrows(ProviderContractException.class, () -> new PythonStockDiscoveryEvaluationClient(
                "http://127.0.0.1:8000", http, 30000).evaluate(request));
    }

    private FinanceHttpClient http(AtomicReference<String> body, String responseBody) {
        return new FinanceHttpClient() {
            @Override
            public FinanceHttpResponse get(String providerCode, URI uri, Map<String, String> headers) {
                throw new UnsupportedOperationException();
            }

            @Override
            public FinanceHttpResponse postJson(String providerCode, URI uri, String request,
                                                Map<String, String> headers, int requestTimeoutMs) {
                body.set(request);
                return new FinanceHttpResponse(200, responseBody, Instant.now(), "hash");
            }
        };
    }

    private String payload() {
        return "{\"schema_version\":\"stock-discovery-evaluation-v1\","
                + "\"as_of_date\":\"2026-08-20\",\"horizon_days\":5,\"status\":\"ACCUMULATING\","
                + "\"conclusion\":\"继续积累真实结果\",\"matured_run_count\":1,"
                + "\"matured_candidate_count\":1,\"matured_final_count\":0,\"pending_count\":3,"
                + "\"probability_quality\":{\"sample_count\":0,\"brier_score\":null,"
                + "\"brier_skill_score\":null,\"log_loss\":null,\"accuracy\":null,"
                + "\"expected_calibration_error\":null,\"baseline_probability\":null},"
                + "\"reliability_bins\":["
                + bin(0, 0.2) + "," + bin(0.2, 0.4) + "," + bin(0.4, 0.6) + ","
                + bin(0.6, 0.8) + "," + bin(0.8, 1.0) + "],"
                + "\"selection_metrics\":[" + selection(1) + "," + selection(3) + "," + selection(5) + "],"
                + "\"windows\":[" + window(30) + "," + window(90) + "," + window(180) + "],"
                + "\"sector_performance\":[],\"model_race\":{\"status\":\"EVIDENCE_ACCUMULATING\","
                + "\"sample_count\":0,\"minimum_promotion_samples\":30,\"champion_code\":null,"
                + "\"promotion_candidate_code\":null,\"conclusion\":\"尚无真实结果\",\"candidates\":[]},"
                + "\"recent_outcomes\":[],\"warnings\":[]}";
    }

    private String bin(double lower, double upper) {
        return "{\"lower_bound\":" + lower + ",\"upper_bound\":" + upper
                + ",\"count\":0,\"mean_probability\":null,\"observed_up_rate\":null,"
                + "\"calibration_error\":null}";
    }

    private String selection(int limit) {
        return "{\"limit\":" + limit + ",\"matured_run_count\":0,\"sample_count\":0,"
                + "\"hit_rate\":null,\"average_net_return\":null,\"median_net_return\":null,"
                + "\"admitted_pool_average_return\":null,\"average_excess_vs_admitted_pool\":null}";
    }

    private String window(int days) {
        return "{\"window_days\":" + days + ",\"start_date\":\"2026-01-01\","
                + "\"matured_run_count\":0,\"probability_sample_count\":0,\"final_count\":0,"
                + "\"final_hit_rate\":null,\"final_average_net_return\":null,"
                + "\"brier_skill_score\":null}";
    }
}
