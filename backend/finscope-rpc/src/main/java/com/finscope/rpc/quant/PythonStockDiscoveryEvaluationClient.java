package com.finscope.rpc.quant;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.finscope.domain.quant.discovery.StockDiscoveryAccuracyReport;
import com.finscope.domain.quant.discovery.StockDiscoveryEvaluationRequest;
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
import java.util.Set;

@Component
public class PythonStockDiscoveryEvaluationClient {
    private static final String CLIENT_CODE = "PYTHON_STOCK_DISCOVERY_EVALUATION";
    @Value("${finscope.python-market-data.base-url:http://127.0.0.1:8000}")
    private String baseUrl;
    @Resource
    private FinanceHttpClient http;
    @Value("${finscope.python-market-data.evaluation-timeout-ms:30000}")
    private int timeoutMs;
    private final ObjectMapper json = new ObjectMapper();
    private final ObjectMapper snakeJson = new ObjectMapper()
            .setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
            .addMixIn(StockDiscoveryAccuracyReport.RankingChallenger.class,
                    RankingChallengerMixin.class);

    private abstract static class RankingChallengerMixin {
        @JsonProperty("top_k_average_return")
        abstract void setTopKAverageReturn(Double value);

        @JsonProperty("top_k_excess_return")
        abstract void setTopKExcessReturn(Double value);
    }

    public PythonStockDiscoveryEvaluationClient() {
    }

    PythonStockDiscoveryEvaluationClient(String baseUrl, FinanceHttpClient http, int timeoutMs) {
        this.baseUrl = baseUrl;
        this.http = http;
        this.timeoutMs = timeoutMs;
    }

    public StockDiscoveryAccuracyReport evaluate(StockDiscoveryEvaluationRequest request) {
        if (request == null || request.getPendingCount() < 0 || request.getObservations() == null
                || request.getModelObservations() == null) {
            throw contract("INVALID_REQUEST", "股票发现真实评测请求无效", false, null);
        }
        try {
            FinanceHttpResponse response = http.postJson(CLIENT_CODE,
                    URI.create(trim(baseUrl) + "/v1/quant/stock-discovery-evaluations"),
                    json.writeValueAsString(request), Collections.<String, String>emptyMap(), timeoutMs);
            StockDiscoveryAccuracyReport report = snakeJson.readValue(
                    response.getBody(), StockDiscoveryAccuracyReport.class);
            validate(report, request);
            return report;
        } catch (ProviderContractException error) {
            throw error;
        } catch (Exception error) {
            throw contract("SCHEMA_DRIFT", "Python 股票发现真实评测响应不符合契约", false, error);
        }
    }

    private void validate(StockDiscoveryAccuracyReport report, StockDiscoveryEvaluationRequest request) {
        if (report == null || !"stock-discovery-evaluation-v1".equals(report.getSchemaVersion())
                || report.getAsOfDate() == null || report.getHorizonDays() != 5
                || !("ACCUMULATING".equals(report.getStatus()) || "HEALTHY".equals(report.getStatus())
                || "WATCH".equals(report.getStatus()))
                || report.getConclusion() == null || report.getProbabilityQuality() == null
                || report.getReliabilityBins() == null || report.getReliabilityBins().size() != 5
                || report.getSelectionMetrics() == null || report.getSelectionMetrics().size() != 3
                || report.getWindows() == null || report.getWindows().size() != 3
                || report.getSectorPerformance() == null || report.getModelRace() == null
                || report.getRankingChallenger() == null
                || report.getRecentOutcomes() == null || report.getWarnings() == null
                || report.getPendingCount() != request.getPendingCount()
                || report.getMaturedCandidateCount() != request.getObservations().size()
                || report.getProbabilityQuality().getSampleCount() > report.getMaturedCandidateCount()
                || report.getMaturedFinalCount() > report.getMaturedCandidateCount()) {
            throw contract("SCHEMA_DRIFT", "Python 股票发现真实评测缺少必需字段或计数无效", false, null);
        }
        try {
            LocalDate.parse(report.getAsOfDate());
        } catch (RuntimeException error) {
            throw contract("SCHEMA_DRIFT", "Python 股票发现真实评测日期无效", false, error);
        }
        assertMetricLimits(report);
    }

    private void assertMetricLimits(StockDiscoveryAccuracyReport report) {
        Set<Integer> selectionLimits = new HashSet<>();
        for (StockDiscoveryAccuracyReport.SelectionMetric metric : report.getSelectionMetrics()) {
            selectionLimits.add(metric.getLimit());
        }
        Set<Integer> windowDays = new HashSet<>();
        for (StockDiscoveryAccuracyReport.WindowMetric metric : report.getWindows()) {
            windowDays.add(metric.getWindowDays());
        }
        StockDiscoveryAccuracyReport.ProbabilityQuality quality = report.getProbabilityQuality();
        StockDiscoveryAccuracyReport.RankingChallenger ranking = report.getRankingChallenger();
        if (!selectionLimits.equals(Set.of(1, 3, 5)) || !windowDays.equals(Set.of(30, 90, 180))
                || !unit(quality.getAccuracy()) || !unit(quality.getExpectedCalibrationError())
                || !unit(quality.getBaselineProbability()) || !finite(quality.getBrierScore())
                || !finite(quality.getBrierSkillScore()) || !finite(quality.getLogLoss())
                || !("SHADOW_ACCUMULATING".equals(ranking.getStatus())
                || "SHADOW_EVALUATING".equals(ranking.getStatus())
                || "PROMOTION_REVIEW".equals(ranking.getStatus()))
                || ranking.getTrainingDateCount() < 0 || ranking.getCalibrationDateCount() < 0
                || ranking.getLockedDateCount() < 0 || ranking.getObservationCount() < 0
                || ranking.getPairCount() < 0 || ranking.getTopK() != 3
                || !unit(ranking.getPairwiseAccuracy()) || !finite(ranking.getRankIc())
                || ranking.getRankIc() != null
                && (ranking.getRankIc() < -1d || ranking.getRankIc() > 1d)
                || !finite(ranking.getTopKAverageReturn())
                || !finite(ranking.getAdmittedPoolAverageReturn())
                || !finite(ranking.getTopKExcessReturn())
                || ranking.getFeatureWeights() == null || ranking.getMethod() == null) {
            throw contract("SCHEMA_DRIFT", "Python 股票发现真实评测指标范围无效", false, null);
        }
    }

    private boolean unit(Double value) {
        return value == null || Double.isFinite(value) && value >= 0d && value <= 1d;
    }

    private boolean finite(Double value) {
        return value == null || Double.isFinite(value);
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
