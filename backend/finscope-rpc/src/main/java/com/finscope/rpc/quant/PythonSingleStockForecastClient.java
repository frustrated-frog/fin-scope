package com.finscope.rpc.quant;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.finscope.domain.quant.forecast.SingleStockForecast;
import com.finscope.rpc.marketintel.FinanceHttpClient;
import com.finscope.rpc.marketintel.FinanceHttpResponse;
import com.finscope.rpc.marketintel.ProviderContractException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/** Thin contract client for the Python-owned single-stock forecast engine. */
@Component
public class PythonSingleStockForecastClient {
    private static final String CLIENT_CODE = "PYTHON_SINGLE_STOCK_FORECAST";
    private static final Set<String> STATUSES = new HashSet<String>(Arrays.asList(
            "INSUFFICIENT_DATA", "ROBUST", "CONDITIONAL", "NO_CLEAR_EDGE"));

    private final String baseUrl;
    private final FinanceHttpClient http;
    private final ObjectMapper json = new ObjectMapper().registerModule(new JavaTimeModule());

    @Autowired
    public PythonSingleStockForecastClient(
            @Value("${finscope.python-market-data.base-url:http://127.0.0.1:8000}") String baseUrl,
            FinanceHttpClient http) {
        this.baseUrl = trimTrailingSlash(baseUrl);
        this.http = http;
    }

    public SingleStockForecast forecast(String code) {
        if (code == null || !code.matches("\\d{6}")) {
            throw contract("INVALID_INSTRUMENT", "股票代码必须是六位 A 股代码", false);
        }
        URI uri = URI.create(baseUrl + "/v1/quant/single-stock-forecasts");
        try {
            FinanceHttpResponse response = http.postJson(
                    CLIENT_CODE, uri, "{\"code\":\"" + code + "\"}",
                    Collections.<String, String>emptyMap());
            SingleStockForecast result = json.readValue(response.getBody(), SingleStockForecast.class);
            validate(result, code);
            return result;
        } catch (ProviderContractException error) {
            throw error;
        } catch (Exception error) {
            throw contract("SCHEMA_DRIFT", "Python 单股预测响应不符合契约", false, error);
        }
    }

    private void validate(SingleStockForecast result, String code) {
        String expected = code + "." + market(code);
        if (result == null || !expected.equals(result.getInstrumentCode())
                || result.getAsOfDate() == null || result.getHorizonDays() != 20
                || !STATUSES.contains(result.getStatus()) || result.getConclusion() == null
                || result.getBarCount() == null || result.getBarCount() < 0
                || result.getDataFingerprint() == null || result.getDataFingerprint().trim().isEmpty()
                || result.getReportSchemaVersion() == null || result.getModelVersion() == null
                || result.getStrategyPolicy() == null || result.getLastClose() == null) {
            throw contract("SCHEMA_DRIFT", "Python 单股预测缺少必需字段", false);
        }
        probability(result.getUpProbability());
        if (result.getValidation() != null) {
            probability(result.getValidation().getAccuracy());
            probability(result.getValidation().getBrierScore());
            probability(result.getValidation().getBaselineBrierScore());
            probability(result.getValidation().getObservedUpRate());
        }
        if (result.getRecentObservations() != null) {
            for (SingleStockForecast.Observation observation : result.getRecentObservations()) {
                if (observation == null || observation.getSignalDate() == null) {
                    throw contract("SCHEMA_DRIFT", "Python 单股预测观测记录不完整", false);
                }
                probability(observation.getProbability());
            }
        }
    }

    private void probability(Double value) {
        if (value != null && (value.isNaN() || value.isInfinite() || value < 0d || value > 1d)) {
            throw contract("SCHEMA_DRIFT", "Python 单股预测概率超出范围", false);
        }
    }

    private String market(String code) {
        if (code.startsWith("6") || code.startsWith("5") || code.startsWith("9")) return "SH";
        if (code.startsWith("4") || code.startsWith("8")) return "BJ";
        return "SZ";
    }

    private static String trimTrailingSlash(String value) {
        String normalized = value == null ? "" : value.trim();
        while (normalized.endsWith("/")) normalized = normalized.substring(0, normalized.length() - 1);
        return normalized;
    }

    private static ProviderContractException contract(String type, String message, boolean retryable) {
        return new ProviderContractException(type, message, retryable);
    }

    private static ProviderContractException contract(
            String type, String message, boolean retryable, Throwable cause) {
        return new ProviderContractException(type, message, retryable, cause);
    }
}
