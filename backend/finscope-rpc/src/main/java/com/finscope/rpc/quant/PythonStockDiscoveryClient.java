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
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

@Component
public class PythonStockDiscoveryClient {
    private static final int MAX_REPORT_BYTES = 8 * 1024 * 1024;
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
                    json.writeValueAsString(request), Collections.<String, String>emptyMap(), timeoutMs, MAX_REPORT_BYTES);
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
                || !"TONGHUASHUN".equals(report.getSourceFamily())
                || report.getDataFingerprint() == null
                || !report.getDataFingerprint().matches("[0-9a-f]{64}")
                || report.getFunnel() == null || report.getFinalCandidates() == null
                || report.getRelativeCandidates() == null
                || report.getCandidates() == null || report.getDeepEvidence() == null
                || report.getSectors() == null || report.getSectors().isEmpty()
                || report.getConstituentSourceFamilies() == null
                || report.getConstituentSourceFamilies().isEmpty()
                || !allowedConstituentQuality(report.getConstituentQualityStatus())
                || report.getFinalCandidates().size() != report.getFunnel().getFinalCount()
                || report.getDeepEvidence().size() != report.getFunnel().getDeepReviewCount()
                || report.getFunnel().getFinalCount() < 0 || report.getFunnel().getFinalCount() > 5
                || report.getFunnel().getDeepReviewCount() < report.getFunnel().getFinalCount()
                || report.getFunnel().getAdmittedCount() < report.getFunnel().getDeepReviewCount()
                || report.getFunnel().getQuantifiedCount() != report.getFunnel().getAdmittedCount()
                || report.getFunnel().getAdmittedCount() > report.getCandidates().size()
                || report.getCandidates().size() != report.getFunnel().getConstituentCount()
                || report.getFunnel().getRawConstituentCount()
                != report.getFunnel().getConstituentCount() + report.getFunnel().getScopeExcludedCount()
                || report.getFunnel().getScopeExcludedCount()
                != report.getFunnel().getStarMarketExcludedCount()
                + report.getFunnel().getBeijingMarketExcludedCount()
                + report.getFunnel().getUnsupportedScopeExcludedCount()) {
            throw contract("SCHEMA_DRIFT", "Python 股票发现缺少必需字段或漏斗计数无效", false, null);
        }
        try {
            LocalDate.parse(report.getAsOfDate());
        } catch (RuntimeException error) {
            throw contract("SCHEMA_DRIFT", "Python 股票发现业务日期格式无效", false, error);
        }
        validateSectors(report.getSectors());
        validateCandidateRelations(report);
        validateRelativeCandidates(report);
    }

    private boolean allowedConstituentQuality(String value) {
        return "COMPLETE".equals(value) || "MIXED_COMPLETE".equals(value)
                || "CACHED_COMPLETE".equals(value) || "PARTIAL".equals(value);
    }

    private void validateSectors(Iterable<Map<String, Object>> sectors) {
        Set<Integer> ranks = new HashSet<Integer>();
        for (Map<String, Object> sector : sectors) {
            int expected = integer(sector.get("expected_constituent_count"));
            int resolved = integer(sector.get("resolved_constituent_count"));
            int rank = integer(sector.get("source_rank"));
            double coverage = decimal(sector.get("constituent_coverage"));
            String quality = text(sector.get("constituent_quality_status"));
            if (!"TONGHUASHUN".equals(text(sector.get("source_family")))
                    || !"INDUSTRY".equals(text(sector.get("category")))
                    || rank < 1 || !ranks.add(rank)
                    || expected < 0 || resolved < 0 || coverage < 0d || coverage > 1d
                    || !("COMPLETE".equals(quality) || "CACHED_COMPLETE".equals(quality)
                    || "SUPPLEMENTED_COMPLETE".equals(quality) || "PARTIAL".equals(quality))) {
                throw contract("SCHEMA_DRIFT", "Python 股票发现板块来源或成分证据无效", false, null);
            }
        }
    }

    private void validateCandidateRelations(StockDiscoveryReport report) {
        Set<String> candidateCodes = codes(report.getCandidates());
        Set<String> deepCodes = codes(report.getDeepEvidence());
        Set<String> selectedCodes = new HashSet<String>();
        Set<Integer> ranks = new HashSet<Integer>();
        int expectedCount = report.getFinalCandidates().size();
        for (Map<String, Object> selected : report.getFinalCandidates()) {
            String code = text(selected.get("code"));
            int rank = integer(selected.get("final_rank"));
            if (!selectedCodes.add(code) || !candidateCodes.contains(code) || !deepCodes.contains(code)
                    || rank < 1 || rank > expectedCount || !ranks.add(rank)
                    || !Boolean.TRUE.equals(selected.get("qualified"))
                    || !"HEALTHY".equals(text(selected.get("health_status")))
                    || !("ROBUST".equals(text(selected.get("conclusion")))
                    || "CONDITIONALLY_EFFECTIVE".equals(text(selected.get("conclusion"))))
                    || !(selected.get("calibrated_probability") instanceof Number)
                    || !(selected.get("probability_lower_bound") instanceof Number)
                    || !(selected.get("evidence") instanceof java.util.List)
                    || !(selected.get("risks") instanceof java.util.List)
                    || !(selected.get("forecast_report") instanceof Map)) {
                throw contract("SCHEMA_DRIFT", "Python 股票发现候选关系或最终排名无效", false, null);
            }
        }
    }

    private void validateRelativeCandidates(StockDiscoveryReport report) {
        Set<String> deepCodes = codes(report.getDeepEvidence());
        Set<String> rankedCodes = new HashSet<String>();
        Set<Integer> ranks = new HashSet<Integer>();
        int expectedCount = Math.min(5, report.getDeepEvidence().size());
        if (report.getRelativeCandidates().size() != expectedCount) {
            throw contract("SCHEMA_DRIFT", "Python 股票发现相对研究榜数量无效", false, null);
        }
        for (Map<String, Object> selected : report.getRelativeCandidates()) {
            String code = text(selected.get("code"));
            int rank = integer(selected.get("relative_rank"));
            String tier = text(selected.get("research_tier"));
            if (!rankedCodes.add(code) || !deepCodes.contains(code)
                    || rank < 1 || rank > expectedCount || !ranks.add(rank)
                    || !(selected.get("relative_score") instanceof Number)
                    || !("ACTIONABLE".equals(tier) || "CONDITIONAL".equals(tier)
                    || "WATCH".equals(tier))) {
                throw contract("SCHEMA_DRIFT", "Python 股票发现相对研究排名无效", false, null);
            }
        }
    }

    private Set<String> codes(Iterable<Map<String, Object>> values) {
        Set<String> result = new HashSet<String>();
        for (Map<String, Object> value : values) {
            String code = text(value.get("code"));
            if (code.isEmpty() || excludedTradingScope(code) || !result.add(code)) {
                throw contract("SCHEMA_DRIFT", "Python 股票发现候选代码缺失或重复", false, null);
            }
        }
        return result;
    }

    private boolean excludedTradingScope(String code) {
        return code.startsWith("688") || code.startsWith("689") || code.startsWith("4")
                || code.startsWith("8") || code.startsWith("92");
    }

    private double decimal(Object value) {
        if (value instanceof Number) {
            return ((Number) value).doubleValue();
        }
        try {
            return Double.parseDouble(text(value));
        } catch (NumberFormatException error) {
            return -1d;
        }
    }

    private String text(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private int integer(Object value) {
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        try {
            return Integer.parseInt(text(value));
        } catch (NumberFormatException error) {
            return -1;
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
