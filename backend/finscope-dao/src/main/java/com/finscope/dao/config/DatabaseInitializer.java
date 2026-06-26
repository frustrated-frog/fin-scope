package com.finscope.dao.config;

import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;

@Component
public class DatabaseInitializer implements InitializingBean {
    private final JdbcTemplate jdbcTemplate;
    private final String dataRoot;

    public DatabaseInitializer(JdbcTemplate jdbcTemplate,
                               @Value("${finscope.data-root:../data}") String dataRoot) {
        this.jdbcTemplate = jdbcTemplate;
        this.dataRoot = dataRoot;
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        Files.createDirectories(Paths.get(dataRoot));
        Files.createDirectories(Paths.get(dataRoot).resolve("vault/daily-briefs"));
        Files.createDirectories(Paths.get(dataRoot).resolve("vault/topics"));
        Files.createDirectories(Paths.get(dataRoot).resolve("vault/terms"));
        Files.createDirectories(Paths.get(dataRoot).resolve("vault/learning-path"));
        Files.createDirectories(Paths.get(dataRoot).resolve("raw"));
        Files.createDirectories(Paths.get(dataRoot).resolve("exports"));
        createSchema();
    }

    private void createSchema() {
        jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS source ("
                + "id INTEGER PRIMARY KEY AUTOINCREMENT,"
                + "name TEXT NOT NULL,"
                + "type TEXT NOT NULL,"
                + "url TEXT NOT NULL,"
                + "enabled INTEGER NOT NULL DEFAULT 1,"
                + "fetch_frequency_minutes INTEGER NOT NULL DEFAULT 60,"
                + "credibility INTEGER NOT NULL DEFAULT 3,"
                + "tags TEXT,"
                + "created_at TEXT NOT NULL,"
                + "updated_at TEXT NOT NULL)");
        jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS fetch_run ("
                + "id INTEGER PRIMARY KEY AUTOINCREMENT,"
                + "source_id INTEGER,"
                + "source_name TEXT,"
                + "status TEXT NOT NULL,"
                + "started_at TEXT NOT NULL,"
                + "ended_at TEXT,"
                + "success_count INTEGER NOT NULL DEFAULT 0,"
                + "duplicate_count INTEGER NOT NULL DEFAULT 0,"
                + "error_message TEXT)");
        jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS article ("
                + "id INTEGER PRIMARY KEY AUTOINCREMENT,"
                + "source_id INTEGER,"
                + "source_name TEXT,"
                + "title TEXT NOT NULL,"
                + "url TEXT NOT NULL,"
                + "published_at TEXT,"
                + "summary TEXT,"
                + "body TEXT,"
                + "category TEXT,"
                + "novelty_type TEXT,"
                + "novelty_reason TEXT,"
                + "url_fingerprint TEXT,"
                + "title_fingerprint TEXT,"
                + "body_simhash INTEGER,"
                + "fetched_at TEXT NOT NULL)");
        jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS insight_card ("
                + "id INTEGER PRIMARY KEY AUTOINCREMENT,"
                + "article_id INTEGER NOT NULL UNIQUE,"
                + "title TEXT NOT NULL,"
                + "source_name TEXT,"
                + "source_url TEXT,"
                + "published_at TEXT,"
                + "one_sentence_summary TEXT,"
                + "core_event TEXT,"
                + "importance TEXT,"
                + "impact_targets TEXT,"
                + "novelty_type TEXT,"
                + "novelty_reason TEXT,"
                + "follow_up_questions TEXT,"
                + "card_markdown TEXT NOT NULL,"
                + "background TEXT,"
                + "key_data TEXT,"
                + "timeline TEXT,"
                + "related_parties TEXT,"
                + "risk_factors TEXT,"
                + "future_outlook TEXT,"
                + "impact_on_investment TEXT,"
                + "impact_on_startup TEXT,"
                + "professional_insight TEXT,"
                + "facts TEXT,"
                + "reasoning TEXT,"
                + "opinions TEXT,"
                + "created_at TEXT NOT NULL,"
                + "updated_at TEXT NOT NULL)");
        jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS brief ("
                + "id INTEGER PRIMARY KEY AUTOINCREMENT,"
                + "brief_date TEXT NOT NULL UNIQUE,"
                + "title TEXT NOT NULL,"
                + "content TEXT NOT NULL,"
                + "markdown_path TEXT NOT NULL,"
                + "created_at TEXT NOT NULL)");
        jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS topic ("
                + "id INTEGER PRIMARY KEY AUTOINCREMENT,"
                + "name TEXT NOT NULL,"
                + "slug TEXT NOT NULL UNIQUE,"
                + "description TEXT,"
                + "status TEXT NOT NULL,"
                + "markdown_path TEXT,"
                + "terms TEXT,"
                + "learning_questions TEXT,"
                + "created_at TEXT NOT NULL,"
                + "updated_at TEXT NOT NULL)");
        ensureColumn("topic", "markdown_path", "TEXT");
        ensureColumn("topic", "terms", "TEXT");
        ensureColumn("topic", "learning_questions", "TEXT");

        // 深度解读字段
        ensureColumn("insight_card", "background", "TEXT");
        ensureColumn("insight_card", "key_data", "TEXT");
        ensureColumn("insight_card", "timeline", "TEXT");
        ensureColumn("insight_card", "related_parties", "TEXT");
        ensureColumn("insight_card", "risk_factors", "TEXT");
        ensureColumn("insight_card", "future_outlook", "TEXT");
        ensureColumn("insight_card", "impact_on_investment", "TEXT");
        ensureColumn("insight_card", "impact_on_startup", "TEXT");
        ensureColumn("insight_card", "professional_insight", "TEXT");
        ensureColumn("insight_card", "facts", "TEXT");
        ensureColumn("insight_card", "reasoning", "TEXT");
        ensureColumn("insight_card", "opinions", "TEXT");
        jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS topic_article ("
                + "topic_id INTEGER NOT NULL,"
                + "article_id INTEGER NOT NULL,"
                + "created_at TEXT NOT NULL,"
                + "PRIMARY KEY(topic_id, article_id))");
        jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS topic_brief ("
                + "topic_id INTEGER NOT NULL,"
                + "brief_id INTEGER NOT NULL,"
                + "created_at TEXT NOT NULL,"
                + "PRIMARY KEY(topic_id, brief_id))");
        jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS agent_run ("
                + "id INTEGER PRIMARY KEY AUTOINCREMENT,"
                + "node_name TEXT NOT NULL,"
                + "status TEXT NOT NULL,"
                + "input TEXT,"
                + "output TEXT,"
                + "error_message TEXT,"
                + "duration_ms INTEGER NOT NULL DEFAULT 0,"
                + "created_at TEXT NOT NULL)");
        jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS export_manifest ("
                + "id INTEGER PRIMARY KEY AUTOINCREMENT,"
                + "file_name TEXT NOT NULL,"
                + "manifest_json TEXT NOT NULL,"
                + "created_at TEXT NOT NULL)");
    }

    private void ensureColumn(String table, String column, String type) {
        List<Map<String, Object>> columns = jdbcTemplate.queryForList("PRAGMA table_info(" + table + ")");
        for (Map<String, Object> existing : columns) {
            if (column.equalsIgnoreCase(String.valueOf(existing.get("name")))) {
                return;
            }
        }
        jdbcTemplate.execute("ALTER TABLE " + table + " ADD COLUMN " + column + " " + type);
    }
}
