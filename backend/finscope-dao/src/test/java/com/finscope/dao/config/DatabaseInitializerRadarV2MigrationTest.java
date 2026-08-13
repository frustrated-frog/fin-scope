package com.finscope.dao.config;

import org.junit.jupiter.api.Test;
import org.sqlite.SQLiteDataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DatabaseInitializerRadarV2MigrationTest {
    @Test
    void upgradesLegacyRadarTablesWithoutRebuildingExistingEvents() throws Exception {
        Path dataRoot = Files.createTempDirectory("finscope-radar-v2-migration");
        SQLiteDataSource dataSource = new SQLiteDataSource();
        dataSource.setUrl("jdbc:sqlite:" + dataRoot.resolve("finance.db"));
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        createLegacyRadarTables(jdbc);
        jdbc.update("INSERT INTO radar_event(event_key,canonical_title,status,first_seen_at,last_seen_at,updated_at) "
                        + "VALUES(?,?,?,?,?,?)", "宁德时代:发布:电池", "宁德时代发布电池", "ACTIVE",
                "2026-08-13T09:00:00", "2026-08-13T09:00:00", "2026-08-13T09:00:00");

        DatabaseInitializer initializer = new DatabaseInitializer();
        ReflectionTestUtils.setField(initializer, "jdbcTemplate", jdbc);
        ReflectionTestUtils.setField(initializer, "dataRoot", dataRoot.toString());
        initializer.afterPropertiesSet();

        assertEquals(1, columnCount(jdbc, "radar_event", "confidence_score"));
        assertEquals(1, columnCount(jdbc, "radar_event", "confidence_explanation"));
        assertEquals(1, columnCount(jdbc, "radar_event", "score_version"));
        assertEquals(1, columnCount(jdbc, "radar_event_snapshot", "confirmation_score"));
        assertEquals(1, columnCount(jdbc, "radar_event_snapshot", "freshness_score"));
        assertEquals(1, columnCount(jdbc, "radar_event_snapshot", "rank_trend_score"));
        assertEquals(1, columnCount(jdbc, "radar_event_snapshot", "confidence_score"));
        assertEquals(1, columnCount(jdbc, "radar_event_snapshot", "score_version"));
        assertEquals("宁德时代:发布:电池", jdbc.queryForObject(
                "SELECT event_key FROM radar_event", String.class));
        assertEquals("HOTSPOT_V1", jdbc.queryForObject(
                "SELECT score_version FROM radar_event", String.class));
    }

    private int columnCount(JdbcTemplate jdbc, String table, String column) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM pragma_table_info('" + table + "') WHERE name=?",
                Integer.class, column);
    }

    private void createLegacyRadarTables(JdbcTemplate jdbc) {
        jdbc.execute("CREATE TABLE radar_event("
                + "id INTEGER PRIMARY KEY AUTOINCREMENT,event_key TEXT NOT NULL UNIQUE,canonical_title TEXT NOT NULL,"
                + "summary TEXT,category_code TEXT,dashboard_category TEXT NOT NULL DEFAULT 'UNCLASSIFIED',"
                + "status TEXT NOT NULL,first_seen_at TEXT NOT NULL,last_seen_at TEXT NOT NULL,"
                + "source_count INTEGER NOT NULL DEFAULT 0,signal_count INTEGER NOT NULL DEFAULT 0,"
                + "hotspot_score INTEGER NOT NULL DEFAULT 0,hotspot_explanation TEXT,hotspot_lifecycle_state TEXT,"
                + "priority_score INTEGER NOT NULL DEFAULT 0,score_explanation TEXT,"
                + "watchlist_relevance INTEGER NOT NULL DEFAULT 0,watchlist_explanation TEXT,"
                + "uncertainty TEXT,next_observation TEXT,updated_at TEXT NOT NULL)");
        jdbc.execute("CREATE TABLE radar_event_snapshot("
                + "id INTEGER PRIMARY KEY AUTOINCREMENT,event_id INTEGER NOT NULL,snapshot_at TEXT NOT NULL,"
                + "signal_count INTEGER NOT NULL DEFAULT 0,independent_source_count INTEGER NOT NULL DEFAULT 0,"
                + "velocity_score REAL NOT NULL DEFAULT 0,hotness_score INTEGER NOT NULL DEFAULT 0,"
                + "lifecycle_state TEXT NOT NULL,explanation TEXT,UNIQUE(event_id,snapshot_at),"
                + "FOREIGN KEY(event_id) REFERENCES radar_event(id) ON DELETE CASCADE)");
    }
}
