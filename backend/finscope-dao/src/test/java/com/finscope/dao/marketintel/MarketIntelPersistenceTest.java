package com.finscope.dao.marketintel;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.finscope.dao.agent.AgentTraceSchemaMigrator;
import com.finscope.domain.marketintel.CapitalBehaviorSnapshot;
import com.finscope.domain.marketintel.CapitalBehaviorEvaluation;
import com.finscope.domain.marketintel.CapitalFlowPoint;
import com.finscope.domain.marketintel.CapitalEvidenceRef;
import com.finscope.domain.marketintel.CapitalInterpretation;
import com.finscope.domain.marketintel.CapitalInterpretationObservation;
import com.finscope.domain.marketintel.CapitalSignalEvaluation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sqlite.SQLiteDataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class MarketIntelPersistenceTest {
    @TempDir Path tempDir;
    private JdbcTemplate jdbc;
    private CapitalFlowRepository flows;
    private CapitalBehaviorSnapshotRepository snapshots;
    private CapitalBehaviorEvaluationRepository evaluations;
    private MarketIntelSchemaMigrator migrator;

    @BeforeEach
    void setUp() {
        SQLiteDataSource dataSource = new SQLiteDataSource();
        dataSource.setUrl("jdbc:sqlite:" + tempDir.resolve("market-intel.db") + "?foreign_keys=on");
        jdbc = new JdbcTemplate(dataSource);
        jdbc.execute("CREATE TABLE instrument(id INTEGER PRIMARY KEY,code TEXT,type TEXT,name TEXT)");
        jdbc.execute("CREATE TABLE agent_run(id INTEGER PRIMARY KEY)");
        jdbc.update("INSERT INTO instrument VALUES(7,'600519','STOCK','贵州茅台')");
        migrator = new MarketIntelSchemaMigrator(jdbc,
                new DataSourceTransactionManager(dataSource));
        migrator.migrate();
        migrator.migrate();
        new AgentTraceSchemaMigrator(jdbc, new DataSourceTransactionManager(dataSource)).migrate();
        flows = new CapitalFlowRepository(jdbc);
        ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
        snapshots = new CapitalBehaviorSnapshotRepository(jdbc, mapper);
        evaluations = new CapitalBehaviorEvaluationRepository(jdbc, mapper);
    }

    @Test
    void migrationIsIdempotentAndFlowPayloadIsDeduplicated() {
        assertEquals(1, count("SELECT COUNT(*) FROM schema_migration WHERE version=100"));
        assertEquals(1, count("SELECT COUNT(*) FROM schema_migration WHERE version=101"));
        assertEquals(1, count("SELECT COUNT(*) FROM schema_migration WHERE version=102"));
        assertEquals(1, count("SELECT COUNT(*) FROM schema_migration WHERE version=103"));
        assertEquals(1, count("SELECT COUNT(*) FROM schema_migration WHERE version=104"));
        assertEquals(1, count("SELECT COUNT(*) FROM schema_migration WHERE version=105"));
        assertEquals(1, count("SELECT COUNT(*) FROM pragma_table_info('agent_run') WHERE name='subject_type'"));
        assertEquals(1, tableCount("market_capital_flow_snapshot"));
        assertEquals(1, tableCount("market_capital_behavior_snapshot"));
        assertEquals(1, tableCount("market_capital_interpretation"));
        assertEquals(1, tableCount("market_intel_refresh_run"));
        assertEquals(1, tableCount("market_intel_refresh_step"));
        assertEquals(1, tableCount("market_capital_behavior_evaluation"));

        CapitalFlowPoint point = point("payload-1");
        flows.saveAll(Collections.singletonList(point));
        flows.saveAll(Collections.singletonList(point("payload-1")));
        assertEquals(1, flows.findRange(7L, point.getObservedAt().minusMinutes(1),
                point.getObservedAt().plusMinutes(1)).size());
        assertNotNull(point.getId());
    }

    @Test
    void snapshotRoundTripsStructuredFacts() {
        CapitalFlowPoint point = point("payload-2");
        flows.saveAll(Collections.singletonList(point));
        CapitalBehaviorSnapshot snapshot = CapitalBehaviorSnapshot.of(7L, point.getObservedAt(),
                Collections.singletonList(point), Collections.emptyList(), "fingerprint-1");
        snapshots.save(snapshot);

        CapitalBehaviorSnapshot restored = snapshots.findLatest(7L).orElseThrow(AssertionError::new);
        assertEquals(new BigDecimal("18000000"), restored.getFacts().get(0).getMainNetInflow());
        assertEquals("fingerprint-1", restored.getFingerprint());
    }

    @Test
    void evaluationRoundTripsAndIsIdempotentForTheSameInputFingerprint() {
        CapitalFlowPoint point = point("payload-evaluation");
        flows.saveAll(Collections.singletonList(point));
        CapitalBehaviorSnapshot snapshot = CapitalBehaviorSnapshot.of(7L, point.getObservedAt(),
                Collections.singletonList(point), Collections.emptyList(), "snapshot-evaluation");
        snapshots.save(snapshot);
        CapitalSignalEvaluation signal = new CapitalSignalEvaluation();
        signal.setSignalType("AMOUNT_EXPANSION_WITH_INFLOW");
        signal.setSignalLabel("放量净流入");
        signal.setHorizonDays(3);
        signal.setSampleCount(6);
        signal.setAverageReturn(new BigDecimal("0.031200"));
        signal.setMedianReturn(new BigDecimal("0.025000"));
        signal.setPositiveRate(new BigDecimal("0.666667"));
        signal.setAverageMfe(new BigDecimal("0.048000"));
        signal.setAverageMae(new BigDecimal("-0.017000"));
        signal.setStabilityStatus("CONSISTENT");
        signal.setEvaluationStatus("EXPLORATORY");
        signal.setLastEventDate(LocalDate.of(2026, 7, 10));
        CapitalBehaviorEvaluation value = CapitalBehaviorEvaluation.of(7L, snapshot.getId(),
                point.getObservedAt(), LocalDate.of(2026, 6, 18), LocalDate.of(2026, 7, 14),
                "capital-factor-v1", "capital-signal-v2", "evaluation-input-fp", "AVAILABLE",
                20, 6, new BigDecimal("0.750000"), new BigDecimal("0.250000"),
                Collections.singletonList(signal), Collections.singletonList("仅为历史统计参考"));

        evaluations.save(value);
        evaluations.save(value);

        CapitalBehaviorEvaluation restored = evaluations.findBySnapshotId(snapshot.getId())
                .orElseThrow(AssertionError::new);
        assertEquals(1, count("SELECT COUNT(*) FROM market_capital_behavior_evaluation"));
        assertEquals("evaluation-input-fp", restored.getInputFingerprint());
        assertEquals(new BigDecimal("0.031200"), restored.getSignals().get(0).getAverageReturn());
        assertEquals("仅为历史统计参考", restored.getDataGaps().get(0));
    }

    @Test
    void interpretationRoundTripsAgentV2EvidenceAndGateMetadata() {
        CapitalFlowPoint point = point("payload-agent-v2");
        flows.saveAll(Collections.singletonList(point));
        CapitalBehaviorSnapshot snapshot = CapitalBehaviorSnapshot.of(7L, point.getObservedAt(),
                Collections.singletonList(point), Collections.emptyList(), "fingerprint-agent-v2");
        snapshots.save(snapshot);

        CapitalInterpretationObservation observation = new CapitalInterpretationObservation();
        observation.setDimension("FLOW");
        observation.setClaim("主力净额保持为正");
        observation.setFactorRefs(Collections.singletonList("factor:MAIN_FLOW_SHARE:2026-07-14T10:30"));
        observation.setMetricRefs(Collections.singletonList(point.metricRef("mainNetInflow")));
        CapitalInterpretation value = new CapitalInterpretation();
        value.setInstrumentId(7L);
        value.setSnapshotId(snapshot.getId());
        value.setInterpretationType("AGENT");
        value.setStatus("SUCCEEDED");
        value.setPlainSummary("资金偏强但仍需确认");
        value.setFacts(Collections.singletonList("主力资金净额为正"));
        value.setHypotheses(Collections.emptyList());
        value.setDataGaps(Collections.singletonList("缺少 Level-2"));
        value.setObservationPoints(Collections.emptyList());
        value.setInputHash("input-agent-v2");
        value.setMarketState("MIXED");
        value.setExecutiveSummary("资金偏强但仍需确认");
        value.setObservations(Collections.singletonList(observation));
        value.setCounterEvidence(Collections.singletonList("量能未持续放大"));
        value.setWatchConditionRefs(Collections.singletonList("watch:MAIN_FLOW_SHARE"));
        value.setConfidence("MID");
        value.setFactorVersion("capital-factor-v1");
        value.setSignalVersion("capital-signal-v2");
        value.setEvidenceRefs(Collections.singletonList(new CapitalEvidenceRef(
                point.metricRef("mainNetInflow"), "主力净额", "FLOW",
                point.getMainNetInflow(), "元", point.getObservedAt())));
        value.setRejectedOutputCount(1);
        value.setRejectionReasons(Collections.singletonList("观察项引用未知因子"));

        CapitalInterpretationRepository repository = new CapitalInterpretationRepository(
                jdbc, new ObjectMapper().findAndRegisterModules());
        repository.save(value);
        CapitalInterpretation restored = repository.findById(value.getId()).orElseThrow(AssertionError::new);

        assertEquals("MIXED", restored.getMarketState());
        assertEquals("FLOW", restored.getObservations().get(0).getDimension());
        assertEquals("主力净额", restored.getEvidenceRefs().get(0).getLabel());
        assertEquals("capital-factor-v1", restored.getFactorVersion());
        assertEquals(1, restored.getRejectedOutputCount());
        assertEquals(Collections.singletonList("观察项引用未知因子"), restored.getRejectionReasons());
    }

    @Test
    void latestSnapshotUsesRefreshCreationTimeAndPreservesWarnings() {
        CapitalFlowPoint futureMarketPoint = point("future-market-time");
        futureMarketPoint.setObservedAt(LocalDateTime.of(2026, 7, 14, 15, 0));
        CapitalBehaviorSnapshot olderRefresh = CapitalBehaviorSnapshot.of(7L, futureMarketPoint.getObservedAt(),
                Collections.singletonList(futureMarketPoint), Collections.emptyList(), "older-refresh");
        olderRefresh.setCreatedAt(LocalDateTime.of(2026, 7, 14, 10, 0));
        snapshots.save(olderRefresh);

        CapitalFlowPoint currentMarketPoint = point("current-market-time");
        currentMarketPoint.setObservedAt(LocalDateTime.of(2026, 7, 14, 10, 30));
        CapitalBehaviorSnapshot newerRefresh = CapitalBehaviorSnapshot.of(7L, currentMarketPoint.getObservedAt(),
                Collections.singletonList(currentMarketPoint), Collections.emptyList(), "newer-refresh");
        newerRefresh.setCreatedAt(LocalDateTime.of(2026, 7, 14, 10, 31));
        newerRefresh.setQualityStatus("PARTIAL");
        newerRefresh.setWarnings(Arrays.asList("实时行情接口暂不可用", "换手率尚未补齐"));
        snapshots.save(newerRefresh);

        CapitalBehaviorSnapshot restored = snapshots.findLatest(7L).orElseThrow(AssertionError::new);
        assertEquals("newer-refresh", restored.getFingerprint());
        assertEquals("PARTIAL", restored.getQualityStatus());
        assertEquals(Arrays.asList("实时行情接口暂不可用", "换手率尚未补齐"), restored.getWarnings());
    }

    @Test
    void preservesRecomputedFactsWhenCalculationVersionChanges() {
        CapitalFlowPoint first = point("same-payload");
        first.setCalculationVersion("eastmoney-v1");
        CapitalFlowPoint recomputed = point("same-payload");
        recomputed.setCalculationVersion("eastmoney-v2");
        recomputed.setTradeVolume(new BigDecimal("81000"));

        flows.saveAll(Collections.singletonList(first));
        flows.saveAll(Collections.singletonList(recomputed));

        assertEquals(2, flows.findRange(7L, first.getObservedAt().minusMinutes(1),
                first.getObservedAt().plusMinutes(1)).size());
        assertNotNull(recomputed.getId());
    }

    @Test
    void upgradesExistingVersion100DatabaseWithoutLosingHistoricalFacts() {
        CapitalFlowPoint historical = point("same-payload");
        flows.saveAll(Collections.singletonList(historical));
        jdbc.update("DELETE FROM schema_migration WHERE version=102");
        jdbc.execute("DROP INDEX idx_capital_flow_identity");
        jdbc.execute("CREATE UNIQUE INDEX idx_capital_flow_identity ON market_capital_flow_snapshot(" +
                "instrument_id,provider_code,granularity,observed_at,payload_hash)");

        migrator.migrate();

        assertEquals(1, count("SELECT COUNT(*) FROM schema_migration WHERE version=102"));
        assertEquals(1, flows.findRange(7L, historical.getObservedAt().minusMinutes(1),
                historical.getObservedAt().plusMinutes(1)).size());
        CapitalFlowPoint recomputed = point("same-payload");
        recomputed.setCalculationVersion("eastmoney-v2");
        flows.saveAll(Collections.singletonList(recomputed));
        assertEquals(2, flows.findRange(7L, historical.getObservedAt().minusMinutes(1),
                historical.getObservedAt().plusMinutes(1)).size());
    }

    private CapitalFlowPoint point(String hash) {
        CapitalFlowPoint point = new CapitalFlowPoint();
        point.setInstrumentId(7L);
        point.setProviderCode("EASTMONEY");
        point.setGranularity("MINUTE_1");
        point.setDataDate(LocalDate.of(2026, 7, 14));
        point.setObservedAt(LocalDateTime.of(2026, 7, 14, 10, 30));
        point.setPrice(new BigDecimal("1480.50"));
        point.setIntervalTradeAmount(new BigDecimal("120000000"));
        point.setMainNetInflow(new BigDecimal("18000000"));
        point.setCalculationVersion("eastmoney-v1");
        point.setRetrievedAt(LocalDateTime.of(2026, 7, 14, 10, 31));
        point.setPayloadHash(hash);
        point.setQualityStatus("COMPLETE");
        return point;
    }

    private int tableCount(String table) {
        return count("SELECT COUNT(*) FROM sqlite_master WHERE type='table' AND name='" + table + "'");
    }
    private int count(String sql) {
        Integer value = jdbc.queryForObject(sql, Integer.class);
        return value == null ? 0 : value;
    }
}
