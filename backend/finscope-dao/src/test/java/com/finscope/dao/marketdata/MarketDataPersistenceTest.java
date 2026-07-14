package com.finscope.dao.marketdata;

import com.finscope.dao.config.DatabaseInitializer;
import com.finscope.domain.marketdata.MarketDataCapability;
import com.finscope.domain.marketdata.MarketDataRefreshRun;
import com.finscope.domain.marketdata.MarketDataSnapshot;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sqlite.SQLiteDataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.file.Path;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MarketDataPersistenceTest {
    @TempDir Path tempDir;
    private JdbcTemplate jdbc;
    private MarketDataSnapshotRepository snapshots;
    private MarketDataRefreshRunRepository refreshRuns;

    @BeforeEach
    void setUp() throws Exception {
        SQLiteDataSource dataSource = new SQLiteDataSource();
        dataSource.setUrl("jdbc:sqlite:" + tempDir.resolve("market-data.db"));
        jdbc = new JdbcTemplate(dataSource);
        DatabaseInitializer initializer = new DatabaseInitializer();
        ReflectionTestUtils.setField(initializer, "jdbcTemplate", jdbc);
        ReflectionTestUtils.setField(initializer, "dataRoot", tempDir.toString());
        initializer.afterPropertiesSet();
        initializer.afterPropertiesSet();
        snapshots = new MarketDataSnapshotRepository(jdbc);
        refreshRuns = new MarketDataRefreshRunRepository(jdbc);
    }

    @Test
    void upsertsOneLastGoodSnapshotPerCapabilityAndScope() {
        snapshots.upsert(snapshot("SINA_STOCK", "SINA", "old", LocalDateTime.now().minusMinutes(1)));
        snapshots.upsert(snapshot("TENCENT_STOCK", "TENCENT", "new", LocalDateTime.now()));

        MarketDataSnapshot loaded = snapshots.find(
                MarketDataCapability.REALTIME_STOCK_QUOTE, "STOCK:600519").orElseThrow(AssertionError::new);
        assertEquals("TENCENT_STOCK", loaded.getProviderCode());
        assertEquals("new", loaded.getPayloadJson());
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM market_data_snapshot", Integer.class));
    }

    @Test
    void deletesOnlyFinishedRefreshRunsOlderThanThirtyDays() {
        long old = refreshRuns.create(MarketDataCapability.REALTIME_INDEX_QUOTE,
                "INDEX:4", "MANUAL", LocalDateTime.now().minusDays(31));
        refreshRuns.finish(old, "SUCCESS", 4, 4, 0, 0,
                "TENCENT_INDEX", null, LocalDateTime.now().minusDays(31));
        long recent = refreshRuns.create(MarketDataCapability.REALTIME_INDEX_QUOTE,
                "INDEX:4", "MANUAL", LocalDateTime.now());

        assertEquals(1, refreshRuns.deleteFinishedBefore(LocalDateTime.now().minusDays(30)));
        assertTrue(refreshRuns.find(recent).isPresent());
        assertEquals("RUNNING", refreshRuns.find(recent).map(MarketDataRefreshRun::getStatus).orElse(""));
    }

    private MarketDataSnapshot snapshot(String providerCode, String providerFamily,
                                        String payload, LocalDateTime retrievedAt) {
        return new MarketDataSnapshot(MarketDataCapability.REALTIME_STOCK_QUOTE, "STOCK:600519",
                providerCode, providerFamily, retrievedAt.minusSeconds(1), retrievedAt,
                payload, "hash-" + payload, 1, retrievedAt);
    }
}
