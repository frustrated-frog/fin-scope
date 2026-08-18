package com.finscope.dao.marketdata;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.sqlite.SQLiteDataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TonghuashunSectorSnapshotCleanupMigratorTest {
    private JdbcTemplate jdbc;
    private TonghuashunSectorSnapshotCleanupMigrator migrator;

    @BeforeEach
    void setUp() throws Exception {
        Path database = Files.createTempFile("finscope-ths-sector-snapshot", ".db");
        SQLiteDataSource dataSource = new SQLiteDataSource();
        dataSource.setUrl("jdbc:sqlite:" + database);
        jdbc = new JdbcTemplate(dataSource);
        jdbc.execute("CREATE TABLE schema_migration (version INTEGER PRIMARY KEY,"
                + "description TEXT NOT NULL,applied_at TEXT NOT NULL)");
        jdbc.execute("CREATE TABLE market_data_snapshot (capability TEXT NOT NULL,"
                + "scope_key TEXT NOT NULL,provider_code TEXT NOT NULL,provider_family TEXT NOT NULL)");
        insert("SECTOR_CATALOG", "SECTOR_CATALOG:INDUSTRY", "SINA_SECTOR_CATALOG", "SINA");
        insert("SECTOR_CATALOG", "SECTOR_CATALOG:CONCEPT", "EASTMONEY_SECTOR", "EASTMONEY");
        insert("SECTOR_CATALOG", "SECTOR_CATALOG:INDUSTRY", "PYTHON_TONGHUASHUN_SECTOR", "TONGHUASHUN");
        insert("QUOTE", "QUOTE:600519", "TENCENT_STOCK", "TENCENT");

        migrator = new TonghuashunSectorSnapshotCleanupMigrator();
        ReflectionTestUtils.setField(migrator, "jdbcTemplate", jdbc);
        ReflectionTestUtils.setField(migrator, "transactionManager",
                new DataSourceTransactionManager(dataSource));
    }

    @Test
    void removesOnlyLegacySectorCatalogSnapshotsAndIsIdempotent() {
        migrator.afterPropertiesSet();
        migrator.afterPropertiesSet();

        assertEquals(0, count("SELECT COUNT(*) FROM market_data_snapshot "
                + "WHERE capability='SECTOR_CATALOG' AND provider_family<>'TONGHUASHUN'"));
        assertEquals(1, count("SELECT COUNT(*) FROM market_data_snapshot "
                + "WHERE capability='SECTOR_CATALOG' AND provider_family='TONGHUASHUN'"));
        assertEquals(1, count("SELECT COUNT(*) FROM market_data_snapshot WHERE capability='QUOTE'"));
        assertEquals(1, count("SELECT COUNT(*) FROM schema_migration WHERE version=401"));
    }

    private void insert(String capability, String scopeKey, String providerCode, String providerFamily) {
        jdbc.update("INSERT INTO market_data_snapshot(capability,scope_key,provider_code,provider_family) "
                + "VALUES(?,?,?,?)", capability, scopeKey, providerCode, providerFamily);
    }

    private int count(String sql) {
        Integer value = jdbc.queryForObject(sql, Integer.class);
        return value == null ? 0 : value;
    }
}
