package com.finscope.rpc.quant;

import com.finscope.domain.strategy.holding.StockHoldingAnalysis;
import com.finscope.domain.strategy.holding.StockHoldingAnalysisRequest;
import com.finscope.rpc.marketintel.FinanceHttpClient;
import com.finscope.rpc.marketintel.FinanceHttpResponse;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PythonHoldingAnalysisClientTest {
    @Test
    void mapsHoldingPathAnalysisFromPython() {
        AtomicReference<String> requestBody = new AtomicReference<String>();
        FinanceHttpClient http = new FinanceHttpClient() {
            @Override
            public FinanceHttpResponse get(String providerCode, URI uri, Map<String, String> headers) {
                throw new UnsupportedOperationException();
            }

            @Override
            public FinanceHttpResponse postJson(String providerCode, URI uri, String request,
                                                Map<String, String> headers, int requestTimeoutMs) {
                assertTrue(uri.toString().endsWith("/v1/quant/holding-analyses"));
                requestBody.set(request);
                return new FinanceHttpResponse(200, "{\"instrumentCode\":\"603618.SH\","
                        + "\"entryDate\":\"2026-07-15\",\"firstObservedDate\":\"2026-07-15\","
                        + "\"asOfDate\":\"2026-09-03\",\"holdingCalendarDays\":50,"
                        + "\"observedTradingDays\":36,\"costBasis\":32.49,\"latestPrice\":39.25,"
                        + "\"quantity\":100,\"totalCost\":3249,\"marketValue\":3925,"
                        + "\"unrealizedProfit\":676,\"holdingReturn\":0.20806402,"
                        + "\"maximumFavorableExcursion\":0.25,\"maximumAdverseExcursion\":-0.08,"
                        + "\"maximumDrawdown\":-0.11,\"maximumDrawdownDays\":8,"
                        + "\"annualizedVolatility\":0.31,\"qualityStatus\":\"COMPLETE\","
                        + "\"sourceCode\":\"CACHE\",\"method\":\"QFQ_NORMALIZED_TO_RAW_QUOTE_V1\","
                        + "\"warnings\":[],\"series\":[{\"tradeDate\":\"2026-07-15\","
                        + "\"close\":32.49,\"returnSinceEntry\":0,\"drawdown\":0}]}",
                        Instant.now(), "hash");
            }
        };
        StockHoldingAnalysisRequest request = new StockHoldingAnalysisRequest();
        request.setInstrumentCode("603618.SH");
        request.setEntryDate(LocalDate.of(2026, 7, 15));
        request.setCostBasis(32.49d);
        request.setQuantity(100d);
        request.setMarketPrice(39.25d);

        StockHoldingAnalysis result = new PythonHoldingAnalysisClient(
                "http://127.0.0.1:8000", http, 30000).analyze(request);

        assertEquals(0.20806402d, result.getHoldingReturn());
        assertEquals(1, result.getSeries().size());
        assertTrue(requestBody.get().contains("\"entryDate\":\"2026-07-15\""));
    }
}
