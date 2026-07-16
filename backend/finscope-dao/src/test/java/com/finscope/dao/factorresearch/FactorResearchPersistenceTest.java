package com.finscope.dao.factorresearch;

import com.finscope.dao.config.DatabaseInitializer;
import com.finscope.dao.quant.QuantDatasetRepository;
import com.finscope.domain.quant.data.QuantCapitalFlowDaily;
import com.finscope.domain.quant.data.QuantDataset;
import com.finscope.domain.quant.data.QuantDatasetPartition;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.sqlite.SQLiteDataSource;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FactorResearchPersistenceTest {
    @TempDir
    Path tempDir;

    private JdbcTemplate jdbc;
    private DataSourceTransactionManager transactionManager;
    private FactorResearchSchemaMigrator migrator;
    private QuantDatasetRepository datasets;
    private QuantCapitalFlowRepository capitalFlows;
    private QuantDatasetPartitionRepository partitions;

    @BeforeEach
    void setUp() throws Exception {
        SQLiteDataSource dataSource = dataSource(tempDir.resolve("factor-research.db"));
        jdbc = new JdbcTemplate(dataSource);
        transactionManager = new DataSourceTransactionManager(dataSource);

        DatabaseInitializer initializer = new DatabaseInitializer();
        ReflectionTestUtils.setField(initializer, "jdbcTemplate", jdbc);
        ReflectionTestUtils.setField(initializer, "dataRoot", tempDir.toString());
        initializer.afterPropertiesSet();

        migrator = new FactorResearchSchemaMigrator(
                jdbc, transactionManager);
        migrator.migrate();

        datasets = new QuantDatasetRepository();
        capitalFlows = new QuantCapitalFlowRepository(jdbc, transactionManager);
        partitions = new QuantDatasetPartitionRepository();
        ReflectionTestUtils.setField(datasets, "jdbcTemplate", jdbc);
        ReflectionTestUtils.setField(partitions, "jdbcTemplate", jdbc);
    }

    @Test
    void migrationIsIdempotentAndRepairsMissingLedgerRows() {
        migrator.migrate();

        assertEquals(1, count("SELECT COUNT(*) FROM schema_migration WHERE version=200"));
        assertEquals(1, count("SELECT COUNT(*) FROM schema_migration WHERE version=201"));
        assertEquals(1, count("SELECT COUNT(*) FROM schema_migration WHERE version=202"));
        assertEquals(1, count("SELECT COUNT(*) FROM schema_migration WHERE version=203"));
        assertEquals(1, objectCount("table", "quant_capital_flow_daily"));
        assertEquals(1, objectCount("table", "quant_dataset_partition"));
        assertEquals(1, objectCount("index", "idx_quant_capital_flow_code_date"));
        assertEquals(1, objectCount("index", "idx_quant_capital_flow_date"));
        assertEquals(1, objectCount("table", "factor_research_draft"));
        assertEquals(1, objectCount("index", "idx_factor_research_draft_created"));
        assertEquals(1, objectCount("table", "factor_research_agent_run"));
        assertEquals(1, objectCount("index", "idx_factor_research_agent_run_created"));

        jdbc.update("DELETE FROM schema_migration WHERE version IN (200,201,202,203)");
        migrator.migrate();

        assertEquals(1, count("SELECT COUNT(*) FROM schema_migration WHERE version=200"));
        assertEquals(1, count("SELECT COUNT(*) FROM schema_migration WHERE version=201"));
        assertEquals(1, count("SELECT COUNT(*) FROM schema_migration WHERE version=202"));
        assertEquals(1, count("SELECT COUNT(*) FROM schema_migration WHERE version=203"));
    }

    @Test
    void persistsDatasetAndCapitalFlowWithoutDecimalPrecisionLoss() {
        QuantDataset savedDataset = datasets.save(dataset("REAL"));
        QuantCapitalFlowDaily expected = capitalFlow(savedDataset.getId());

        capitalFlows.saveAll(Collections.singletonList(expected));

        QuantDataset restoredDataset = datasets.findById(savedDataset.getId())
                .orElseThrow(AssertionError::new);
        QuantCapitalFlowDaily restored = capitalFlows.findByDatasetId(savedDataset.getId()).get(0);
        assertEquals("RESEARCH", restoredDataset.getDatasetLevel());
        assertEquals(LocalDateTime.of(2026, 7, 15, 15, 30), restoredDataset.getAsOfTime());
        assertEquals("quant-dataset-v2", restoredDataset.getFingerprintVersion());
        assertEquals("[{\"type\":\"CAPITAL_FLOW_DAILY\"}]", restoredDataset.getPartitionManifest());
        assertEquals(expected.getTradeDate(), restored.getTradeDate());
        assertEquals(expected.getInstrumentCode(), restored.getInstrumentCode());
        assertEquals(expected.getAvailableAt(), restored.getAvailableAt());
        assertEquals(expected.getSourceFlowId(), restored.getSourceFlowId());
        assertEquals(expected.getProviderCode(), restored.getProviderCode());
        assertDecimalEquals(expected.getMainNetInflow(), restored.getMainNetInflow());
        assertDecimalEquals(expected.getMainFlowShare(), restored.getMainFlowShare());
        assertDecimalEquals(expected.getSuperLargeNetInflow(), restored.getSuperLargeNetInflow());
        assertDecimalEquals(expected.getLargeNetInflow(), restored.getLargeNetInflow());
        assertDecimalEquals(expected.getMediumNetInflow(), restored.getMediumNetInflow());
        assertDecimalEquals(expected.getSmallNetInflow(), restored.getSmallNetInflow());
        assertDecimalEquals(expected.getTurnoverRate(), restored.getTurnoverRate());
        assertDecimalEquals(expected.getAmount(), restored.getAmount());
        assertEquals(expected.getMainNetInflow().scale(), restored.getMainNetInflow().scale());
        assertEquals("12345678901234567890.1200", jdbc.queryForObject(
                "SELECT main_net_inflow FROM quant_capital_flow_daily WHERE dataset_id=?",
                String.class, savedDataset.getId()));
        assertEquals(expected.getQualityStatus(), restored.getQualityStatus());
        assertEquals(expected.getSourceFingerprint(), restored.getSourceFingerprint());
        assertEquals(expected.getCalculationVersion(), restored.getCalculationVersion());
    }

    @Test
    void duplicateFrozenCapitalFlowKeyFailsLoudly() {
        Long datasetId = datasets.save(dataset("REAL")).getId();
        QuantCapitalFlowDaily flow = capitalFlow(datasetId);
        capitalFlows.saveAll(Collections.singletonList(flow));

        assertThrows(DataAccessException.class,
                () -> capitalFlows.saveAll(Collections.singletonList(flow)));
        assertEquals(1, capitalFlows.findByDatasetId(datasetId).size());
    }

    @Test
    void saveAllRollsBackEarlierRowsWhenAnyFrozenKeyIsDuplicate() {
        Long datasetId = datasets.save(dataset("REAL")).getId();
        QuantCapitalFlowDaily existing = capitalFlow(datasetId);
        capitalFlows.saveAll(Collections.singletonList(existing));
        QuantCapitalFlowDaily validNewRow = capitalFlow(datasetId);
        validNewRow.setTradeDate(existing.getTradeDate().plusDays(1));

        assertThrows(DataAccessException.class,
                () -> capitalFlows.saveAll(Arrays.asList(validNewRow, existing)));

        List<QuantCapitalFlowDaily> restored = capitalFlows.findByDatasetId(datasetId);
        assertEquals(1, restored.size());
        assertEquals(existing.getTradeDate(), restored.get(0).getTradeDate());
    }

    @Test
    void saveAllParticipatesInOuterRequiredTransaction() {
        Long datasetId = datasets.save(dataset("REAL")).getId();
        QuantCapitalFlowDaily first = capitalFlow(datasetId);
        QuantCapitalFlowDaily second = capitalFlow(datasetId);
        second.setTradeDate(first.getTradeDate().plusDays(1));
        TransactionTemplate outerTransaction = new TransactionTemplate(transactionManager);

        assertThrows(RuntimeException.class, () -> outerTransaction.executeWithoutResult(status -> {
            capitalFlows.saveAll(Arrays.asList(first, second));
            throw new RuntimeException("force outer rollback");
        }));

        assertEquals(0, capitalFlows.findByDatasetId(datasetId).size());
    }

    @ParameterizedTest
    @ValueSource(strings = {"600519", "600519.sh", " 600519.SH ", "600519.HK"})
    void rejectsNonCanonicalInstrumentCodesBeforeWriting(String code) {
        Long datasetId = datasets.save(dataset("REAL")).getId();
        QuantCapitalFlowDaily invalid = capitalFlow(datasetId);
        invalid.setInstrumentCode(code);

        assertThrows(IllegalArgumentException.class,
                () -> capitalFlows.saveAll(Collections.singletonList(invalid)));
        assertEquals(0, capitalFlows.findByDatasetId(datasetId).size());
    }

    @Test
    void partitionsRoundTripInStablePartitionTypeOrder() {
        Long datasetId = datasets.save(dataset("LEARNING_SAMPLE")).getId();
        partitions.save(partition(datasetId, "UNIVERSE", 30));
        partitions.save(partition(datasetId, "CAPITAL_FLOW_DAILY", 240));

        List<QuantDatasetPartition> restored = partitions.findByDatasetId(datasetId);

        assertEquals(Arrays.asList("CAPITAL_FLOW_DAILY", "UNIVERSE"), Arrays.asList(
                restored.get(0).getPartitionType(), restored.get(1).getPartitionType()));
        assertEquals(240, restored.get(0).getRowCount());
        assertEquals(LocalDate.of(2026, 1, 2), restored.get(0).getMinDate());
        assertEquals(LocalDate.of(2026, 7, 14), restored.get(0).getMaxDate());
        assertEquals("partition-CAPITAL_FLOW_DAILY", restored.get(0).getPartitionFingerprint());
        assertEquals("COMPLETE", restored.get(0).getQualityStatus());
        assertEquals(LocalDateTime.of(2026, 7, 15, 16, 0), restored.get(0).getCreatedAt());
    }

    @Test
    void repositoryRejectsInvalidPartitionRangesBeforeWriting() {
        Long datasetId = datasets.save(dataset("REAL")).getId();
        QuantDatasetPartition negativeRows = partition(datasetId, "NEGATIVE", -1);
        QuantDatasetPartition oneSidedRange = partition(datasetId, "ONE_SIDED", 0);
        oneSidedRange.setMaxDate(null);
        QuantDatasetPartition invertedRange = partition(datasetId, "INVERTED", 0);
        invertedRange.setMinDate(LocalDate.of(2026, 7, 15));
        invertedRange.setMaxDate(LocalDate.of(2026, 7, 14));

        assertThrows(IllegalArgumentException.class, () -> partitions.save(negativeRows));
        assertThrows(IllegalArgumentException.class, () -> partitions.save(oneSidedRange));
        assertThrows(IllegalArgumentException.class, () -> partitions.save(invertedRange));
        assertEquals(0, partitions.findByDatasetId(datasetId).size());
    }

    @Test
    void databaseChecksRejectInvalidPartitionRangesFromDirectSql() {
        Long datasetId = datasets.save(dataset("REAL")).getId();
        String sql = "INSERT INTO quant_dataset_partition(dataset_id,partition_type,row_count,min_date,max_date,"
                + "partition_fingerprint,quality_status,created_at) VALUES(?,?,?,?,?,?,?,?)";

        assertThrows(DataAccessException.class, () -> jdbc.update(sql, datasetId, "NEGATIVE", -1,
                null, null, "negative", "INVALID", "2026-07-15T16:00"));
        assertThrows(DataAccessException.class, () -> jdbc.update(sql, datasetId, "ONE_SIDED", 0,
                "2026-01-02", null, "one-sided", "INVALID", "2026-07-15T16:00"));
        assertThrows(DataAccessException.class, () -> jdbc.update(sql, datasetId, "INVERTED", 0,
                "2026-07-15", "2026-07-14", "inverted", "INVALID", "2026-07-15T16:00"));
        assertEquals(0, partitions.findByDatasetId(datasetId).size());
    }

    @Test
    void concurrentMigratorsAtomicallyClaimEveryVersion() throws Exception {
        Path database = tempDir.resolve("concurrent-migration.db");
        SQLiteDataSource initializerDataSource = dataSource(database);
        JdbcTemplate initializerJdbc = new JdbcTemplate(initializerDataSource);
        DatabaseInitializer initializer = new DatabaseInitializer();
        ReflectionTestUtils.setField(initializer, "jdbcTemplate", initializerJdbc);
        ReflectionTestUtils.setField(initializer, "dataRoot", tempDir.resolve("concurrent-data").toString());
        initializer.afterPropertiesSet();
        initializerJdbc.execute("CREATE TABLE IF NOT EXISTS schema_migration ("
                + "version INTEGER PRIMARY KEY,description TEXT NOT NULL,applied_at TEXT NOT NULL)");

        CyclicBarrier version200ClaimBarrier = new CyclicBarrier(2);
        AtomicInteger version200ClaimArrivals = new AtomicInteger();
        SQLiteDataSource firstDataSource = dataSource(database);
        SQLiteDataSource secondDataSource = dataSource(database);
        JdbcTemplate firstJdbc = new MigrationRaceJdbcTemplate(
                firstDataSource, version200ClaimBarrier, version200ClaimArrivals);
        JdbcTemplate secondJdbc = new MigrationRaceJdbcTemplate(
                secondDataSource, version200ClaimBarrier, version200ClaimArrivals);
        FactorResearchSchemaMigrator first = new FactorResearchSchemaMigrator(
                firstJdbc, new DataSourceTransactionManager(firstDataSource));
        FactorResearchSchemaMigrator second = new FactorResearchSchemaMigrator(
                secondJdbc, new DataSourceTransactionManager(secondDataSource));
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<?> firstRun = executor.submit(() -> migrateTogether(first, ready, start));
            Future<?> secondRun = executor.submit(() -> migrateTogether(second, ready, start));
            assertTrue(ready.await(5, TimeUnit.SECONDS));
            start.countDown();

            firstRun.get(10, TimeUnit.SECONDS);
            secondRun.get(10, TimeUnit.SECONDS);
        } finally {
            executor.shutdownNow();
        }

        assertEquals(2, version200ClaimArrivals.get());
        assertEquals(1, initializerJdbc.queryForObject(
                "SELECT COUNT(*) FROM schema_migration WHERE version=200", Integer.class));
        assertEquals(1, initializerJdbc.queryForObject(
                "SELECT COUNT(*) FROM schema_migration WHERE version=201", Integer.class));
        assertEquals(1, initializerJdbc.queryForObject(
                "SELECT COUNT(*) FROM schema_migration WHERE version=202", Integer.class));
        assertEquals(1, initializerJdbc.queryForObject(
                "SELECT COUNT(*) FROM schema_migration WHERE version=203", Integer.class));
        assertEquals(1, initializerJdbc.queryForObject(
                "SELECT COUNT(*) FROM sqlite_master WHERE type='table' AND name='quant_capital_flow_daily'",
                Integer.class));
        assertEquals(1, initializerJdbc.queryForObject(
                "SELECT COUNT(*) FROM sqlite_master WHERE type='table' AND name='quant_dataset_partition'",
                Integer.class));
        assertEquals(1, initializerJdbc.queryForObject(
                "SELECT COUNT(*) FROM sqlite_master WHERE type='table' AND name='factor_research_draft'",
                Integer.class));
    }

    @Test
    void failedVersion200DdlRollsBackClaimAndAllowsCleanRerun() throws Exception {
        Path database = tempDir.resolve("failed-migration.db");
        SQLiteDataSource dataSource = dataSource(database);
        JdbcTemplate normalJdbc = new JdbcTemplate(dataSource);
        DatabaseInitializer initializer = new DatabaseInitializer();
        ReflectionTestUtils.setField(initializer, "jdbcTemplate", normalJdbc);
        ReflectionTestUtils.setField(initializer, "dataRoot", tempDir.resolve("failed-data").toString());
        initializer.afterPropertiesSet();

        AtomicInteger successfulClaims = new AtomicInteger();
        AtomicInteger failedDdlAttempts = new AtomicInteger();
        JdbcTemplate failingJdbc = new FailingCapitalTableJdbcTemplate(
                dataSource, successfulClaims, failedDdlAttempts);
        FactorResearchSchemaMigrator failingMigrator = new FactorResearchSchemaMigrator(
                failingJdbc, new DataSourceTransactionManager(dataSource));

        assertThrows(DataAccessException.class, failingMigrator::migrate);
        assertEquals(1, successfulClaims.get());
        assertEquals(1, failedDdlAttempts.get());
        assertEquals(0, normalJdbc.queryForObject(
                "SELECT COUNT(*) FROM schema_migration WHERE version=200", Integer.class));
        assertEquals(0, normalJdbc.queryForObject(
                "SELECT COUNT(*) FROM sqlite_master WHERE type='table' AND name='quant_capital_flow_daily'",
                Integer.class));

        FactorResearchSchemaMigrator retry = new FactorResearchSchemaMigrator(
                normalJdbc, new DataSourceTransactionManager(dataSource));
        retry.migrate();

        assertEquals(1, normalJdbc.queryForObject(
                "SELECT COUNT(*) FROM schema_migration WHERE version=200", Integer.class));
        assertEquals(1, normalJdbc.queryForObject(
                "SELECT COUNT(*) FROM sqlite_master WHERE type='table' AND name='quant_capital_flow_daily'",
                Integer.class));
    }

    @Test
    void deletingDatasetCascadesFrozenCapitalAndPartitions() {
        Long datasetId = datasets.save(dataset("REAL")).getId();
        capitalFlows.saveAll(Collections.singletonList(capitalFlow(datasetId)));
        partitions.save(partition(datasetId, "CAPITAL_FLOW_DAILY", 1));

        jdbc.update("DELETE FROM quant_dataset WHERE id=?", datasetId);

        assertEquals(0, capitalFlows.findByDatasetId(datasetId).size());
        assertEquals(0, partitions.findByDatasetId(datasetId).size());
    }

    @Test
    void upgradesOldDatasetSchemaWithoutRowLossAndPreservesExplicitLevelOnRepair() {
        SQLiteDataSource legacyDataSource = dataSource(tempDir.resolve("legacy.db"));
        JdbcTemplate legacyJdbc = new JdbcTemplate(legacyDataSource);
        legacyJdbc.execute("CREATE TABLE quant_dataset ("
                + "id INTEGER PRIMARY KEY AUTOINCREMENT,name TEXT NOT NULL,market TEXT NOT NULL,"
                + "universe_type TEXT NOT NULL,source_type TEXT NOT NULL,data_kind TEXT NOT NULL,"
                + "start_date TEXT,end_date TEXT,status TEXT NOT NULL,fingerprint TEXT,quality_summary TEXT,"
                + "revision INTEGER NOT NULL DEFAULT 0,created_at TEXT NOT NULL,updated_at TEXT NOT NULL)");
        legacyJdbc.update("INSERT INTO quant_dataset(name,market,universe_type,source_type,data_kind,status,created_at,updated_at) "
                        + "VALUES(?,?,?,?,?,?,?,?)", "legacy-real", "A_SHARE", "CUSTOM", "IMPORT", "REAL", "READY",
                "2026-07-15T10:00", "2026-07-15T10:00");
        FactorResearchSchemaMigrator legacyMigrator = new FactorResearchSchemaMigrator(
                legacyJdbc, new DataSourceTransactionManager(legacyDataSource));

        legacyMigrator.migrate();

        Map<String, Object> upgraded = legacyJdbc.queryForMap(
                "SELECT data_kind,dataset_level,fingerprint_version,partition_manifest FROM quant_dataset");
        assertEquals("REAL", upgraded.get("data_kind"));
        assertEquals("RESEARCH", upgraded.get("dataset_level"));
        assertEquals("quant-dataset-v1", upgraded.get("fingerprint_version"));
        assertEquals("[]", upgraded.get("partition_manifest"));
        assertEquals(1, legacyJdbc.queryForObject("SELECT COUNT(*) FROM quant_dataset", Integer.class));

        legacyJdbc.update("UPDATE quant_dataset SET dataset_level='ARCHIVE'");
        legacyJdbc.update("DELETE FROM schema_migration WHERE version=202");
        legacyMigrator.migrate();

        assertEquals("ARCHIVE", legacyJdbc.queryForObject(
                "SELECT dataset_level FROM quant_dataset", String.class));
        assertEquals(1, legacyJdbc.queryForObject(
                "SELECT COUNT(*) FROM schema_migration WHERE version=202", Integer.class));
        assertFalse(columns(legacyJdbc, "quant_dataset").isEmpty());
    }

    private SQLiteDataSource dataSource(Path database) {
        SQLiteDataSource dataSource = new SQLiteDataSource();
        dataSource.setUrl("jdbc:sqlite:" + database + "?foreign_keys=on");
        dataSource.setBusyTimeout(5000);
        dataSource.setEnforceForeignKeys(true);
        return dataSource;
    }

    private void migrateTogether(FactorResearchSchemaMigrator target,
                                 CountDownLatch ready,
                                 CountDownLatch start) {
        try {
            ready.countDown();
            start.await(5, TimeUnit.SECONDS);
            target.migrate();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("migration test interrupted", e);
        }
    }

    private static final class MigrationRaceJdbcTemplate extends JdbcTemplate {
        private final CyclicBarrier barrier;
        private final AtomicInteger arrivals;

        private MigrationRaceJdbcTemplate(SQLiteDataSource dataSource,
                                           CyclicBarrier barrier,
                                           AtomicInteger arrivals) {
            super(dataSource);
            this.barrier = barrier;
            this.arrivals = arrivals;
        }

        @Override
        public int update(String sql, Object... args) {
            if (sql.startsWith("INSERT OR IGNORE INTO schema_migration") && args.length == 3
                    && Integer.valueOf(200).equals(args[0])) {
                arrivals.incrementAndGet();
                try {
                    barrier.await(5, TimeUnit.SECONDS);
                } catch (Exception e) {
                    throw new IllegalStateException("failed to synchronize migration race", e);
                }
            }
            return super.update(sql, args);
        }
    }

    private static final class FailingCapitalTableJdbcTemplate extends JdbcTemplate {
        private final AtomicInteger successfulClaims;
        private final AtomicInteger failedDdlAttempts;

        private FailingCapitalTableJdbcTemplate(SQLiteDataSource dataSource,
                                                 AtomicInteger successfulClaims,
                                                 AtomicInteger failedDdlAttempts) {
            super(dataSource);
            this.successfulClaims = successfulClaims;
            this.failedDdlAttempts = failedDdlAttempts;
        }

        @Override
        public int update(String sql, Object... args) {
            int updated = super.update(sql, args);
            if (sql.startsWith("INSERT OR IGNORE INTO schema_migration") && args.length == 3
                    && Integer.valueOf(200).equals(args[0]) && updated == 1) {
                successfulClaims.incrementAndGet();
            }
            return updated;
        }

        @Override
        public void execute(String sql) {
            if (sql.startsWith("CREATE TABLE IF NOT EXISTS quant_capital_flow_daily")) {
                failedDdlAttempts.incrementAndGet();
                throw new DataIntegrityViolationException("injected version 200 DDL failure");
            }
            super.execute(sql);
        }
    }

    private QuantDataset dataset(String dataKind) {
        QuantDataset value = new QuantDataset();
        value.setName("资金行为冻结研究集");
        value.setMarket("A_SHARE");
        value.setUniverseType("CUSTOM");
        value.setSourceType("CAPITAL_BEHAVIOR");
        value.setDataKind(dataKind);
        value.setStatus("READY");
        value.setDatasetLevel("REAL".equals(dataKind) ? "RESEARCH" : null);
        value.setAsOfTime(LocalDateTime.of(2026, 7, 15, 15, 30));
        value.setFingerprintVersion("quant-dataset-v2");
        value.setPartitionManifest("[{\"type\":\"CAPITAL_FLOW_DAILY\"}]");
        return value;
    }

    private QuantCapitalFlowDaily capitalFlow(Long datasetId) {
        QuantCapitalFlowDaily value = new QuantCapitalFlowDaily();
        value.setDatasetId(datasetId);
        value.setTradeDate(LocalDate.of(2026, 7, 14));
        value.setInstrumentCode("600519.SH");
        value.setAvailableAt(LocalDateTime.of(2026, 7, 15, 9, 15, 30));
        value.setSourceFlowId(998877L);
        value.setProviderCode("EASTMONEY");
        value.setMainNetInflow(new BigDecimal("12345678901234567890.1200"));
        value.setMainFlowShare(new BigDecimal("0.123400"));
        value.setSuperLargeNetInflow(new BigDecimal("111111111.11"));
        value.setLargeNetInflow(new BigDecimal("222222222.220"));
        value.setMediumNetInflow(new BigDecimal("-333333333.3300"));
        value.setSmallNetInflow(new BigDecimal("0.00000001"));
        value.setTurnoverRate(new BigDecimal("2.3400"));
        value.setAmount(new BigDecimal("98765432109876543210.0001"));
        value.setQualityStatus("COMPLETE");
        value.setSourceFingerprint("capital-source-sha256");
        value.setCalculationVersion("capital-factor-v1");
        return value;
    }

    private QuantDatasetPartition partition(Long datasetId, String type, long rowCount) {
        QuantDatasetPartition value = new QuantDatasetPartition();
        value.setDatasetId(datasetId);
        value.setPartitionType(type);
        value.setRowCount(rowCount);
        value.setMinDate(LocalDate.of(2026, 1, 2));
        value.setMaxDate(LocalDate.of(2026, 7, 14));
        value.setPartitionFingerprint("partition-" + type);
        value.setQualityStatus("COMPLETE");
        value.setCreatedAt(LocalDateTime.of(2026, 7, 15, 16, 0));
        return value;
    }

    private int objectCount(String type, String name) {
        Integer value = jdbc.queryForObject(
                "SELECT COUNT(*) FROM sqlite_master WHERE type=? AND name=?", Integer.class, type, name);
        return value == null ? 0 : value;
    }

    private int count(String sql) {
        Integer value = jdbc.queryForObject(sql, Integer.class);
        return value == null ? 0 : value;
    }

    private List<Map<String, Object>> columns(JdbcTemplate template, String table) {
        return template.queryForList("PRAGMA table_info(" + table + ")");
    }

    private void assertDecimalEquals(BigDecimal expected, BigDecimal actual) {
        assertEquals(0, expected.compareTo(actual));
    }
}
