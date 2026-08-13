package com.finscope.rpc.quant;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.finscope.domain.quant.discovery.StockDiscoveryReport;
import com.finscope.rpc.marketintel.FinanceHttpClient;
import com.finscope.rpc.marketintel.FinanceHttpResponse;
import com.finscope.rpc.marketintel.ProviderContractException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.net.URI;
import java.time.LocalDate;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

@Component
public class PythonStockDiscoveryClient {
    private static final String CLIENT_CODE = "PYTHON_STOCK_DISCOVERY";
    @Value("${finscope.python-market-data.base-url:http://127.0.0.1:8000}")
    private String baseUrl;
    @Resource
    private FinanceHttpClient http;
    @Value("${finscope.python-market-data.discovery-timeout-ms:900000}")
    private int timeoutMs;
    private final ObjectMapper json = new ObjectMapper();
    private final ObjectMapper snakeJson = new ObjectMapper()
            .setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE);

    public PythonStockDiscoveryClient() {
    }

    PythonStockDiscoveryClient(String baseUrl, FinanceHttpClient http, int timeoutMs) {
        this.baseUrl = baseUrl;
        this.http = http;
        this.timeoutMs = timeoutMs;
    }

    public StockDiscoveryReport discover(LocalDate businessDate, double budget, String policyVersion) {
        if (businessDate == null || budget <= 0 || policyVersion == null || policyVersion.trim().isEmpty()) {
            throw contract("INVALID_REQUEST", "股票发现请求参数无效", false, null);
        }
        try {
            Map<String, Object> request = new LinkedHashMap<String, Object>();
            request.put("businessDate", businessDate.toString());
            request.put("budget", budget);
            request.put("sectorLimit", 5);
            request.put("deepLimit", 15);
            request.put("finalLimit", 5);
            request.put("horizonDays", 5);
            request.put("policyVersion", policyVersion);
            FinanceHttpResponse response = http.postJson(CLIENT_CODE,
                    URI.create(trim(baseUrl) + "/v1/quant/stock-discoveries"),
                    json.writeValueAsString(request), Collections.<String, String>emptyMap(), timeoutMs);
            StockDiscoveryReport report = snakeJson.readValue(response.getBody(), StockDiscoveryReport.class);
            validate(report, policyVersion);
            report.setRawJson(response.getBody());
            return report;
        } catch (ProviderContractException error) {
            throw error;
        } catch (Exception error) {
            throw contract("SCHEMA_DRIFT", "Python 股票发现响应不符合契约", false, error);
        }
    }

    private void validate(StockDiscoveryReport report, String policyVersion) {
        if (report == null || !"1.0.0".equals(report.getSchemaVersion())
                || !policyVersion.equals(report.getPolicyVersion()) || report.getAsOfDate() == null
                || report.getSourceFamily() == null || report.getQualityStatus() == null
                || report.getDataFingerprint() == null || report.getDataFingerprint().length() != 64
                || report.getFunnel() == null || report.getFinalCandidates() == null
                || report.getFinalCandidates().size() != report.getFunnel().getFinalCount()
                || report.getFunnel().getFinalCount() < 0 || report.getFunnel().getFinalCount() > 5
                || report.getFunnel().getDeepReviewCount() < report.getFunnel().getFinalCount()
                || report.getFunnel().getAdmittedCount() < report.getFunnel().getDeepReviewCount()) {
            throw contract("SCHEMA_DRIFT", "Python 股票发现缺少必需字段或漏斗计数无效", false, null);
        }
    }

    private ProviderContractException contract(String type, String message, boolean retryable, Throwable error) {
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
