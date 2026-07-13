package com.finscope.dao.knowledge;

import org.springframework.beans.factory.InitializingBean;
import org.springframework.context.annotation.DependsOn;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Versioned, restart-safe schema evolution for the knowledge bounded context.
 *
 * <p>The legacy initializer remains responsible for the original schema. This
 * migrator owns every additive knowledge-domain change and records a version
 * only after the whole migration transaction succeeds.</p>
 */
@Component
@DependsOn("databaseInitializer")
public class KnowledgeSchemaMigrator implements InitializingBean {
    private static final int VERSION_1 = 1;
    private static final String VERSION_1_DESCRIPTION =
            "knowledge workbench core schema and legacy status migration";

    private final JdbcTemplate jdbcTemplate;
    private final TransactionTemplate transactionTemplate;

    public KnowledgeSchemaMigrator(JdbcTemplate jdbcTemplate,
                                   PlatformTransactionManager transactionManager) {
        this.jdbcTemplate = jdbcTemplate;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    @Override
    public void afterPropertiesSet() {
        migrate();
    }

    public void migrate() {
        createMigrationLedger();
        transactionTemplate.executeWithoutResult(status -> {
            if (isApplied(VERSION_1)) {
                return;
            }
            applyVersion1();
            jdbcTemplate.update(
                    "INSERT INTO schema_migration(version,description,applied_at) VALUES(?,?,?)",
                    VERSION_1,
                    VERSION_1_DESCRIPTION,
                    LocalDateTime.now().toString()
            );
        });
    }

    private void createMigrationLedger() {
        jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS schema_migration (" +
                "version INTEGER PRIMARY KEY," +
                "description TEXT NOT NULL," +
                "applied_at TEXT NOT NULL)");
    }

    private boolean isApplied(int version) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM schema_migration WHERE version=?",
                Integer.class,
                version
        );
        return count != null && count > 0;
    }

    private void applyVersion1() {
        addTopicColumns();
        addLearningTaskColumns();
        createKnowledgeEntrySchema();
        createTopicRelationSchema();
        createProjectionSchema();
        migrateLegacyValues();
    }

    private void addTopicColumns() {
        addColumnIfMissing("topic", "lifecycle_status",
                "TEXT NOT NULL DEFAULT 'ACTIVE'");
        addColumnIfMissing("topic", "mastery_status",
                "TEXT NOT NULL DEFAULT 'EXPLORING'");
        addColumnIfMissing("topic", "revision",
                "INTEGER NOT NULL DEFAULT 0");
        jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_topic_lifecycle_mastery " +
                "ON topic(lifecycle_status,mastery_status,updated_at DESC)");
    }

    private void addLearningTaskColumns() {
        addColumnIfMissing("learning_task", "topic_id",
                "INTEGER REFERENCES topic(id) ON DELETE SET NULL");
        addColumnIfMissing("learning_task", "origin",
                "TEXT NOT NULL DEFAULT 'AGENT'");
        addColumnIfMissing("learning_task", "task_key", "TEXT");
        addColumnIfMissing("learning_task", "priority",
                "INTEGER NOT NULL DEFAULT 50");
        addColumnIfMissing("learning_task", "accepted_at", "TEXT");
        addColumnIfMissing("learning_task", "dismissed_reason", "TEXT");
        addColumnIfMissing("learning_task", "completion_mode", "TEXT");
        addColumnIfMissing("learning_task", "revision",
                "INTEGER NOT NULL DEFAULT 0");

        jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_learning_task_topic_status " +
                "ON learning_task(topic_id,status,updated_at DESC)");
        jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_learning_task_status_priority " +
                "ON learning_task(status,priority DESC,updated_at DESC)");
        jdbcTemplate.execute("CREATE UNIQUE INDEX IF NOT EXISTS idx_learning_task_event_key " +
                "ON learning_task(event_id,task_key) WHERE task_key IS NOT NULL");
    }

    private void createKnowledgeEntrySchema() {
        jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS knowledge_entry (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "topic_id INTEGER NOT NULL," +
                "learning_task_id INTEGER," +
                "entry_type TEXT NOT NULL CHECK(entry_type IN ('ANSWER','INSIGHT','CONCLUSION','REVIEW'))," +
                "entry_status TEXT NOT NULL DEFAULT 'DRAFT' CHECK(entry_status IN ('DRAFT','FINAL'))," +
                "question_snapshot TEXT," +
                "content_markdown TEXT NOT NULL," +
                "confidence TEXT NOT NULL DEFAULT 'MEDIUM' CHECK(confidence IN ('LOW','MEDIUM','HIGH'))," +
                "revision INTEGER NOT NULL DEFAULT 0," +
                "created_at TEXT NOT NULL," +
                "updated_at TEXT NOT NULL," +
                "FOREIGN KEY(topic_id) REFERENCES topic(id) ON DELETE RESTRICT," +
                "FOREIGN KEY(learning_task_id) REFERENCES learning_task(id) ON DELETE SET NULL)");
        jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_knowledge_entry_topic_created " +
                "ON knowledge_entry(topic_id,created_at DESC)");
        jdbcTemplate.execute("CREATE UNIQUE INDEX IF NOT EXISTS idx_knowledge_entry_final_answer " +
                "ON knowledge_entry(learning_task_id) " +
                "WHERE learning_task_id IS NOT NULL " +
                "AND entry_type='ANSWER' AND entry_status='FINAL'");

        jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS knowledge_entry_evidence (" +
                "knowledge_entry_id INTEGER NOT NULL," +
                "evidence_id INTEGER NOT NULL," +
                "created_at TEXT NOT NULL," +
                "PRIMARY KEY(knowledge_entry_id,evidence_id)," +
                "FOREIGN KEY(knowledge_entry_id) REFERENCES knowledge_entry(id) ON DELETE CASCADE," +
                "FOREIGN KEY(evidence_id) REFERENCES evidence_item(id) ON DELETE RESTRICT)");
        jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_knowledge_entry_evidence_item " +
                "ON knowledge_entry_evidence(evidence_id)");
    }

    private void createTopicRelationSchema() {
        jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS topic_event (" +
                "topic_id INTEGER NOT NULL," +
                "event_id INTEGER NOT NULL," +
                "link_type TEXT NOT NULL DEFAULT 'RELATED'," +
                "created_at TEXT NOT NULL," +
                "PRIMARY KEY(topic_id,event_id)," +
                "FOREIGN KEY(topic_id) REFERENCES topic(id) ON DELETE CASCADE," +
                "FOREIGN KEY(event_id) REFERENCES event_cluster(id) ON DELETE RESTRICT)");
        jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_topic_event_event " +
                "ON topic_event(event_id)");

        jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS topic_review_state (" +
                "topic_id INTEGER PRIMARY KEY," +
                "last_reviewed_at TEXT," +
                "next_review_at TEXT," +
                "interval_days INTEGER NOT NULL DEFAULT 7 CHECK(interval_days > 0)," +
                "review_count INTEGER NOT NULL DEFAULT 0 CHECK(review_count >= 0)," +
                "revision INTEGER NOT NULL DEFAULT 0," +
                "FOREIGN KEY(topic_id) REFERENCES topic(id) ON DELETE CASCADE)");
        jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_topic_review_due " +
                "ON topic_review_state(next_review_at)");
    }

    private void createProjectionSchema() {
        jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS knowledge_projection_job (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "topic_id INTEGER NOT NULL," +
                "entry_id INTEGER," +
                "status TEXT NOT NULL CHECK(status IN ('PENDING','PROCESSING','COMPLETED','FAILED'))," +
                "attempt_count INTEGER NOT NULL DEFAULT 0 CHECK(attempt_count >= 0)," +
                "last_error TEXT," +
                "created_at TEXT NOT NULL," +
                "updated_at TEXT NOT NULL," +
                "FOREIGN KEY(topic_id) REFERENCES topic(id) ON DELETE CASCADE," +
                "FOREIGN KEY(entry_id) REFERENCES knowledge_entry(id) ON DELETE CASCADE)");
        jdbcTemplate.execute("CREATE UNIQUE INDEX IF NOT EXISTS idx_knowledge_projection_entry " +
                "ON knowledge_projection_job(topic_id,entry_id) WHERE entry_id IS NOT NULL");
        jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_knowledge_projection_pending " +
                "ON knowledge_projection_job(status,updated_at)");
    }

    private void migrateLegacyValues() {
        jdbcTemplate.update("UPDATE topic SET " +
                "lifecycle_status='ACTIVE'," +
                "mastery_status=CASE status " +
                "WHEN 'LEARNING' THEN 'BUILDING' " +
                "WHEN 'REVIEWING' THEN 'REVIEWING' " +
                "WHEN 'MATURE' THEN 'MATURE' " +
                "ELSE 'EXPLORING' END");

        jdbcTemplate.update("UPDATE learning_task SET status=CASE status " +
                "WHEN 'TODO' THEN 'SUGGESTED' " +
                "WHEN 'LEARNING' THEN 'IN_PROGRESS' " +
                "WHEN 'REVIEWING' THEN 'IN_PROGRESS' " +
                "ELSE status END");
        jdbcTemplate.update("UPDATE learning_task SET completion_mode='LEGACY' " +
                "WHERE status='DONE' AND completion_mode IS NULL");
    }

    private void addColumnIfMissing(String table, String column, String definition) {
        if (!hasColumn(table, column)) {
            jdbcTemplate.execute("ALTER TABLE " + table + " ADD COLUMN " + column + " " + definition);
        }
    }

    private boolean hasColumn(String table, String column) {
        List<Map<String, Object>> columns = jdbcTemplate.queryForList("PRAGMA table_info(" + table + ")");
        for (Map<String, Object> metadata : columns) {
            if (column.equals(metadata.get("name"))) {
                return true;
            }
        }
        return false;
    }
}
