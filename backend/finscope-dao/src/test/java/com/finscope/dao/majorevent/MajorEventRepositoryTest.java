package com.finscope.dao.majorevent;

import org.junit.jupiter.api.Test;
import org.sqlite.SQLiteDataSource;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.junit.jupiter.api.Assertions.assertTrue;

class MajorEventRepositoryTest {
    @Test
    void requiresAnExactUniqueSnapshotPredatingTheRun() {
        SQLiteDataSource source = new SQLiteDataSource();
        source.setUrl("jdbc:sqlite::memory:");
        // A single connection is needed to retain an in-memory SQLite database across queries.
        try (java.sql.Connection connection = source.getConnection()) {
            JdbcTemplate jdbc = new JdbcTemplate(
                    new org.springframework.jdbc.datasource.SingleConnectionDataSource(connection, true));
            jdbc.execute("CREATE TABLE major_event(id INTEGER PRIMARY KEY,origin_type TEXT,origin_key TEXT,title TEXT,"
                    + "summary TEXT,source_name TEXT,source_url TEXT,category_code TEXT,occurred_date TEXT,note TEXT,created_at TEXT,updated_at TEXT)");
            MajorEventRepository repository = new MajorEventRepository(jdbc);
            jdbc.update("INSERT INTO major_event(id,title,created_at) VALUES(1,?,?)", "长鑫科技交易", "2026-08-06T10:00:00");
            java.time.LocalDateTime cutoff = java.time.LocalDateTime.of(2026, 8, 6, 18, 0);
            assertTrue(repository.findUniqueTitleBefore("长鑫科技交易", cutoff).isPresent());
            assertTrue(repository.findUniqueTitleBefore("长鑫科技", cutoff).isEmpty());
            jdbc.update("INSERT INTO major_event(id,title,created_at) VALUES(2,?,?)", "长鑫科技交易", "2026-08-07T10:00:00");
            assertTrue(repository.findUniqueTitleBefore("长鑫科技交易", cutoff).isPresent());
            jdbc.update("INSERT INTO major_event(id,title,created_at) VALUES(3,?,?)", "长鑫科技交易", "2026-08-06T11:00:00");
            assertTrue(repository.findUniqueTitleBefore("长鑫科技交易", cutoff).isEmpty());
        } catch (java.sql.SQLException error) {
            throw new IllegalStateException(error);
        }
    }
}
