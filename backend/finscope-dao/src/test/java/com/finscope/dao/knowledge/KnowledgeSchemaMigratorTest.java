package com.finscope.dao.knowledge;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sqlite.SQLiteDataSource;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class KnowledgeSchemaMigratorTest {
    @TempDir
    Path tempDir;

    private JdbcTemplate jdbc;
    private KnowledgeSchemaMigrator migrator;

    @BeforeEach
    void setUp() {
        SQLiteDataSource dataSource = new SQLiteDataSource();
        dataSource.setUrl("jdbc:sqlite:" + tempDir.resolve("legacy.db") + "?foreign_keys=on");
        jdbc = new JdbcTemplate(dataSource);
        migrator = new KnowledgeSchemaMigrator(
                jdbc,
                new DataSourceTransactionManager(dataSource)
        );
        createLegacySchema();
        insertLegacyData();
    }

    @Test
    void migratesLegacyKnowledgeDataExactlyOnce() {
        migrator.migrate();
        migrator.migrate();

        assertEquals(1, tableCount("knowledge_entry"));
        assertEquals(1, tableCount("knowledge_entry_evidence"));
        assertEquals(1, tableCount("topic_review_state"));
        assertEquals(1, tableCount("topic_event"));
        assertEquals(1, tableCount("knowledge_projection_job"));

        assertEquals("ACTIVE", value("SELECT lifecycle_status FROM topic WHERE id=1"));
        assertEquals("BUILDING", value("SELECT mastery_status FROM topic WHERE id=1"));
        assertEquals("REVIEWING", value("SELECT mastery_status FROM topic WHERE id=2"));
        assertEquals("EXPLORING", value("SELECT mastery_status FROM topic WHERE id=3"));

        assertEquals("SUGGESTED", value("SELECT status FROM learning_task WHERE id=1"));
        assertEquals("IN_PROGRESS", value("SELECT status FROM learning_task WHERE id=2"));
        assertEquals("DONE", value("SELECT status FROM learning_task WHERE id=3"));
        assertEquals("LEGACY", value("SELECT completion_mode FROM learning_task WHERE id=3"));
        assertEquals(1, count("SELECT COUNT(*) FROM schema_migration WHERE version=1"));
    }

    @Test
    void enforcesOneFinalAnswerPerLearningTask() {
        migrator.migrate();
        String insert = "INSERT INTO knowledge_entry(" +
                "topic_id,learning_task_id,entry_type,entry_status,content_markdown," +
                "confidence,created_at,updated_at) VALUES(1,1,'ANSWER','FINAL','content','MEDIUM',?,?)";
        jdbc.update(insert, "2026-07-13T10:00:00", "2026-07-13T10:00:00");

        assertThrows(DataAccessException.class,
                () -> jdbc.update(insert, "2026-07-13T10:01:00", "2026-07-13T10:01:00"));
    }

    private void createLegacySchema() {
        jdbc.execute("CREATE TABLE topic (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "name TEXT NOT NULL,slug TEXT NOT NULL UNIQUE,description TEXT,status TEXT NOT NULL," +
                "markdown_path TEXT,terms TEXT,learning_questions TEXT," +
                "created_at TEXT NOT NULL,updated_at TEXT NOT NULL)");
        jdbc.execute("CREATE TABLE event_cluster (id INTEGER PRIMARY KEY AUTOINCREMENT)");
        jdbc.execute("CREATE TABLE evidence_item (id INTEGER PRIMARY KEY AUTOINCREMENT)");
        jdbc.execute("CREATE TABLE learning_task (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT,event_id INTEGER,theme_code TEXT NOT NULL," +
                "question TEXT NOT NULL,concepts TEXT,difficulty TEXT NOT NULL,status TEXT NOT NULL," +
                "why_needed TEXT,created_at TEXT NOT NULL,updated_at TEXT NOT NULL)");
    }

    private void insertLegacyData() {
        insertTopic(1, "LEARNING");
        insertTopic(2, "REVIEWING");
        insertTopic(3, "UNKNOWN");
        jdbc.update("INSERT INTO event_cluster(id) VALUES(1)");
        insertTask(1, "TODO");
        insertTask(2, "LEARNING");
        insertTask(3, "DONE");
    }

    private void insertTopic(long id, String status) {
        jdbc.update("INSERT INTO topic(id,name,slug,status,created_at,updated_at) VALUES(?,?,?,?,?,?)",
                id, "Topic " + id, "topic-" + id, status,
                "2026-07-13T09:00:00", "2026-07-13T09:00:00");
    }

    private void insertTask(long id, String status) {
        jdbc.update("INSERT INTO learning_task(" +
                        "id,event_id,theme_code,question,difficulty,status,created_at,updated_at" +
                        ") VALUES(?,?,?,?,?,?,?,?)",
                id, 1L, "AI", "Question " + id, "MEDIUM", status,
                "2026-07-13T09:00:00", "2026-07-13T09:00:00");
    }

    private int tableCount(String tableName) {
        return count("SELECT COUNT(*) FROM sqlite_master WHERE type='table' AND name='" + tableName + "'");
    }

    private int count(String sql) {
        Integer result = jdbc.queryForObject(sql, Integer.class);
        return result == null ? 0 : result;
    }

    private String value(String sql) {
        return jdbc.queryForObject(sql, String.class);
    }
}
