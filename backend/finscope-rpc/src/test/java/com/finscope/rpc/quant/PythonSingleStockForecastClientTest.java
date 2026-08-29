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
    void usesForecastSpecificTimeoutInsteadOfTheSharedMarketDataTimeout() {
        AtomicReference<Integer> timeoutMs = new AtomicReference<Integer>();
        FinanceHttpClient http = new FinanceHttpClient() {
            @Override
            public FinanceHttpResponse get(String providerCode, URI uri, Map<String, String> headers) {
                throw new UnsupportedOperationException();
            }

            @Override
            public FinanceHttpResponse postJson(String providerCode, URI uri, String body,
                                                Map<String, String> headers) {
                throw new AssertionError("forecast must use its dedicated timeout");
            }

            @Override
            public FinanceHttpResponse postJson(String providerCode, URI uri, String body,
                                                Map<String, String> headers, int requestTimeoutMs) {
                timeoutMs.set(requestTimeoutMs);
                return response(v5Payload());
            }
        };

        new PythonSingleStockForecastClient("http://127.0.0.1:8000", http, 240_000)
                .forecast("600519", 5);

        assertEquals(240_000, timeoutMs.get());
    }

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
                "http://127.0.0.1:8000/", http).forecast("600519", 20);

        assertEquals("/v1/quant/single-stock-forecasts", uri.get().getPath());
        assertEquals("{\"code\":\"600519\",\"horizonDays\":20}", body.get());
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
                () -> client.forecast("600519", 20)).getErrorType());
    }

    @Test
    void mapsAndValidatesVersionThreeQualification() {
        PythonSingleStockForecastClient client = clientReturning(v3Payload(0.45, 0.75, repeat('a', 64), 15));

        SingleStockForecast result = client.forecast("600519", 20);

        assertEquals("QUALIFIED", result.getQualification().getStatus());
        assertEquals(0.08d,
                result.getQualification().getLockedTest().getCalibratedMetrics().getBrierSkillScore(),
                0.000001d);
    }

    @Test
    void rejectsReversedVersionThreeInterval() {
        PythonSingleStockForecastClient client = clientReturning(v3Payload(0.75, 0.45, repeat('a', 64), 15));

        assertEquals("SCHEMA_DRIFT", assertThrows(
                ProviderContractException.class,
                () -> client.forecast("600519", 20)).getErrorType());
    }

    @Test
    void rejectsInvalidTrialIdentityAndReliabilityCount() {
        PythonSingleStockForecastClient invalidTrial = clientReturning(v3Payload(0.45, 0.75, "short", 15));
        PythonSingleStockForecastClient invalidBins = clientReturning(v3Payload(0.45, 0.75, repeat('b', 64), 14));

        assertThrows(ProviderContractException.class, () -> invalidTrial.forecast("600519", 20));
        assertThrows(ProviderContractException.class, () -> invalidBins.forecast("600519", 20));
    }

    @Test
    void mapsVersionFourSelectiveDecisionForRequestedHorizon() {
        PythonSingleStockForecastClient client = clientReturning(v4Payload());

        SingleStockForecast result = client.forecast("600519", 5);

        assertEquals("ABSTAIN", result.getDecision());
        assertEquals(0.6d, result.getSelectiveValidation().getCoverage(), 0.000001d);
        assertEquals(5, result.getQualification().getSplitAudit().getLabelHorizonDays());
    }

    @Test
    void mapsAndValidatesVersionFiveContextCompetitionAndLeakageEvidence() {
        PythonSingleStockForecastClient client = clientReturning(v5Payload());

        SingleStockForecast result = client.forecast("600519", 5);

        assertEquals("000300.SH", result.getContext().getMarket().getCode());
        assertEquals("LOGISTIC", result.getModelCompetition().getSelectedModel());
        assertEquals("PASSED", result.getLeakageAudit().getStatus());
        assertEquals(false, result.getQlibReference().isRuntimeDependency());
    }

    @Test
    void rejectsVersionFiveWhenSelectedCandidateDoesNotMatchWinnerCode() {
        PythonSingleStockForecastClient client = clientReturning(
                v5Payload().replace("\"selectedModel\":\"LOGISTIC\"",
                        "\"selectedModel\":\"BOOSTED_STUMPS\""));

        assertThrows(ProviderContractException.class, () -> client.forecast("600519", 5));
    }

    @Test
    void mapsAndValidatesVersionSixFrozenCandidateEvidence() {
        SingleStockForecast result = clientReturning(v6Payload()).forecast("600519", 5);

        assertEquals(4, result.getModelCompetition().getCandidates().size());
        SingleStockForecast.ModelCandidate champion = result.getModelCompetition().getCandidates().stream()
                .filter(SingleStockForecast.ModelCandidate::isSelected).findFirst().orElseThrow();
        assertEquals("CHAMPION", champion.getRole());
        assertEquals(.61d, champion.getCalibratedProbability(), .000001d);
        assertEquals(15, champion.getLockedMetrics().getSampleCount());
    }

    @Test
    void rejectsVersionSixCandidateWithInvalidRoleOrProbability() {
        PythonSingleStockForecastClient invalidRole = clientReturning(
                v6Payload().replace("\"role\":\"CHALLENGER\"", "\"role\":\"UNKNOWN\""));
        PythonSingleStockForecastClient invalidProbability = clientReturning(
                v6Payload().replace("\"calibratedProbability\":0.55", "\"calibratedProbability\":1.2"));

        assertThrows(ProviderContractException.class, () -> invalidRole.forecast("600519", 5));
        assertThrows(ProviderContractException.class, () -> invalidProbability.forecast("600519", 5));
    }

    @Test
    void mapsAndValidatesVersionSevenBacktestAudit() {
        SingleStockForecast result = clientReturning(v7Payload()).forecast("600519", 5);

        assertEquals("PASS", result.getBacktestAudit().getStatus());
        assertEquals("SHADOW", result.getBacktestAudit().getMode());
        assertEquals("BACKTESTING_PY", result.getBacktestAudit().getShadowEngine().getEngine());
        assertEquals(1d, result.getBacktestAudit().getEntryDateAgreementRate(), .000001d);
        assertEquals(5, result.getParameterStability().getScenarioCount());
        assertEquals(4, result.getParameterStability().getRobustRegionSize());
    }

    @Test
    void rejectsVersionSevenInvalidAgreementOrMissingShadowEvidence() {
        PythonSingleStockForecastClient invalidAgreement = clientReturning(
                v7Payload().replace("\"entryDateAgreementRate\":1.0",
                        "\"entryDateAgreementRate\":1.2"));
        PythonSingleStockForecastClient missingShadow = clientReturning(
                v7Payload().replace("\"shadowEngine\":" + auditEngine("BACKTESTING_PY") + ",", ""));
        PythonSingleStockForecastClient invalidRobustness = clientReturning(
                v7Payload().replace("\"scenarioCount\":5", "\"scenarioCount\":0"));

        assertThrows(ProviderContractException.class,
                () -> invalidAgreement.forecast("600519", 5));
        assertThrows(ProviderContractException.class,
                () -> missingShadow.forecast("600519", 5));
        assertThrows(ProviderContractException.class,
                () -> invalidRobustness.forecast("600519", 5));
    }

    @Test
    void mapsAndValidatesVersionEightPanelModelEvidence() {
        SingleStockForecast result = clientReturning(v8Payload()).forecast("600519", 5);

        assertEquals("SHADOW", result.getPanelModel().getStatus());
        assertEquals("PANEL_CORE", result.getPanelModel().getMode());
        assertEquals(20, result.getPanelModel().getUniverseSize());
        assertEquals(0d, result.getPanelModel().getBlendWeight(), .000001d);
    }

    @Test
    void mapsAndValidatesVersionNineModelCompetition() {
        SingleStockForecast result = clientReturning(v9Payload()).forecast("600519", 5);

        assertEquals("single-stock-research-v9", result.getReportSchemaVersion());
        assertEquals(6, result.getModelCompetition().getCandidates().size());
    }

    @Test
    void rejectsVersionEightWhenShadowModelChangesFinalProbability() {
        PythonSingleStockForecastClient client = clientReturning(
                v8Payload().replace("\"finalProbability\":0.61",
                        "\"finalProbability\":0.62"));

        assertThrows(ProviderContractException.class, () -> client.forecast("600519", 5));
    }

    @Test
    void rejectsUnknownForecastSchemaVersion() {
        PythonSingleStockForecastClient client = clientReturning(
                v5Payload().replace("single-stock-research-v5", "single-stock-research-v6"));

        assertThrows(ProviderContractException.class, () -> client.forecast("600519", 5));
    }

    private static FinanceHttpResponse response(String body) {
        return new FinanceHttpResponse(200, body, Instant.parse("2026-08-07T07:00:00Z"), "hash");
    }

    private static PythonSingleStockForecastClient clientReturning(final String payload) {
        return new PythonSingleStockForecastClient("http://127.0.0.1:8000", new FinanceHttpClient() {
            @Override
            public FinanceHttpResponse get(String providerCode, URI uri, Map<String, String> headers) {
                throw new UnsupportedOperationException();
            }

            @Override
            public FinanceHttpResponse postJson(String providerCode, URI uri, String body,
                                                Map<String, String> headers) {
                return response(payload);
            }
        });
    }

    private static String v3Payload(double lower, double upper, String trialId, int binCount) {
        String metric = "{\"sampleCount\":15,\"accuracy\":0.6,\"brierScore\":0.22," +
                "\"baselineBrierScore\":0.24,\"brierSkillScore\":0.08,\"logLoss\":0.64," +
                "\"expectedCalibrationError\":0.08}";
        String interval = "{\"status\":\"AVAILABLE\",\"lower\":" + lower + ",\"upper\":" + upper +
                ",\"confidenceLevel\":0.95,\"method\":\"MOVING_BLOCK_BOOTSTRAP\",\"validIterations\":1000}";
        String development = slice("2015-01-01", "2020-01-01");
        String calibration = slice("2020-01-02", "2023-01-01");
        String locked = slice("2023-01-02", "2026-08-01");
        return "{\"instrumentCode\":\"600519.SH\",\"asOfDate\":\"2026-08-07\"," +
                "\"reportSchemaVersion\":\"single-stock-research-v3\",\"modelVersion\":\"logistic-platt-qualified-v3\"," +
                "\"horizonDays\":20,\"status\":\"ROBUST\",\"conclusion\":\"锁定测试存在增量\"," +
                "\"barCount\":1600,\"upProbability\":0.61,\"rawProbability\":0.68," +
                "\"probabilityInterval\":" + interval + ",\"dataFingerprint\":\"abcdef\"," +
                "\"sourceCode\":\"PYTDX\",\"sourceFamily\":\"TDX\",\"qualityStatus\":\"FRESH_FALLBACK\"," +
                "\"lastClose\":1505.0,\"strategyPolicy\":{\"signalThreshold\":0.6,\"holdingDays\":20," +
                "\"entryRule\":\"T+1\",\"exitRule\":\"T+20\",\"overlapPolicy\":\"不重叠\"," +
                "\"roundTripCostRate\":0.0015,\"benchmark\":\"同股买入并持有\"}," +
                "\"qualification\":{\"status\":\"QUALIFIED\",\"trial\":{\"trialId\":\"" + trialId + "\"," +
                "\"featureVersion\":\"f1\",\"labelVersion\":\"l1\",\"splitVersion\":\"s1\"," +
                "\"calibrationVersion\":\"c1\",\"bootstrapVersion\":\"b1\",\"randomSeed\":7," +
                "\"modelVersion\":\"logistic-platt-qualified-v3\"},\"splitAudit\":{\"development\":" + development +
                ",\"calibration\":" + calibration + ",\"lockedTest\":" + locked + ",\"labelHorizonDays\":20," +
                "\"independentStrideDays\":20,\"rule\":\"strict forward\"}," +
                "\"calibration\":{\"status\":\"FITTED\",\"method\":\"PLATT\",\"sampleCount\":15," +
                "\"positiveCount\":8,\"slope\":0.7,\"intercept\":0.1,\"rawLogLoss\":0.68," +
                "\"calibratedLogLoss\":0.64},\"lockedTest\":{\"baselineProbability\":0.53," +
                "\"rawMetrics\":" + metric + ",\"calibratedMetrics\":" + metric + ",\"baselineMetrics\":" + metric +
                ",\"reliabilityBins\":" + bins(binCount) + "}," +
                "\"confidenceIntervals\":{\"brierSkillScore\":" + interval + ",\"accuracy\":" + interval +
                ",\"excessReturn\":" + interval + ",\"sharpeRatio\":" + interval + "}},\"warnings\":[]}";
    }

    private static String v4Payload() {
        return v3Payload(0.45, 0.75, repeat('c', 64), 15)
                .replace("single-stock-research-v3", "single-stock-research-v4")
                .replace("logistic-platt-qualified-v3", "logistic-platt-selective-v4")
                .replace("\"horizonDays\":20", "\"horizonDays\":5")
                .replace("\"conclusion\":\"锁定测试存在增量\",",
                        "\"conclusion\":\"锁定测试存在增量\",\"decision\":\"ABSTAIN\","
                                + "\"decisionReason\":\"概率位于拒绝区间\",")
                .replace("\"labelHorizonDays\":20,\"independentStrideDays\":20",
                        "\"labelHorizonDays\":5,\"independentStrideDays\":5")
                .replace("\"warnings\":[]}",
                        "\"selectiveValidation\":{\"lowerThreshold\":0.4,"
                                + "\"upperThreshold\":0.6,\"sampleCount\":15,"
                                + "\"coveredCount\":9,\"coverage\":0.6,"
                                + "\"coveredAccuracy\":0.67,\"abstainRate\":0.4},"
                                + "\"warnings\":[]}");
    }

    private static String v5Payload() {
        return v4Payload()
                .replace("single-stock-research-v4", "single-stock-research-v5")
                .replace("logistic-platt-selective-v4", "competition-logistic-platt-v5")
                .replace("\"warnings\":[]}",
                        "\"context\":{\"market\":{\"code\":\"000300.SH\",\"label\":\"沪深300\"," +
                                "\"status\":\"AVAILABLE\",\"coverage\":1.0,\"regime\":\"UPTREND\"}," +
                                "\"industry\":{\"label\":\"行业代理指数\",\"status\":\"UNAVAILABLE\"," +
                                "\"coverage\":0.0},\"featureCodes\":[\"MOMENTUM_5\"]," +
                                "\"alignmentRule\":\"strict left join\"}," +
                                "\"modelCompetition\":{\"selectedModel\":\"LOGISTIC\"," +
                                "\"selectionEndDate\":\"2020-01-01\",\"calibrationStartDate\":\"2020-01-02\"," +
                                "\"selectionRule\":\"锁定测试不参与冠军选择\",\"candidates\":[{" +
                                "\"code\":\"LOGISTIC\",\"name\":\"逻辑回归\",\"selected\":true," +
                                "\"selectionSampleCount\":20,\"accuracy\":0.6,\"brierScore\":0.22," +
                                "\"logLoss\":0.64,\"baselineBrierScore\":0.24,\"reason\":\"最优\"}]}," +
                                "\"leakageAudit\":{\"status\":\"PASSED\",\"checkedSampleCount\":100," +
                                "\"checks\":[\"无未来数据\"]},\"qlibReference\":{\"status\":\"NOT_RUN\"," +
                                "\"role\":\"离线辅助\",\"runtimeDependency\":false},\"warnings\":[]}");
    }

    private static String v6Payload() {
        String locked = "{\"sampleCount\":15,\"accuracy\":0.6,\"brierScore\":0.22," +
                "\"baselineBrierScore\":0.24,\"brierSkillScore\":0.08,\"logLoss\":0.64," +
                "\"expectedCalibrationError\":0.08}";
        String champion = "{\"code\":\"LOGISTIC\",\"name\":\"正则化逻辑回归\"," +
                "\"selected\":true,\"selectionSampleCount\":45,\"accuracy\":0.6," +
                "\"brierScore\":0.22,\"logLoss\":0.64,\"baselineBrierScore\":0.24," +
                "\"validationFoldCount\":3,\"brierStd\":0.01,\"role\":\"CHAMPION\"," +
                "\"modelVersion\":\"competition-logistic-platt-v6\",\"rawProbability\":0.66," +
                "\"calibratedProbability\":0.61,\"shadowDecision\":\"UP\"," +
                "\"qualificationStatus\":\"QUALIFIED\",\"lockedMetrics\":" + locked +
                ",\"reason\":\"最优\"}";
        String challenger = "{\"code\":\"BOOSTED_STUMPS\",\"name\":\"轻量梯度提升树桩\"," +
                "\"selected\":false,\"selectionSampleCount\":45,\"accuracy\":0.58," +
                "\"brierScore\":0.23,\"logLoss\":0.66,\"baselineBrierScore\":0.24," +
                "\"validationFoldCount\":3,\"brierStd\":0.02,\"role\":\"CHALLENGER\"," +
                "\"modelVersion\":\"competition-boosted_stumps-platt-v6\",\"rawProbability\":0.57," +
                "\"calibratedProbability\":0.55,\"shadowDecision\":\"ABSTAIN\"," +
                "\"qualificationStatus\":\"CONDITIONAL\",\"lockedMetrics\":" + locked +
                ",\"reason\":\"对照\"}";
        String regime = challenger.replace("BOOSTED_STUMPS", "REGIME_LOGISTIC")
                .replace("轻量梯度提升树桩", "市场状态感知逻辑回归");
        String baseline = challenger.replace("BOOSTED_STUMPS", "RULE_BASELINE")
                .replace("轻量梯度提升树桩", "确定性动量规则")
                .replace("CHALLENGER", "BASELINE");
        return v5Payload()
                .replace("single-stock-research-v5", "single-stock-research-v6")
                .replace("competition-logistic-platt-v5", "competition-logistic-platt-v6")
                .replace("\"candidates\":[{\"code\":\"LOGISTIC\",\"name\":\"逻辑回归\"," +
                                "\"selected\":true,\"selectionSampleCount\":20,\"accuracy\":0.6," +
                                "\"brierScore\":0.22,\"logLoss\":0.64,\"baselineBrierScore\":0.24," +
                                "\"reason\":\"最优\"}]",
                        "\"candidates\":[" + champion + "," + challenger + "," + regime + "," + baseline + "]");
    }

    private static String v7Payload() {
        String audit = "{\"status\":\"PASS\",\"mode\":\"SHADOW\"," +
                "\"primaryEngine\":" + auditEngine("FIN_SCOPE") + "," +
                "\"shadowEngine\":" + auditEngine("BACKTESTING_PY") + "," +
                "\"tradeCountAgreement\":true,\"entryDateAgreementRate\":1.0," +
                "\"exitDateAgreementRate\":1.0,\"returnDelta\":0.0001," +
                "\"maxDrawdownDelta\":0.0002,\"sharpeDelta\":0.001," +
                "\"costDelta\":0.0,\"durationMs\":12,\"mismatches\":[]," +
                "\"limitations\":[\"影子验证不参与方向决策\"]}";
        String scenario = "{\"holdingDays\":5,\"threshold\":0.6," +
                "\"primary\":true,\"annualizedReturn\":0.12,\"excessReturn\":0.04," +
                "\"sharpeRatio\":0.9,\"maxDrawdown\":0.08,\"tradeCount\":2}";
        String stability = "{\"scenarios\":[" + scenario + "," + scenario + "," +
                scenario + "," + scenario + "," + scenario + "]," +
                "\"positiveExcessRatio\":0.8,\"worstExcessReturn\":-0.01," +
                "\"worstSharpeRatio\":0.2,\"neighborMeanExcessReturn\":0.03," +
                "\"neighborMedianExcessReturn\":0.03,\"outperformBenchmarkRatio\":0.8," +
                "\"surfaceVariance\":0.001,\"robustRegionSize\":4,\"scenarioCount\":5}";
        return v6Payload()
                .replace("single-stock-research-v6", "single-stock-research-v7")
                .replace("\"warnings\":[]}", "\"parameterStability\":" + stability +
                        ",\"backtestAudit\":" + audit + ",\"warnings\":[]}");
    }

    private static String v8Payload() {
        String panel = "{\"status\":\"SHADOW\",\"mode\":\"PANEL_CORE\"," +
                "\"artifactVersion\":\"abcdef123456\"," +
                "\"publishedAt\":\"2026-08-17T10:00:00\",\"artifactAgeDays\":0," +
                "\"universeSize\":20,\"sampleCount\":12000,\"featureCoverage\":1.0," +
                "\"featureDistance\":1.2,\"driftStatus\":\"HEALTHY\"," +
                "\"individualProbability\":0.61,\"panelProbability\":0.58," +
                "\"finalProbability\":0.61,\"blendWeight\":0.0," +
                "\"targetLockedSampleCount\":15,\"lockedBrierDelta\":0.01," +
                "\"lockedLogLossDelta\":0.01,\"panelBrierScore\":0.22," +
                "\"panelLogLoss\":0.64,\"panelEce\":0.08," +
                "\"fallbackReason\":\"未优于个股冠军\"," +
                "\"evidence\":[\"日期级前向切分\"]}";
        return v7Payload()
                .replace("single-stock-research-v7", "single-stock-research-v8")
                .replace("\"warnings\":[]}",
                        "\"panelModel\":" + panel + ",\"warnings\":[]}");
    }

    private static String v9Payload() {
        String challenger = "{\"code\":\"HISTOGRAM_GB\",\"name\":\"正则化直方图梯度提升\"," +
                "\"selected\":false,\"selectionSampleCount\":45,\"accuracy\":0.58," +
                "\"brierScore\":0.23,\"logLoss\":0.66,\"baselineBrierScore\":0.24," +
                "\"validationFoldCount\":3,\"brierStd\":0.02,\"role\":\"CHALLENGER\"," +
                "\"modelVersion\":\"competition-histogram_gb-platt-v9\"," +
                "\"rawProbability\":0.57,\"calibratedProbability\":0.55," +
                "\"shadowDecision\":\"ABSTAIN\",\"qualificationStatus\":\"CONDITIONAL\"," +
                "\"lockedMetrics\":{\"sampleCount\":15,\"accuracy\":0.6," +
                "\"brierScore\":0.22,\"baselineBrierScore\":0.24," +
                "\"brierSkillScore\":0.08,\"logLoss\":0.64," +
                "\"expectedCalibrationError\":0.08},\"reason\":\"对照\"}";
        String stacked = challenger.replace("HISTOGRAM_GB", "STACKED")
                .replace("正则化直方图梯度提升", "受约束时序集成")
                .replace("histogram_gb", "stacked");
        return v8Payload()
                .replace("single-stock-research-v8", "single-stock-research-v9")
                .replace("-platt-v6", "-platt-v9")
                .replace("}]},\"leakageAudit\"",
                        "}," + challenger + "," + stacked + "]},\"leakageAudit\"");
    }

    private static String auditEngine(String code) {
        return "{\"engine\":\"" + code + "\",\"tradeCount\":2," +
                "\"totalReturn\":0.12,\"maxDrawdown\":0.08," +
                "\"sharpeRatio\":0.9,\"totalCost\":0.003}";
    }

    private static String slice(String startDate, String endDate) {
        return "{\"startDate\":\"" + startDate + "\",\"endDate\":\"" + endDate + "\"," +
                "\"sampleCount\":300,\"independentSampleCount\":15,\"positiveCount\":8,\"purgedCount\":0}";
    }

    private static String bins(int firstCount) {
        return "[{\"lowerBound\":0,\"upperBound\":0.2,\"count\":" + firstCount +
                ",\"meanProbability\":0.1,\"observedUpRate\":0.13,\"calibrationError\":0.03}," +
                "{\"lowerBound\":0.2,\"upperBound\":0.4,\"count\":0}," +
                "{\"lowerBound\":0.4,\"upperBound\":0.6,\"count\":0}," +
                "{\"lowerBound\":0.6,\"upperBound\":0.8,\"count\":0}," +
                "{\"lowerBound\":0.8,\"upperBound\":1.0,\"count\":0}]";
    }

    private static String repeat(char value, int count) {
        StringBuilder result = new StringBuilder();
        for (int index = 0; index < count; index++) result.append(value);
        return result.toString();
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
