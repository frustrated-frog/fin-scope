package com.finscope.dao.marketintel;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.finscope.domain.marketintel.CapitalBehaviorSnapshot;
import com.finscope.domain.marketintel.CapitalFlowPoint;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class MarketIntelPersistenceTest {
    @TempDir Path tempDir;
    private JdbcTemplate jdbc;
    private CapitalFlowRepository flows;
    private CapitalBehaviorSnapshotRepository snapshots;

    @BeforeEach
    void setUp() {
        SQLiteDataSource dataSource = new SQLiteDataSource();
        dataSource.setUrl("jdbc:sqlite:" + tempDir.resolve("market-intel.db") + "?foreign_keys=on");
        jdbc = new JdbcTemplate(dataSource);
        jdbc.execute("CREATE TABLE instrument(id INTEGER PRIMARY KEY,code TEXT,type TEXT,name TEXT)");
        jdbc.update("INSERT INTO instrument VALUES(7,'600519','STOCK','贵州茅台')");
        MarketIntelSchemaMigrator migrator = new MarketIntelSchemaMigrator(jdbc,
                new DataSourceTransactionManager(dataSource));
        migrator.migrate();
        migrator.migrate();
        flows = new CapitalFlowRepository(jdbc);
        snapshots = new CapitalBehaviorSnapshotRepository(jdbc, new ObjectMapper().findAndRegisterModules());
    }

    @Test
    void migrationIsIdempotentAndFlowPayloadIsDeduplicated() {
        assertEquals(1, count("SELECT COUNT(*) FROM schema_migration WHERE version=100"));
        assertEquals(1, tableCount("market_capital_flow_snapshot"));
        assertEquals(1, tableCount("market_capital_behavior_snapshot"));
        assertEquals(1, tableCount("market_capital_interpretation"));
        assertEquals(1, tableCount("market_intel_refresh_run"));
        assertEquals(1, tableCount("market_intel_refresh_step"));

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
