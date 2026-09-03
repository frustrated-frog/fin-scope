package com.finscope.dao.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sqlite.SQLiteDataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

class EphemeralContentDataMigrationTest {
    @TempDir
    Path tempDir;

    @Test
    void removesLegacyNewsAndRadarRowsOnceButPreservesMajorEventsAndCategories() throws Exception {
        SQLiteDataSource dataSource = new SQLiteDataSource();
        dataSource.setUrl("jdbc:sqlite:" + tempDir.resolve("migration.db"));
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        DatabaseInitializer initializer = new DatabaseInitializer();
        ReflectionTestUtils.setField(initializer, "jdbcTemplate", jdbc);
        ReflectionTestUtils.setField(initializer, "dataRoot", tempDir.toString());
        initializer.afterPropertiesSet();
        jdbc.update("INSERT INTO news_item_classification(item_id,status,created_at,updated_at) VALUES('NEWS:1','PENDING',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)");
        jdbc.update("INSERT INTO radar_signal(item_id,title,first_seen_at,last_seen_at,content_hash,status) VALUES('NEWS:1','标题',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,'hash','ACTIVE')");
        jdbc.update("INSERT INTO radar_event(event_key,canonical_title,status,first_seen_at,last_seen_at,updated_at) VALUES('event-1','事件','ACTIVE',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)");
        jdbc.update("INSERT INTO major_event(origin_type,origin_key,title,occurred_date,created_at,updated_at) VALUES('NEWS_ITEM','NEWS:1','已归档','2026-09-04',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)");
        EphemeralContentDataMigration migration = new EphemeralContentDataMigration();
        ReflectionTestUtils.setField(migration, "jdbcTemplate", jdbc);

        migration.migrate();
        migration.migrate();

        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM news_item_classification", Integer.class));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM radar_signal", Integer.class));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM radar_event", Integer.class));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM major_event", Integer.class));
        assertEquals(5, jdbc.queryForObject("SELECT COUNT(*) FROM news_category", Integer.class));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM schema_migration WHERE version=402", Integer.class));
    }
}
