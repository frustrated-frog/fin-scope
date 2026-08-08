package com.finscope.rpc.quant;

import com.finscope.domain.quant.forecast.SingleStockForecast;
import com.finscope.rpc.marketintel.FinanceHttpClient;
import com.finscope.rpc.marketintel.FinanceHttpResponse;
import com.finscope.rpc.marketintel.ProviderContractException;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PythonSingleStockForecastClientTest {
    @Test
    void postsCodeAndMapsTheCompleteForecastContract() {
        AtomicReference<URI> uri = new AtomicReference<URI>();
        AtomicReference<String> body = new AtomicReference<String>();
        FinanceHttpClient http = new FinanceHttpClient() {
            @Override
            public FinanceHttpResponse get(String providerCode, URI value, Map<String, String> headers) {
                throw new UnsupportedOperationException();
            }

            @Override
            public FinanceHttpResponse postJson(String providerCode, URI value, String valueBody,
                                                Map<String, String> headers) {
                uri.set(value); body.set(valueBody);
                return response(payload(0.63));
            }
        };

        SingleStockForecast result = new PythonSingleStockForecastClient(
                "http://127.0.0.1:8000/", http).forecast("600519");

        assertEquals("/v1/quant/single-stock-forecasts", uri.get().getPath());
        assertEquals("{\"code\":\"600519\"}", body.get());
        assertEquals("600519.SH", result.getInstrumentCode());
        assertEquals(LocalDate.of(2026, 8, 7), result.getAsOfDate());
        assertEquals(0.63d, result.getUpProbability(), 0.000000001d);
        assertEquals(1, result.getRecentObservations().size());
        assertEquals(32, result.getValidation().getIndependentSampleCount());
    }

    @Test
    void rejectsProbabilityOutsideTheContract() {
        PythonSingleStockForecastClient client = new PythonSingleStockForecastClient(
                "http://127.0.0.1:8000",
                new FinanceHttpClient() {
                    @Override
                    public FinanceHttpResponse get(String providerCode, URI uri,
                                                   Map<String, String> headers) {
                        throw new UnsupportedOperationException();
                    }

                    @Override
                    public FinanceHttpResponse postJson(String providerCode, URI uri, String body,
                                                        Map<String, String> headers) {
                        return response(payload(1.2));
                    }
                });

        assertEquals("SCHEMA_DRIFT", assertThrows(
                ProviderContractException.class,
                () -> client.forecast("600519")).getErrorType());
    }

    private static FinanceHttpResponse response(String body) {
        return new FinanceHttpResponse(200, body, Instant.parse("2026-08-07T07:00:00Z"), "hash");
    }

    private static String payload(double probability) {
        return "{"
                + "\"instrumentCode\":\"600519.SH\",\"asOfDate\":\"2026-08-07\","
                + "\"reportSchemaVersion\":\"single-stock-research-v2\","
                + "\"modelVersion\":\"logistic-walk-forward-v2\","
                + "\"horizonDays\":20,\"status\":\"ROBUST\","
                + "\"conclusion\":\"样本外存在增量\",\"barCount\":1600,"
                + "\"labeledSampleCount\":1519,\"upProbability\":" + probability + ","
                + "\"expectedNetReturn\":0.03,\"lowerNetReturn\":-0.08,"
                + "\"upperNetReturn\":0.12,\"dataFingerprint\":\"abcdef\","
                + "\"sourceCode\":\"PYTDX\",\"sourceFamily\":\"TDX\","
                + "\"qualityStatus\":\"FRESH_FALLBACK\","
                + "\"lastClose\":1505.0,\"strategyPolicy\":{"
                + "\"signalThreshold\":0.6,\"holdingDays\":20,"
                + "\"entryRule\":\"T+1 开盘\",\"exitRule\":\"T+20 收盘\","
                + "\"overlapPolicy\":\"不重叠\",\"roundTripCostRate\":0.0015,"
                + "\"benchmark\":\"同股买入并持有\"},"
                + "\"validation\":{\"outOfSampleCount\":620,\"independentSampleCount\":32,"
                + "\"accuracy\":0.58,\"brierScore\":0.22,"
                + "\"baselineBrierScore\":0.24,\"observedUpRate\":0.54},"
                + "\"recentObservations\":[{\"signalDate\":\"2026-07-01\","
                + "\"probability\":0.61,\"actualNetReturn\":0.04,\"correct\":true}],"
                + "\"warnings\":[\"前复权模拟\"]}"
                ;
    }
}
