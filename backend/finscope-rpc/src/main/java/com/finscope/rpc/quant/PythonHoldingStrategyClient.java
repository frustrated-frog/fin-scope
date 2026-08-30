package com.finscope.rpc.quant;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.finscope.domain.strategy.holding.HoldingStrategyAdvice;
import com.finscope.domain.strategy.holding.HoldingStrategyEvaluationRequest;
import com.finscope.domain.strategy.holding.HoldingStrategySettlementRequest;
import com.finscope.domain.strategy.holding.HoldingStrategySettlementResult;
import com.finscope.rpc.marketintel.FinanceHttpClient;
import com.finscope.rpc.marketintel.FinanceHttpResponse;
import com.finscope.rpc.marketintel.ProviderContractException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.net.URI;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

@Component
public class PythonHoldingStrategyClient {
    private static final String CLIENT_CODE = "PYTHON_HOLDING_STRATEGY";
    private static final Set<String> ACTIONS = new HashSet<String>(Arrays.asList(
            "HOLD", "ALLOW_ADD", "REDUCE_CONCENTRATION", "EXIT_TRIGGERED", "ABSTAIN"));

    @Value("${finscope.python-market-data.base-url:http://127.0.0.1:8000}")
    private String baseUrl;
    @Resource
    private FinanceHttpClient http;
    @Value("${finscope.python-market-data.evaluation-timeout-ms:30000}")
    private int timeoutMs;
    private final ObjectMapper json = new ObjectMapper().registerModule(new JavaTimeModule());

    public PythonHoldingStrategyClient() {
    }

    PythonHoldingStrategyClient(String baseUrl, FinanceHttpClient http, int timeoutMs) {
        this.baseUrl = baseUrl;
        this.http = http;
        this.timeoutMs = timeoutMs;
    }

    public HoldingStrategyAdvice evaluate(HoldingStrategyEvaluationRequest request) {
        if (request == null || request.getInstrumentCode() == null
                || request.getAsOfDate() == null || request.getMarketPrice() <= 0) {
            throw contract("INVALID_REQUEST", "持仓策略请求缺少必要字段", false, null);
        }
        try {
            FinanceHttpResponse response = http.postJson(CLIENT_CODE,
                    URI.create(trim(baseUrl) + "/v1/quant/holding-strategies/evaluate"),
                    json.writeValueAsString(request), Collections.<String, String>emptyMap(), timeoutMs);
            HoldingStrategyAdvice advice = json.readValue(response.getBody(), HoldingStrategyAdvice.class);
            validate(advice);
            return advice;
        } catch (ProviderContractException error) {
            throw error;
        } catch (Exception error) {
            throw contract("SCHEMA_DRIFT", "Python 持仓策略响应不符合契约", false, error);
        }
    }

    public HoldingStrategySettlementResult settle(HoldingStrategySettlementRequest request) {
        if (request == null || request.getAction() == null || request.getHeldQuantity() <= 0
                || request.getCurrentMarketValue() <= 0 || request.getEntryPrice() <= 0) {
            throw contract("INVALID_REQUEST", "持仓策略结算请求缺少必要字段", false, null);
        }
        try {
            FinanceHttpResponse response = http.postJson(CLIENT_CODE,
                    URI.create(trim(baseUrl) + "/v1/quant/holding-strategies/settle"),
                    json.writeValueAsString(request), Collections.<String, String>emptyMap(), timeoutMs);
            HoldingStrategySettlementResult result = json.readValue(
                    response.getBody(), HoldingStrategySettlementResult.class);
            if (result.getMethod() == null || !Double.isFinite(result.getStrategyReturn())
                    || !Double.isFinite(result.getHoldReturn())
                    || !Double.isFinite(result.getIncrementalReturn())) {
                throw contract("SCHEMA_DRIFT", "Python 持仓策略结算响应缺少必要字段", false, null);
            }
            return result;
        } catch (ProviderContractException error) {
            throw error;
        } catch (Exception error) {
            throw contract("SCHEMA_DRIFT", "Python 持仓策略结算响应不符合契约", false, error);
        }
    }

    private void validate(HoldingStrategyAdvice advice) {
        if (advice == null || !ACTIONS.contains(advice.getAction())
                || advice.getSuggestedQuantity() < 0 || advice.getPolicyVersion() == null
                || advice.getExplanation() == null || advice.getBenchmark() == null
                || advice.getEvidence() == null || advice.getBlockers() == null
                || !Double.isFinite(advice.getExpectedEdgeAfterCost())
                || !Double.isFinite(advice.getP10RiskAmount())
                || !Double.isFinite(advice.getP90UpsideAmount())) {
            throw contract("SCHEMA_DRIFT", "Python 持仓策略响应缺少必要字段", false, null);
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
