package com.finscope.rpc.quant;

import com.finscope.domain.strategy.holding.HoldingStrategyAdvice;
import com.finscope.domain.strategy.holding.HoldingStrategyEvaluationRequest;
import com.finscope.domain.strategy.holding.HoldingStrategySettlementRequest;
import com.finscope.rpc.marketintel.FinanceHttpClient;
import com.finscope.rpc.marketintel.FinanceHttpResponse;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PythonHoldingStrategyClientTest {
    @Test
    void postsHoldingContextToIndependentPolicyEndpoint() {
        AtomicReference<String> body = new AtomicReference<String>();
        FinanceHttpClient http = new FinanceHttpClient() {
            @Override
            public FinanceHttpResponse get(String providerCode, URI uri, Map<String, String> headers) {
                throw new UnsupportedOperationException();
            }

            @Override
            public FinanceHttpResponse postJson(String providerCode, URI uri, String request,
                                                Map<String, String> headers, int requestTimeoutMs) {
                body.set(request);
                return new FinanceHttpResponse(200, "{\"action\":\"HOLD\",\"suggestedQuantity\":0,"
                        + "\"expectedEdgeAfterCost\":0.012,\"p10RiskAmount\":-180,"
                        + "\"p90UpsideAmount\":360,\"currentMarketValue\":3000,"
                        + "\"projectedWeight\":0.5,\"evidence\":[\"概率门禁通过\"],"
                        + "\"blockers\":[],\"explanation\":\"保持持仓\","
                        + "\"benchmark\":\"同一只股票保持当时持仓不动\","
                        + "\"policyVersion\":\"holding-policy-v1\"}", Instant.now(), "hash");
            }
        };
        HoldingStrategyEvaluationRequest request = request();

        HoldingStrategyAdvice advice = new PythonHoldingStrategyClient(
                "http://127.0.0.1:8000", http, 30000).evaluate(request);

        assertEquals("HOLD", advice.getAction());
        assertEquals("holding-policy-v1", advice.getPolicyVersion());
        assertTrue(body.get().contains("\"costBasis\":25.0"));
        assertFalse(body.get().contains("averageCost"));
    }

    @Test
    void settlesFrozenActionAgainstSameStockHold() {
        FinanceHttpClient http = new FinanceHttpClient() {
            @Override
            public FinanceHttpResponse get(String providerCode, URI uri, Map<String, String> headers) {
                throw new UnsupportedOperationException();
            }

            @Override
            public FinanceHttpResponse postJson(String providerCode, URI uri, String request,
                                                Map<String, String> headers, int requestTimeoutMs) {
                assertTrue(uri.toString().endsWith("/v1/quant/holding-strategies/settle"));
                return new FinanceHttpResponse(200, "{\"strategyReturn\":0.16,\"holdReturn\":0.08,"
                        + "\"incrementalReturn\":0.08,\"method\":\"frozen-action-v1\"}",
                        Instant.now(), "hash");
            }
        };
        HoldingStrategySettlementRequest request = new HoldingStrategySettlementRequest();
        request.setAction("ALLOW_ADD");
        request.setSuggestedQuantity(100);
        request.setHeldQuantity(100);
        request.setCurrentMarketValue(3000d);
        request.setEntryPrice(30d);
        request.setActualNetReturn(0.08d);

        assertEquals(0.08d, new PythonHoldingStrategyClient(
                "http://127.0.0.1:8000", http, 30000).settle(request).getIncrementalReturn());
    }

    private HoldingStrategyEvaluationRequest request() {
        HoldingStrategyEvaluationRequest value = new HoldingStrategyEvaluationRequest();
        value.setInstrumentCode("600570.SH");
        value.setAsOfDate(LocalDate.of(2026, 8, 31));
        value.setHorizonDays(5);
        value.setMarketPrice(30d);
        value.setQuantity(100d);
        value.setCash(3000d);
        value.setTotalEquity(6000d);
        value.setCurrentWeight(0.5d);
        value.setUpProbability(0.61d);
        value.setP10Return(-0.06d);
        value.setP50Return(0.015d);
        value.setP90Return(0.12d);
        value.setForecastStatus("CONDITIONAL");
        value.setModelHealthStatus("HEALTHY");
        value.setQuoteAgeDays(0);
        value.setRoundTripCostRate(0.0015d);
        value.setForecastRunId(12L);
        value.setModelVersion("panel-logit-v10");
        value.setDataFingerprint("sha256:abc");
        value.setCostBasis(25d);
        value.setUnrealizedReturn(0.2d);
        return value;
    }
}
