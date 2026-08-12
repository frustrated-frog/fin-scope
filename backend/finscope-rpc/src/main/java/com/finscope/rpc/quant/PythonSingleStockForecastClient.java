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
    private static final Set<String> QUALIFICATION_STATUSES = new HashSet<String>(Arrays.asList(
            "QUALIFIED", "CONDITIONAL", "FAILED", "INSUFFICIENT_DATA"));
    private static final Set<String> CALIBRATION_STATUSES = new HashSet<String>(Arrays.asList(
            "FITTED", "NOT_FITTED"));
    private static final Set<String> INTERVAL_STATUSES = new HashSet<String>(Arrays.asList(
            "AVAILABLE", "UNAVAILABLE"));
    private static final Set<String> DECISIONS = new HashSet<String>(Arrays.asList(
            "UP", "DOWN", "ABSTAIN"));
    private static final Set<String> REPORT_SCHEMA_VERSIONS = new HashSet<String>(Arrays.asList(
            "single-stock-research-v2", "single-stock-research-v3",
            "single-stock-research-v4", "single-stock-research-v5"));

    private final String baseUrl;
    private final FinanceHttpClient http;
    private final int timeoutMs;
    private final ObjectMapper json = new ObjectMapper().registerModule(new JavaTimeModule());

    @Autowired
    public PythonSingleStockForecastClient(
            @Value("${finscope.python-market-data.base-url:http://127.0.0.1:8000}") String baseUrl,
            FinanceHttpClient http,
            @Value("${finscope.python-market-data.forecast-timeout-ms:240000}") int timeoutMs) {
        this.baseUrl = trimTrailingSlash(baseUrl);
        this.http = http;
        if (timeoutMs <= 0) {
            throw new IllegalArgumentException("Python 单股预测超时必须大于零");
        }
        this.timeoutMs = timeoutMs;
    }

    PythonSingleStockForecastClient(String baseUrl, FinanceHttpClient http) {
        this(baseUrl, http, 240_000);
    }

    public SingleStockForecast forecast(String code) {
        return forecast(code, 5);
    }

    public SingleStockForecast forecast(String code, int horizonDays) {
        if (code == null || !code.matches("\\d{6}")) {
            throw contract("INVALID_INSTRUMENT", "股票代码必须是六位 A 股代码", false);
        }
        if (horizonDays != 1 && horizonDays != 5 && horizonDays != 20) {
            throw contract("INVALID_HORIZON", "预测周期只支持 1、5、20 个交易日", false);
        }
        URI uri = URI.create(baseUrl + "/v1/quant/single-stock-forecasts");
        try {
            FinanceHttpResponse response = http.postJson(
                    CLIENT_CODE, uri, "{\"code\":\"" + code + "\",\"horizonDays\":"
                            + horizonDays + "}",
                    Collections.<String, String>emptyMap(), timeoutMs);
            SingleStockForecast result = json.readValue(response.getBody(), SingleStockForecast.class);
            validate(result, code, horizonDays);
            return result;
        } catch (ProviderContractException error) {
            throw error;
        } catch (Exception error) {
            throw contract("SCHEMA_DRIFT", "Python 单股预测响应不符合契约", false, error);
        }
    }

    private void validate(SingleStockForecast result, String code, int horizonDays) {
        String expected = code + "." + market(code);
        if (result == null || !expected.equals(result.getInstrumentCode())
                || result.getAsOfDate() == null || result.getHorizonDays() != horizonDays
                || !STATUSES.contains(result.getStatus()) || result.getConclusion() == null
                || result.getBarCount() == null || result.getBarCount() < 0
                || result.getDataFingerprint() == null || result.getDataFingerprint().trim().isEmpty()
                || !REPORT_SCHEMA_VERSIONS.contains(result.getReportSchemaVersion())
                || result.getModelVersion() == null
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
        if ("single-stock-research-v3".equals(result.getReportSchemaVersion())) {
            validateVersionThree(result);
        }
        if ("single-stock-research-v4".equals(result.getReportSchemaVersion())) {
            validateVersionFour(result);
        }
        if ("single-stock-research-v5".equals(result.getReportSchemaVersion())) {
            validateVersionFive(result);
        }
    }

    private void validateVersionFive(SingleStockForecast result) {
        validateVersionFour(result);
        if ("INSUFFICIENT_DATA".equals(result.getStatus())) {
            if (result.getContext() == null || result.getLeakageAudit() == null) {
                throw contract("SCHEMA_DRIFT", "Python v5 数据不足报告缺少上下文审计", false);
            }
            return;
        }
        SingleStockForecast.ForecastContext context = result.getContext();
        SingleStockForecast.ModelCompetition competition = result.getModelCompetition();
        SingleStockForecast.LeakageAudit leakage = result.getLeakageAudit();
        SingleStockForecast.QlibReference qlib = result.getQlibReference();
        if (context == null || context.getMarket() == null || context.getIndustry() == null
                || context.getFeatureCodes() == null || context.getFeatureCodes().isEmpty()
                || context.getAlignmentRule() == null || competition == null
                || competition.getSelectedModel() == null || competition.getCandidates() == null
                || competition.getCandidates().isEmpty() || competition.getSelectionEndDate() == null
                || competition.getCalibrationStartDate() == null
                || !competition.getSelectionEndDate().isBefore(competition.getCalibrationStartDate())
                || leakage == null || !"PASSED".equals(leakage.getStatus())
                || leakage.getCheckedSampleCount() < 1 || qlib == null || qlib.isRuntimeDependency()) {
            throw contract("SCHEMA_DRIFT", "Python v5 上下文、竞赛或泄漏审计无效", false);
        }
        probability(context.getMarket().getCoverage());
        probability(context.getIndustry().getCoverage());
        int selected = 0;
        String selectedCandidateCode = null;
        for (SingleStockForecast.ModelCandidate candidate : competition.getCandidates()) {
            if (candidate == null || candidate.getCode() == null || candidate.getName() == null
                    || candidate.getSelectionSampleCount() < 1 || candidate.getReason() == null
                    || !finiteNonNegative(candidate.getLogLoss())) {
                throw contract("SCHEMA_DRIFT", "Python v5 模型候选无效", false);
            }
            probability(candidate.getAccuracy());
            probability(candidate.getBrierScore());
            probability(candidate.getBaselineBrierScore());
            if (candidate.isSelected()) {
                selected++;
                selectedCandidateCode = candidate.getCode();
            }
        }
        if (selected != 1 || !competition.getSelectedModel().equals(selectedCandidateCode)) {
            throw contract("SCHEMA_DRIFT", "Python v5 模型竞赛冠军标识不一致", false);
        }
    }

    private void validateVersionFour(SingleStockForecast result) {
        validateVersionThree(result);
        if (!DECISIONS.contains(result.getDecision()) || result.getDecisionReason() == null) {
            throw contract("SCHEMA_DRIFT", "Python v4 单股预测缺少选择性决策", false);
        }
        if ("INSUFFICIENT_DATA".equals(result.getStatus())) {
            return;
        }
        SingleStockForecast.SelectiveValidation selective = result.getSelectiveValidation();
        if (result.getQualification().getSplitAudit().getLabelHorizonDays() != result.getHorizonDays()
                || selective == null || selective.getSampleCount() < 1 || selective.getCoveredCount() < 0
                || selective.getCoveredCount() > selective.getSampleCount()
                || selective.getLowerThreshold() <= 0 || selective.getLowerThreshold() >= 0.5d
                || selective.getUpperThreshold() <= 0.5d || selective.getUpperThreshold() >= 1d) {
            throw contract("SCHEMA_DRIFT", "Python v4 单股预测选择性指标无效", false);
        }
        probability(selective.getCoverage());
        probability(selective.getCoveredAccuracy());
        probability(selective.getAbstainRate());
    }

    private void validateVersionThree(SingleStockForecast result) {
        probability(result.getRawProbability());
        if ("INSUFFICIENT_DATA".equals(result.getStatus()) && result.getQualification() == null) return;
        if (!"INSUFFICIENT_DATA".equals(result.getStatus())
                && (result.getUpProbability() == null || result.getRawProbability() == null)) {
            throw contract("SCHEMA_DRIFT", "Python v3 单股预测缺少生产概率", false);
        }
        validateInterval(result.getProbabilityInterval());
        SingleStockForecast.ModelQualification qualification = result.getQualification();
        if (qualification == null || !QUALIFICATION_STATUSES.contains(qualification.getStatus())
                || qualification.getTrial() == null || qualification.getSplitAudit() == null
                || qualification.getCalibration() == null || qualification.getLockedTest() == null
                || qualification.getConfidenceIntervals() == null) {
            throw contract("SCHEMA_DRIFT", "Python v3 单股预测缺少资格检验证据", false);
        }
        validateTrial(qualification.getTrial(), result.getModelVersion());
        validateSplit(qualification.getSplitAudit());
        validateCalibration(qualification.getCalibration());
        validateLockedTest(qualification.getLockedTest());
        validateInterval(qualification.getConfidenceIntervals().getBrierSkillScore());
        validateInterval(qualification.getConfidenceIntervals().getAccuracy());
        validateInterval(qualification.getConfidenceIntervals().getExcessReturn());
        validateInterval(qualification.getConfidenceIntervals().getSharpeRatio());
    }

    private void validateTrial(SingleStockForecast.TrialIdentity trial, String modelVersion) {
        if (trial.getTrialId() == null || !trial.getTrialId().matches("[0-9a-f]{64}")
                || trial.getFeatureVersion() == null || trial.getLabelVersion() == null
                || trial.getSplitVersion() == null || trial.getCalibrationVersion() == null
                || trial.getBootstrapVersion() == null || !modelVersion.equals(trial.getModelVersion())) {
            throw contract("SCHEMA_DRIFT", "Python v3 单股预测试验身份无效", false);
        }
    }

    private void validateSplit(SingleStockForecast.QualificationSplitAudit audit) {
        validateSlice(audit.getDevelopment());
        validateSlice(audit.getCalibration());
        validateSlice(audit.getLockedTest());
        if (audit.getDevelopment().getEndDate().compareTo(audit.getCalibration().getStartDate()) >= 0
                || audit.getCalibration().getEndDate().compareTo(audit.getLockedTest().getStartDate()) >= 0
                || audit.getLabelHorizonDays() < 1
                || audit.getLabelHorizonDays() != audit.getIndependentStrideDays()
                || audit.getRule() == null) {
            throw contract("SCHEMA_DRIFT", "Python v3 单股预测时间切分无效", false);
        }
    }

    private void validateSlice(SingleStockForecast.SplitSliceAudit slice) {
        if (slice == null || slice.getStartDate() == null || slice.getEndDate() == null
                || slice.getStartDate().isAfter(slice.getEndDate()) || slice.getSampleCount() < 1
                || slice.getIndependentSampleCount() < 1 || slice.getPositiveCount() < 0
                || slice.getPositiveCount() > slice.getIndependentSampleCount() || slice.getPurgedCount() < 0) {
            throw contract("SCHEMA_DRIFT", "Python v3 单股预测切分审计无效", false);
        }
    }

    private void validateCalibration(SingleStockForecast.CalibrationReport calibration) {
        if (!CALIBRATION_STATUSES.contains(calibration.getStatus()) || calibration.getMethod() == null
                || calibration.getSampleCount() < 0 || calibration.getPositiveCount() < 0
                || calibration.getPositiveCount() > calibration.getSampleCount()
                || !finite(calibration.getSlope()) || !finite(calibration.getIntercept())
                || !finiteNonNegative(calibration.getRawLogLoss())
                || !finiteNonNegative(calibration.getCalibratedLogLoss())) {
            throw contract("SCHEMA_DRIFT", "Python v3 单股预测校准证据无效", false);
        }
    }

    private void validateLockedTest(SingleStockForecast.LockedTestReport locked) {
        probability(locked.getBaselineProbability());
        validateMetrics(locked.getRawMetrics());
        validateMetrics(locked.getCalibratedMetrics());
        validateMetrics(locked.getBaselineMetrics());
        if (locked.getReliabilityBins() == null || locked.getReliabilityBins().size() != 5) {
            throw contract("SCHEMA_DRIFT", "Python v3 单股预测可靠性分箱数量无效", false);
        }
        int total = 0;
        double expectedLower = 0d;
        for (SingleStockForecast.ReliabilityBin bin : locked.getReliabilityBins()) {
            if (bin == null || Math.abs(bin.getLowerBound() - expectedLower) > 0.0000001d
                    || bin.getUpperBound() <= bin.getLowerBound() || bin.getUpperBound() > 1d
                    || bin.getCount() < 0) {
                throw contract("SCHEMA_DRIFT", "Python v3 单股预测可靠性分箱边界无效", false);
            }
            if (bin.getCount() == 0) {
                if (bin.getMeanProbability() != null || bin.getObservedUpRate() != null) {
                    throw contract("SCHEMA_DRIFT", "Python v3 空可靠性分箱包含观测值", false);
                }
            } else {
                probability(bin.getMeanProbability());
                probability(bin.getObservedUpRate());
                probability(bin.getCalibrationError());
            }
            total += bin.getCount();
            expectedLower = bin.getUpperBound();
        }
        if (Math.abs(expectedLower - 1d) > 0.0000001d
                || total != locked.getCalibratedMetrics().getSampleCount()) {
            throw contract("SCHEMA_DRIFT", "Python v3 可靠性分箱样本合计无效", false);
        }
    }

    private void validateMetrics(SingleStockForecast.ProbabilityMetricSet metrics) {
        if (metrics == null || metrics.getSampleCount() < 1 || !finite(metrics.getBrierSkillScore())
                || !finiteNonNegative(metrics.getLogLoss())) {
            throw contract("SCHEMA_DRIFT", "Python v3 单股预测概率指标无效", false);
        }
        probability(metrics.getAccuracy());
        probability(metrics.getBrierScore());
        probability(metrics.getBaselineBrierScore());
        probability(metrics.getExpectedCalibrationError());
    }

    private void validateInterval(SingleStockForecast.ConfidenceInterval interval) {
        if (interval == null || !INTERVAL_STATUSES.contains(interval.getStatus())
                || !finite(interval.getConfidenceLevel()) || interval.getConfidenceLevel() <= 0d
                || interval.getConfidenceLevel() >= 1d || interval.getMethod() == null
                || interval.getValidIterations() < 0) {
            throw contract("SCHEMA_DRIFT", "Python v3 单股预测置信区间无效", false);
        }
        if ("AVAILABLE".equals(interval.getStatus())) {
            if (!finite(interval.getLower()) || !finite(interval.getUpper())
                    || interval.getLower() > interval.getUpper() || interval.getValidIterations() < 1) {
                throw contract("SCHEMA_DRIFT", "Python v3 单股预测置信区间上下界无效", false);
            }
        } else if (interval.getLower() != null || interval.getUpper() != null) {
            throw contract("SCHEMA_DRIFT", "Python v3 不可用区间不应包含上下界", false);
        }
    }

    private void probability(Double value) {
        if (value != null && (value.isNaN() || value.isInfinite() || value < 0d || value > 1d)) {
            throw contract("SCHEMA_DRIFT", "Python 单股预测概率超出范围", false);
        }
    }

    private boolean finite(Double value) {
        return value != null && !value.isNaN() && !value.isInfinite();
    }

    private boolean finite(double value) {
        return !Double.isNaN(value) && !Double.isInfinite(value);
    }

    private boolean finiteNonNegative(double value) {
        return finite(value) && value >= 0d;
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
