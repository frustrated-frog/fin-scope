package com.finscope.service.marketpulse;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.finscope.common.enums.marketpulse.*;
import com.finscope.dao.marketpulse.*;
import com.finscope.domain.marketpulse.*;
import com.finscope.domain.marketpulse.ai.*;
import com.finscope.domain.news.ai.NewsAiProviderConfig;
import com.finscope.rpc.llm.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;
import org.sqlite.SQLiteDataSource;
import java.nio.file.Path;
import java.time.*;
import java.util.*;
import java.util.concurrent.Executor;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class MarketAiTest {
    @TempDir Path directory;
    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
    private final LocalDate date = LocalDate.parse("2026-11-17");
    private MarketPulseRepository snapshots;
    private MarketAiEvidenceService evidence;
    private MarketAiRepository repository;
    private MarketAiProperties properties;
    private MarketAiAgent agent;
    private LunaResponsesClient client;
    private MarketAiInput input;

    @BeforeEach
    void setup() {
        snapshots = mock(MarketPulseRepository.class);
        var today = workspace(date);
        var previous = workspace(date.minusDays(1));
        var future = workspace(date.plusDays(1));
        when(snapshots.findWorkspace(date)).thenReturn(Optional.of(today));
        when(snapshots.findRecentWorkspaces(6, date)).thenReturn(List.of(future, today, previous));
        when(snapshots.findRecentWorkspaces(21, date)).thenReturn(List.of(future, today, previous));
        properties = new MarketAiProperties();
        evidence = new MarketAiEvidenceService();
        inject(evidence, "repository", snapshots, "mapper", mapper, "scoring", new MarketStructureScoring(), "properties", properties);
        input = evidence.assemble(today);
        SQLiteDataSource source = new SQLiteDataSource();
        source.setUrl("jdbc:sqlite:" + directory.resolve("ai.db"));
        repository = new MarketAiRepository();
        inject(repository, "jdbc", new JdbcTemplate(source), "mapper", mapper);
        repository.afterPropertiesSet();
        properties = new MarketAiProperties();
        client = mock(NewsAiLunaClient.class);
        when(client.model()).thenReturn("gpt-6-luna");
        when(client.isConfigured()).thenReturn(true);
        when(client.prepare(anyString(), anyString(), anyInt(), anyMap(), anyString())).thenReturn(new LunaResponsesClient.Prepared());
        agent = new MarketAiAgent();
        inject(agent, "client", client, "mapper", mapper, "properties", properties);
    }

    @Test
    void factsExcludeFutureAndRuleScoresAndUseStableInputIdentity() throws Exception {
        String json = mapper.writeValueAsString(input);
        assertFalse(json.contains("rotationScore"));
        assertFalse(json.contains("confidenceScore"));
        assertFalse(json.contains("固定结论"));
        assertFalse(json.contains(date.plusDays(1).toString()));
        var refreshed = workspace(date);
        refreshed.setGeneratedAt(date.atTime(18, 0));
        assertEquals(input.getFingerprint(), evidence.assemble(refreshed).getFingerprint());
        refreshed.getBreadth().setAdvanceRatio(.71);
        assertNotEquals(input.getFingerprint(), evidence.assemble(refreshed).getFingerprint());
        var real = new NewsAiLunaClient();
        inject(real, "config", new NewsAiProviderConfig(), "mapper", mapper);
        inject(agent, "client", real);
        var prepared = mapper.readTree(agent.prepare(input).getBody());
        assertEquals("low", prepared.path("reasoning").path("effort").asText());
    }

    @Test
    void incompatibleBreadthDenominatorsDoNotProduceComparableHistoricRatios() {
        var today = workspace(date);
        var previous = workspace(date.minusDays(1));
        var currentTrend = new MarketTrendBreadth();
        currentTrend.setMa20Ratio(.3182);
        currentTrend.setMa20ValidCount(66);
        currentTrend.setMa60ValidCount(66);
        today.getBreadth().setTrendBreadth(currentTrend);
        today.getBreadth().setValidCount(5577);
        var priorTrend = new MarketTrendBreadth();
        priorTrend.setMa20Ratio(.4514);
        priorTrend.setMa20ValidCount(576);
        priorTrend.setMa60ValidCount(576);
        previous.getBreadth().setTrendBreadth(priorTrend);
        when(snapshots.findRecentWorkspaces(6, date)).thenReturn(List.of(previous));
        var facts = evidence.assemble(today).getFacts();
        var old = facts.stream().filter(f -> f.getLabel().equals("样本趋势宽度") && f.getBusinessDate().equals(previous.getBusinessDate().toString())).findFirst().orElseThrow();
        assertFalse(old.getValue().contains("45.14"));
        assertTrue(old.getValue().contains("不参与跨日比较"));
    }

    @Test
    void rejectsInventedReferencesNumbersSectorsAndUnsupportedHistoricalComparison() throws Exception {
        var report = report();
        assertNotNull(agent.parse(mapper.writeValueAsString(report), input));
        report.getDrivers().getFirst().setFactIds(List.of("F9999"));
        assertThrows(IllegalArgumentException.class, () -> agent.parse(mapper.writeValueAsString(report), input));
        report.getDrivers().getFirst().setFactIds(List.of("F1"));
        report.setSummary("市场上涨了987.65%");
        assertThrows(IllegalArgumentException.class, () -> agent.parse(mapper.writeValueAsString(report), input));
        report.setSummary("参与范围正在扩大，持续性需要成交支持。");
        report.getChange().setFactIds(List.of("F1"));
        assertThrows(IllegalArgumentException.class, () -> agent.parse(mapper.writeValueAsString(report), input));
    }

    @Test
    void cachedResultSurvivesRepeatedRequestsAndReadNeverInvokesTheModel() throws Exception {
        var reply = new LunaResponsesClient.Reply();
        reply.setText(mapper.writeValueAsString(report()));
        reply.setInputTokens(100L);
        reply.setOutputTokens(200L);
        reply.setCompleted(true);
        when(client.complete(any())).thenReturn(reply);
        var service = service();
        assertEquals(MarketAiStatus.NOT_GENERATED, service.read(date).getStatus());
        verify(client, never()).complete(any());
        assertEquals(MarketAiStatus.READY, service.request(date, false).getStatus());
        assertEquals(MarketAiStatus.READY, service.request(date, false).getStatus());
        assertEquals(100L, service.read(date).getInputTokens());
        verify(client, times(1)).complete(any());
        properties.setEnabled(false);
        assertEquals(MarketAiStatus.DISABLED, service.request(date, false).getStatus());
        assertTrue(service.read(date).getStructure().stream().anyMatch(row -> row.getBaselineScore() != null));
        verify(client, times(1)).complete(any());
    }

    @Test
    void unknownRequestIsNotAutomaticallyRepeatedAndPreservesRuleWorkflow() throws Exception {
        when(client.complete(any())).thenThrow(new java.net.http.HttpTimeoutException("timeout"));
        var service = service();
        assertEquals(MarketAiStatus.UNKNOWN, service.request(date, false).getStatus());
        assertEquals(MarketAiStatus.UNKNOWN, service.request(date, true).getStatus());
        verify(client, times(1)).complete(any());
        assertNotNull(snapshots.findWorkspace(date).orElseThrow().getRegime());
    }

    @Test
    void restartRecoversUnsentJobsButNeverResendsDispatchedOnes() {
        var view = new MarketAiView();
        view.setBusinessDate(date);
        view.setStatus(MarketAiStatus.QUEUED);
        var now = date.atTime(18, 0);
        assertTrue(repository.claim("queued", "one", view, now));
        assertFalse(repository.claim("queued", "two", view, now));
        assertTrue(repository.claim("running", "one", view, now));
        view.setStatus(MarketAiStatus.RUNNING);
        assertTrue(repository.markSent("running", "one", view, now));
        assertFalse(repository.markSent("running", "two", view, now));
        repository.recover(now.plusMinutes(5));
        assertTrue(repository.find("queued").isEmpty());
        assertEquals(MarketAiStatus.UNKNOWN, repository.find("running").orElseThrow().getStatus());
    }

    @Test
    void retryKeepsThePreviousAttemptAndItsUsage() {
        var view = new MarketAiView();
        view.setBusinessDate(date);
        view.setStatus(MarketAiStatus.QUEUED);
        var now = date.atTime(18, 0);
        assertTrue(repository.claim("key", "first", view, now));
        view.setStatus(MarketAiStatus.FAILED);
        view.setInputTokens(100L);
        view.setOutputTokens(200L);
        repository.finish("key", "first", view, now);
        repository.retryFailed("key");
        assertTrue(repository.claim("key", "second", view, now));
        var jdbc = (JdbcTemplate) ReflectionTestUtils.getField(repository, "jdbc");
        assertEquals(2, jdbc.queryForObject("SELECT COUNT(*) FROM market_ai_attempt", Integer.class));
        assertEquals(200L, jdbc.queryForObject("SELECT output_tokens FROM market_ai_attempt WHERE owner='first'", Long.class));
    }

    @Test
    void reviewUsesOnlyScenarioCreatedBeforeTheNextTradingDate() {
        var now = date.atTime(18, 0);
        var view = new MarketAiView();
        view.setBusinessDate(date);
        view.setStatus(MarketAiStatus.READY);
        view.setReport(report());
        view.setGeneratedAt(now);
        repository.claim("before", "one", view, now);
        repository.finish("before", "one", view, now);
        view.setGeneratedAt(date.plusDays(1).atTime(16, 0));
        repository.claim("after", "two", view, view.getGeneratedAt());
        repository.finish("after", "two", view, view.getGeneratedAt());
        assertEquals(now, repository.findReviewSource(date.toString(), date.plusDays(1).atStartOfDay()).orElseThrow().getGeneratedAt());
        assertTrue(repository.findReviewSource(date.minusDays(1).toString(), date.atStartOfDay()).isEmpty());
    }

    @Test
    void chineseDeclineMagnitudesKeepTheSameMeaningAsSignedFacts() throws Exception {
        var fact = new MarketAiFact();
        fact.setId("Fdecline");
        fact.setBusinessDate(date.toString());
        fact.setLabel("指数");
        fact.setValue("上证 -0.50%；创业板 -1.20%");
        input.getFacts().add(fact);
        var report = report();
        report.getDrivers().getFirst().setDetail("指数分别下跌0.50%、1.20%。");
        report.getDrivers().getFirst().setFactIds(List.of("Fdecline"));
        assertNotNull(agent.parse(mapper.writeValueAsString(report), input));
        report.getDrivers().getFirst().setDetail("指数上涨0.50%。");
        assertThrows(IllegalArgumentException.class, () -> agent.parse(mapper.writeValueAsString(report), input));
    }

    @Test
    void schemaUsesOnlyFrozenMetricsDimensionsReferencesAndBoundsWithoutRepeatingFactCatalog() throws Exception {
        var real = new NewsAiLunaClient();
        inject(real, "config", new NewsAiProviderConfig(), "mapper", mapper);
        inject(agent, "client", real);
        input.setAdjustmentLimit(7);
        input.getStructure().getFirst().setBaselineScore(null);
        input.getObservations().put(MarketScenarioMetric.MA20_RATIO, null);
        var request = mapper.readTree(agent.prepare(input).getBody());
        var schema = request.path("text").path("format").path("schema");
        var metrics = schema.path("$defs").path("condition").path("properties").path("metric").path("enum");
        assertFalse(metrics.toString().contains("MA20_RATIO"));
        assertTrue(metrics.toString().contains("ADVANCE_RATIO"));
        var adjustments = schema.path("properties").path("adjustments");
        long expected = input.getStructure().stream().filter(row -> row.getBaselineScore() != null).count();
        assertEquals(expected, adjustments.path("minItems").asLong());
        assertEquals(expected, adjustments.path("maxItems").asLong());
        assertEquals(7, adjustments.path("items").path("properties").path("delta").path("maximum").asInt());
        assertEquals(input.getFacts().size(), schema.path("$defs").path("factId").path("enum").size());
        assertEquals("#/$defs/point", schema.path("properties").path("change").path("$ref").asText());
        assertEquals(1, schema.toString().split("\"F1\"", -1).length - 1);
        input.getSectorNames().clear();
        input.getStructure().forEach(row -> row.setBaselineScore(null));
        schema = mapper.readTree(agent.prepare(input).getBody()).path("text").path("format").path("schema");
        assertEquals(0, schema.path("properties").path("sectors").path("maxItems").asInt());
        assertEquals(0, schema.path("properties").path("adjustments").path("maxItems").asInt());
        assertFalse(schema.toString().contains("\"enum\":[]"));
    }

    @Test
    void validationRetainsTheExactSafeFieldPath() throws Exception {
        var report = report();
        report.getScenarios().getFirst().getConfirmation().setMetric(MarketScenarioMetric.MA20_RATIO);
        var invalid = assertThrows(MarketAiValidationException.class, () -> agent.parse(mapper.writeValueAsString(report), input));
        assertEquals(MarketAiFailureCode.INVALID_FIELD, invalid.getCode());
        assertEquals("$.scenarios[0].confirmation.metric", invalid.getPath());
        report.getDrivers().getFirst().setDetail("无法证实987.65%");
        invalid = assertThrows(MarketAiValidationException.class, () -> agent.parse(mapper.writeValueAsString(report), input));
        assertEquals(MarketAiFailureCode.UNSUPPORTED_NUMBER, invalid.getCode());
        assertEquals("$.drivers[0].detail", invalid.getPath());
        assertFalse(invalid.getMessage().contains("987.65"));
        assertNull(invalid.getCause());
        assertThrows(MarketAiValidationException.class, () -> agent.parse("null", input));
        assertThrows(MarketAiValidationException.class, () -> agent.parse(mapper.writeValueAsString(report()) + " {}", input));
        String fractional = mapper.writeValueAsString(report()).replace("\"delta\":0", "\"delta\":0.5");
        assertThrows(MarketAiValidationException.class, () -> agent.parse(fractional, input));
    }

    @Test
    void failedOutputAndUsageAreStoredBeforeValidationAndNotAutomaticallyResent() throws Exception {
        var report = report();
        report.getScenarios().getFirst().getConfirmation().setMetric(MarketScenarioMetric.MA20_RATIO);
        var reply = reply(mapper.writeValueAsString(report), true);
        when(client.complete(any())).thenReturn(reply);
        var service = service();
        assertEquals(MarketAiStatus.FAILED, service.request(date, false).getStatus());
        var artifact = artifact();
        assertEquals(reply.getText(), artifact.getResponseText());
        assertEquals(input.getFingerprint(), artifact.getInput().getFingerprint());
        assertEquals(MarketAiFailureCode.INVALID_FIELD, artifact.getFailure().getCode());
        assertEquals("$.scenarios[0].confirmation.metric", artifact.getFailure().getPath());
        assertEquals(200L, artifact.getOutputTokens());
        repository.afterPropertiesSet();
        assertEquals(reply.getText(), artifact().getResponseText());
        assertEquals(MarketAiStatus.FAILED, service.read(date).getStatus());
        assertEquals(MarketAiStatus.FAILED, service.request(date, false).getStatus());
        assertEquals(MarketAiStatus.FAILED, service.revalidate(date).getStatus());
        verify(client, times(1)).complete(any());
    }

    @Test
    void explicitRetryReplaysValidPaidOutputWithoutAnotherCallOrLosingTheOriginalAttempt() throws Exception {
        var reply = reply(mapper.writeValueAsString(report()), true);
        when(client.complete(any())).thenReturn(reply);
        agent = spy(agent);
        doThrow(new MarketAiValidationException(MarketAiFailureCode.INVALID_FIELD, "$.scenarios[0].confirmation.metric"))
                .doCallRealMethod().when(agent).parse(anyString(), any());
        var service = service();
        assertEquals(MarketAiStatus.FAILED, service.request(date, false).getStatus());
        var saved = artifact();
        var restored = service.request(date, true);
        assertEquals(MarketAiStatus.READY, restored.getStatus());
        assertEquals(saved.getRespondedAt(), restored.getGeneratedAt());
        assertEquals(saved.getOwner(), artifact().getOwner());
        assertEquals(200L, restored.getOutputTokens());
        assertEquals(MarketAiFailureCode.INVALID_FIELD, artifact().getFailure().getCode());
        assertEquals(1, jdbc().queryForObject("SELECT COUNT(*) FROM market_ai_attempt", Integer.class));
        assertEquals(MarketAiStatus.READY, service.request(date, true).getStatus());
        verify(client, times(1)).complete(any());
    }

    @Test
    void incompleteOrOversizeOutputsKeepUsageAndCannotBeReplayedAsComplete() throws Exception {
        when(client.complete(any())).thenReturn(reply(mapper.writeValueAsString(report()), false));
        var service = service();
        assertEquals(MarketAiStatus.FAILED, service.request(date, false).getStatus());
        assertEquals(MarketAiFailureCode.INCOMPLETE_RESPONSE, artifact().getFailure().getCode());
        assertFalse(artifact().isResponseCompleted());
        assertEquals(200L, artifact().getOutputTokens());
        assertEquals(MarketAiStatus.FAILED, service.revalidate(date).getStatus());
        when(client.complete(any())).thenReturn(reply("x".repeat(65537), true));
        assertEquals(MarketAiStatus.FAILED, service.request(date, true).getStatus());
        assertEquals(65536, artifact().getResponseText().length());
        assertTrue(artifact().isResponseTruncated());
        assertEquals(MarketAiFailureCode.OUTPUT_TOO_LARGE, artifact().getFailure().getCode());
        assertEquals(MarketAiStatus.FAILED, service.revalidate(date).getStatus());
        assertEquals(2, jdbc().queryForObject("SELECT COUNT(*) FROM market_ai_attempt", Integer.class));
        assertEquals(2, jdbc().queryForObject("SELECT COUNT(*) FROM market_ai_artifact", Integer.class));
        verify(client, times(2)).complete(any());
    }

    @Test
    void replyCheckpointCanRecoverAfterRestartAndRejectsStaleOwners() throws Exception {
        var view = new MarketAiView();
        view.setBusinessDate(date);
        view.setStatus(MarketAiStatus.QUEUED);
        view.setInputTokens(100L);
        view.setOutputTokens(200L);
        String key = MarketAiAgent.VERSION + ":gpt-6-luna:low:" + input.getFingerprint();
        var now = date.atTime(18, 0);
        assertTrue(repository.claim(key, "first", view, now));
        repository.saveInput(key, "first", input, now);
        view.setStatus(MarketAiStatus.RUNNING);
        assertTrue(repository.markSent(key, "first", view, now));
        repository.saveReply(key, "first", view, mapper.writeValueAsString(report()), true, now);
        repository.recover(now.plusMinutes(5));
        assertEquals(MarketAiStatus.READY, service().revalidate(date).getStatus());
        verify(client, never()).complete(any());
        view.setStatus(MarketAiStatus.FAILED);
        repository.finish(key, "first", view, now);
        repository.retryFailed(key);
        view.setStatus(MarketAiStatus.QUEUED);
        assertTrue(repository.claim(key, "second", view, now));
        repository.saveInput(key, "second", input, now);
        assertThrows(IllegalStateException.class, () -> repository.saveReply(key, "first", view, "stale", true, now));
        assertNull(repository.findArtifact(key).orElseThrow().getResponseText());
        assertEquals("second", repository.findArtifact(key).orElseThrow().getOwner());
    }

    private LunaResponsesClient.Reply reply(String text, boolean completed) {
        var reply = new LunaResponsesClient.Reply();
        reply.setText(text);
        reply.setInputTokens(100L);
        reply.setOutputTokens(200L);
        reply.setCompleted(completed);
        return reply;
    }
    private JdbcTemplate jdbc() {
        return (JdbcTemplate) ReflectionTestUtils.getField(repository, "jdbc");
    }
    private MarketAiArtifact artifact() {
        String key = jdbc().queryForObject("SELECT cache_key FROM market_ai_review", String.class);
        return repository.findArtifact(key).orElseThrow();
    }

    private MarketAiService service() {
        var service = new MarketAiService();
        inject(service, "repository", repository, "snapshots", snapshots, "evidence", evidence, "agent", agent,
                "client", client, "properties", properties, "executor", (Executor) Runnable::run);
        return service;
    }
    private MarketAiReport report() {
        var report = new MarketAiReport();
        report.setHeadline("参与度改善，仍需观察量能");
        report.setSummary("参与范围正在扩大，持续性需要成交支持。");
        report.setStateLabel("结构修复");
        var change = point();
        change.setFactIds(List.of("F1", input.getFacts().stream().filter(f -> f.getBusinessDate().equals(date.minusDays(1).toString())).findFirst().orElseThrow().getId()));
        report.setChange(change);
        report.setDrivers(List.of(point(), point()));
        report.setContradictions(List.of(point()));
        report.setConditions(List.of(point(), point()));
        report.setAdjustments(input.getStructure().stream().filter(row -> row.getBaselineScore() != null).map(row -> {
            var adjustment = new MarketScoreAdjustment();
            adjustment.setDimension(row.getDimension());
            adjustment.setDelta(0);
            adjustment.setReason("保留基准，观察持续性");
            adjustment.setCounterEvidence("参与范围仍需验证");
            adjustment.setFactIds(List.of("F1"));
            return adjustment;
        }).toList());
        report.setScenarios(List.of(scenario("修复延续"), scenario("局部分化")));
        return report;
    }
    private MarketAiScenario scenario(String title) {
        var scenario = new MarketAiScenario();
        var thesis = point();
        thesis.setTitle(title);
        scenario.setThesis(thesis);
        var confirm = new MarketScenarioCondition();
        confirm.setMetric(MarketScenarioMetric.ADVANCE_RATIO);
        confirm.setDirection(MarketScenarioDirection.RISE);
        confirm.setExplanation(point());
        scenario.setConfirmation(confirm);
        var invalidate = new MarketScenarioCondition();
        invalidate.setMetric(MarketScenarioMetric.ADVANCE_RATIO);
        invalidate.setDirection(MarketScenarioDirection.FALL);
        invalidate.setExplanation(point());
        scenario.setInvalidation(invalidate);
        return scenario;
    }
    @Test
    void rejectsUnboundedDuplicateOrUnavailableAdjustmentsAndUnverifiableScenarios() throws Exception {
        var report = report();
        report.getAdjustments().getFirst().setDelta(16);
        assertThrows(IllegalArgumentException.class, () -> agent.parse(mapper.writeValueAsString(report), input));
        report.getAdjustments().getFirst().setDelta(Integer.MIN_VALUE);
        assertThrows(IllegalArgumentException.class, () -> agent.parse(mapper.writeValueAsString(report), input));
        var duplicate = report();
        duplicate.getAdjustments().getLast().setDimension(duplicate.getAdjustments().getFirst().getDimension());
        assertThrows(IllegalArgumentException.class, () -> agent.parse(mapper.writeValueAsString(duplicate), input));
        var unsupported = report();
        unsupported.getScenarios().getFirst().getConfirmation().setMetric(MarketScenarioMetric.MA20_RATIO);
        assertThrows(IllegalArgumentException.class, () -> agent.parse(mapper.writeValueAsString(unsupported), input));
        var adjusted = report();
        adjusted.getAdjustments().getFirst().setDelta(-8);
        assertEquals(-8, agent.parse(mapper.writeValueAsString(adjusted), input).getAdjustments().getFirst().getDelta());
    }
    private MarketAiPoint point() {
        var point = new MarketAiPoint();
        point.setTitle("参与变化");
        point.setDetail("上涨参与需要继续观察。");
        point.setFactIds(List.of("F1"));
        return point;
    }
    private MarketPulseWorkspace workspace(LocalDate date) {
        var workspace = new MarketPulseWorkspace();
        workspace.setBusinessDate(date);
        workspace.setGeneratedAt(date.atTime(16, 0));
        var breadth = new MarketBreadthSnapshot();
        breadth.setBusinessDate(date);
        breadth.setAdvanceRatio(.63);
        breadth.setValidCount(5000);
        var pressure = new MarketVolumePressure();
        pressure.setAdvanceAmountRatio(.6);
        breadth.setVolumePressure(pressure);
        breadth.setTotalAmount(2.2e12);
        workspace.setBreadth(breadth);
        var regime = new MarketRegimeSnapshot();
        regime.setConfidenceScore(84);
        regime.setExplanation("固定结论");
        workspace.setRegime(regime);
        var sector = new SectorRotationItem();
        sector.setSectorCode("tech");
        sector.setSectorName("电子");
        sector.setRotationScore(85);
        sector.setReturn5d(3.5);
        workspace.setSectors(List.of(sector));
        return workspace;
    }
    private void inject(Object target, Object... fields) {
        for (int i = 0; i < fields.length; i += 2) {
            ReflectionTestUtils.setField(target, (String) fields[i], fields[i + 1]);
        }
    }
}
