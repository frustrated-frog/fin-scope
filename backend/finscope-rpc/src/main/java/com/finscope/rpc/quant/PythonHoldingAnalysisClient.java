package com.finscope.rpc.quant;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.finscope.domain.strategy.holding.StockHoldingAnalysis;
import com.finscope.domain.strategy.holding.StockHoldingAnalysisRequest;
import com.finscope.rpc.marketintel.FinanceHttpClient;
import com.finscope.rpc.marketintel.FinanceHttpResponse;
import com.finscope.rpc.marketintel.ProviderContractException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.net.URI;
import java.util.Collections;

@Component
public class PythonHoldingAnalysisClient {
    private static final String CLIENT_CODE = "PYTHON_HOLDING_ANALYSIS";

    @Value("${finscope.python-market-data.base-url:http://127.0.0.1:8000}")
    private String baseUrl;
    @Resource
    private FinanceHttpClient http;
    @Value("${finscope.python-market-data.evaluation-timeout-ms:30000}")
    private int timeoutMs;
    private final ObjectMapper json = new ObjectMapper().registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    public PythonHoldingAnalysisClient() {
    }

    PythonHoldingAnalysisClient(String baseUrl, FinanceHttpClient http, int timeoutMs) {
        this.baseUrl = baseUrl;
        this.http = http;
        this.timeoutMs = timeoutMs;
    }

    public StockHoldingAnalysis analyze(StockHoldingAnalysisRequest request) {
        validateRequest(request);
        try {
            FinanceHttpResponse response = http.postJson(CLIENT_CODE,
                    URI.create(trim(baseUrl) + "/v1/quant/holding-analyses"),
                    json.writeValueAsString(request), Collections.<String, String>emptyMap(), timeoutMs);
            StockHoldingAnalysis result = json.readValue(response.getBody(), StockHoldingAnalysis.class);
            validateResult(result);
            return result;
        } catch (ProviderContractException error) {
            throw error;
        } catch (Exception error) {
            throw contract("SCHEMA_DRIFT", "Python 持仓分析响应不符合契约", false, error);
        }
    }

    private void validateRequest(StockHoldingAnalysisRequest request) {
        if (request == null || request.getInstrumentCode() == null
                || request.getEntryDate() == null || request.getCostBasis() <= 0
                || request.getQuantity() <= 0 || request.getMarketPrice() <= 0) {
            throw contract("INVALID_REQUEST", "持仓分析请求缺少必要字段", false, null);
        }
    }

    private void validateResult(StockHoldingAnalysis result) {
        if (result == null || result.getInstrumentCode() == null
                || result.getEntryDate() == null || result.getAsOfDate() == null
                || result.getQualityStatus() == null || result.getSeries() == null
                || result.getWarnings() == null || !Double.isFinite(result.getHoldingReturn())
                || !Double.isFinite(result.getMaximumDrawdown())) {
            throw contract("SCHEMA_DRIFT", "Python 持仓分析响应缺少必要字段", false, null);
        }
    }

    private ProviderContractException contract(String type, String message,
                                                boolean retryable, Throwable error) {
        if (error == null) {
            return new ProviderContractException(type, message, retryable);
        }
        return new ProviderContractException(type, message, retryable, error);
    }

    private String trim(String value) {
        String normalized = value == null ? "" : value.trim();
        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }
}
