package com.finscope.dao.config;

import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;

@Component
public class DatabaseInitializer implements InitializingBean {
    @Resource
    private JdbcTemplate jdbcTemplate;
    @Value("${finscope.data-root:../data}")
    private String dataRoot;

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
        jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS async_task ("
                + "id TEXT PRIMARY KEY,"
                + "type TEXT NOT NULL,"
                + "status TEXT NOT NULL,"
                + "phase TEXT NOT NULL,"
                + "message TEXT,"
                + "request_url TEXT,"
                + "article_id INTEGER,"
                + "error_message TEXT,"
                + "created_at TEXT NOT NULL,"
                + "updated_at TEXT NOT NULL,"
                + "started_at TEXT,"
                + "ended_at TEXT)");
        jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_async_task_status ON async_task(status)");
        jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_async_task_article ON async_task(article_id)");
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
        jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS event_cluster ("
                + "id INTEGER PRIMARY KEY AUTOINCREMENT,"
                + "canonical_title TEXT NOT NULL,"
                + "canonical_event_key TEXT NOT NULL,"
                + "theme_code TEXT NOT NULL,"
                + "summary TEXT,"
                + "status TEXT NOT NULL,"
                + "first_seen_at TEXT NOT NULL,"
                + "last_seen_at TEXT NOT NULL,"
                + "last_meaningful_update_at TEXT,"
                + "importance_score INTEGER NOT NULL DEFAULT 0,"
                + "novelty_state TEXT NOT NULL,"
                + "evidence_count INTEGER NOT NULL DEFAULT 0,"
                + "article_count INTEGER NOT NULL DEFAULT 0,"
                + "created_at TEXT NOT NULL,"
                + "updated_at TEXT NOT NULL)");
        jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_event_cluster_theme ON event_cluster(theme_code)");
        jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_event_cluster_key ON event_cluster(canonical_event_key)");
        jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_event_cluster_seen ON event_cluster(last_seen_at)");
        jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS event_article_link ("
                + "event_id INTEGER NOT NULL,"
                + "article_id INTEGER NOT NULL,"
                + "relation_type TEXT NOT NULL,"
                + "match_score REAL NOT NULL,"
                + "novelty_type TEXT NOT NULL,"
                + "novelty_reason TEXT,"
                + "created_at TEXT NOT NULL,"
                + "PRIMARY KEY(event_id, article_id))");
        jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_event_article_article ON event_article_link(article_id)");
        jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_event_article_novelty ON event_article_link(novelty_type)");
        jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS evidence_item ("
                + "id INTEGER PRIMARY KEY AUTOINCREMENT,"
                + "event_id INTEGER NOT NULL,"
                + "article_id INTEGER NOT NULL,"
                + "source_tier TEXT NOT NULL,"
                + "evidence_type TEXT NOT NULL,"
                + "claim TEXT NOT NULL,"
                + "confidence INTEGER NOT NULL DEFAULT 0,"
                + "created_at TEXT NOT NULL)");
        jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_evidence_event ON evidence_item(event_id)");
        jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_evidence_article ON evidence_item(article_id)");
        jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS learning_task ("
                + "id INTEGER PRIMARY KEY AUTOINCREMENT,"
                + "event_id INTEGER,"
                + "theme_code TEXT NOT NULL,"
                + "question TEXT NOT NULL,"
                + "concepts TEXT,"
                + "difficulty TEXT NOT NULL,"
                + "status TEXT NOT NULL,"
                + "why_needed TEXT,"
                + "created_at TEXT NOT NULL,"
                + "updated_at TEXT NOT NULL)");
        jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_learning_task_event ON learning_task(event_id)");
        jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_learning_task_status ON learning_task(status)");
        jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS content_idea ("
                + "id INTEGER PRIMARY KEY AUTOINCREMENT,"
                + "event_id INTEGER,"
                + "theme_code TEXT NOT NULL,"
                + "title TEXT NOT NULL,"
                + "angle TEXT,"
                + "format TEXT NOT NULL,"
                + "audience TEXT,"
                + "score INTEGER NOT NULL DEFAULT 0,"
                + "score_reason TEXT,"
                + "outline TEXT,"
                + "status TEXT NOT NULL,"
                + "created_at TEXT NOT NULL,"
                + "updated_at TEXT NOT NULL)");
        jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_content_idea_event ON content_idea(event_id)");
        jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_content_idea_score ON content_idea(score)");
        jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_content_idea_status ON content_idea(status)");
        jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS research_run ("
                + "id INTEGER PRIMARY KEY AUTOINCREMENT,"
                + "run_date TEXT NOT NULL,"
                + "theme_codes TEXT NOT NULL,"
                + "source_count INTEGER NOT NULL DEFAULT 0,"
                + "fetched_source_count INTEGER NOT NULL DEFAULT 0,"
                + "article_count INTEGER NOT NULL DEFAULT 0,"
                + "event_count INTEGER NOT NULL DEFAULT 0,"
                + "evidence_count INTEGER NOT NULL DEFAULT 0,"
                + "learning_task_count INTEGER NOT NULL DEFAULT 0,"
                + "content_idea_count INTEGER NOT NULL DEFAULT 0,"
                + "brief_date TEXT,"
                + "status TEXT NOT NULL,"
                + "summary TEXT,"
                + "error_message TEXT,"
                + "created_at TEXT NOT NULL,"
                + "updated_at TEXT NOT NULL)");
        ensureColumn("research_run", "fetched_source_count", "INTEGER NOT NULL DEFAULT 0");
        ensureColumn("research_run", "article_count", "INTEGER NOT NULL DEFAULT 0");
        ensureColumn("research_run", "event_count", "INTEGER NOT NULL DEFAULT 0");
        ensureColumn("research_run", "evidence_count", "INTEGER NOT NULL DEFAULT 0");
        ensureColumn("research_run", "learning_task_count", "INTEGER NOT NULL DEFAULT 0");
        ensureColumn("research_run", "content_idea_count", "INTEGER NOT NULL DEFAULT 0");
        ensureColumn("research_run", "brief_date", "TEXT");
        jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_research_run_date ON research_run(run_date)");
        jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_research_run_status ON research_run(status)");
        jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS research_run_source ("
                + "run_id INTEGER NOT NULL,"
                + "source_id INTEGER,"
                + "source_name TEXT NOT NULL,"
                + "source_tier TEXT,"
                + "theme_codes TEXT,"
                + "credibility INTEGER,"
                + "enabled INTEGER NOT NULL DEFAULT 1,"
                + "position INTEGER NOT NULL DEFAULT 0,"
                + "PRIMARY KEY(run_id, position))");
        jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_research_run_source_run ON research_run_source(run_id)");
        jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_research_run_source_source ON research_run_source(source_id)");
        jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS agent_run ("
                + "id INTEGER PRIMARY KEY AUTOINCREMENT,"
                + "research_run_id INTEGER,"
                + "event_id INTEGER,"
                + "article_id INTEGER,"
                + "node_name TEXT NOT NULL,"
                + "status TEXT NOT NULL,"
                + "input TEXT,"
                + "output TEXT,"
                + "error_message TEXT,"
                + "duration_ms INTEGER NOT NULL DEFAULT 0,"
                + "created_at TEXT NOT NULL)");
        ensureColumn("agent_run", "research_run_id", "INTEGER");
        ensureColumn("agent_run", "event_id", "INTEGER");
        ensureColumn("agent_run", "article_id", "INTEGER");
        ensureColumn("agent_run", "step_id", "TEXT");
        ensureColumn("agent_run", "attempt", "INTEGER NOT NULL DEFAULT 1");
        ensureColumn("agent_run", "action_fingerprint", "TEXT");
        ensureColumn("agent_run", "input_hash", "TEXT");
        ensureColumn("agent_run", "output_hash", "TEXT");
        ensureColumn("agent_run", "error_type", "TEXT");
        ensureColumn("agent_run", "fallback_used", "INTEGER NOT NULL DEFAULT 0");
        ensureColumn("agent_run", "fallback_reason", "TEXT");
        ensureColumn("agent_run", "termination_reason", "TEXT");
        ensureColumn("agent_run", "progress_delta", "INTEGER NOT NULL DEFAULT 0");
        ensureColumn("agent_run", "budget_snapshot", "TEXT");
        ensureColumn("agent_run", "metadata_json", "TEXT");
        jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_agent_run_research ON agent_run(research_run_id)");
        jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_agent_run_event ON agent_run(event_id)");
        jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_agent_run_article ON agent_run(article_id)");
        jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_agent_run_fingerprint ON agent_run(action_fingerprint)");
        jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_agent_run_step ON agent_run(research_run_id, step_id)");
        jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_agent_run_status ON agent_run(status)");
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
