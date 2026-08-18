package com.finscope.dao.instrument;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.sqlite.SQLiteDataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TonghuashunSectorCutoverMigratorTest {
    private JdbcTemplate jdbc;
    private TonghuashunSectorCutoverMigrator migrator;

    @BeforeEach
    void setUp() throws Exception {
        Path database = Files.createTempFile("finscope-ths-sector-cutover", ".db");
        SQLiteDataSource dataSource = new SQLiteDataSource();
        dataSource.setUrl("jdbc:sqlite:" + database);
        jdbc = new JdbcTemplate(dataSource);
        jdbc.execute("CREATE TABLE schema_migration (version INTEGER PRIMARY KEY,"
                + "description TEXT NOT NULL,applied_at TEXT NOT NULL)");
        jdbc.execute("CREATE TABLE instrument (id INTEGER PRIMARY KEY AUTOINCREMENT,"
                + "code TEXT NOT NULL,type TEXT NOT NULL,name TEXT)");
        jdbc.execute("CREATE TABLE watchlist_item (id INTEGER PRIMARY KEY AUTOINCREMENT,"
                + "instrument_id INTEGER NOT NULL)");
        insert("BK1036", "SECTOR", "旧半导体");
        insert("881121", "SECTOR", "新半导体");
        insert("600519", "STOCK", "贵州茅台");
        jdbc.update("INSERT INTO watchlist_item(instrument_id) SELECT id FROM instrument");

        migrator = new TonghuashunSectorCutoverMigrator();
        ReflectionTestUtils.setField(migrator, "jdbcTemplate", jdbc);
        ReflectionTestUtils.setField(migrator, "transactionManager",
                new DataSourceTransactionManager(dataSource));
    }

    @Test
    void removesOnlyLegacyBkSectorFollowsAndIsIdempotent() throws Exception {
        migrator.afterPropertiesSet();
        migrator.afterPropertiesSet();

        assertEquals(0, count("SELECT COUNT(*) FROM watchlist_item w JOIN instrument i "
                + "ON w.instrument_id=i.id WHERE i.type='SECTOR' AND i.code LIKE 'BK%'"));
        assertEquals(1, count("SELECT COUNT(*) FROM watchlist_item w JOIN instrument i "
                + "ON w.instrument_id=i.id WHERE i.type='SECTOR' AND i.code='881121'"));
        assertEquals(1, count("SELECT COUNT(*) FROM watchlist_item w JOIN instrument i "
                + "ON w.instrument_id=i.id WHERE i.type='STOCK'"));
        assertEquals(1, count("SELECT COUNT(*) FROM schema_migration WHERE version=400"));
    }

    private void insert(String code, String type, String name) {
        jdbc.update("INSERT INTO instrument(code,type,name) VALUES(?,?,?)", code, type, name);
    }

    private int count(String sql) {
        Integer value = jdbc.queryForObject(sql, Integer.class);
        return value == null ? 0 : value;
    }
}
