package com.finscope.dao.config;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.LocalDateTime;
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
        seedNewsCategories();
        seedStrategyPlaybooks();
    }

    private void createSchema() {
        jdbcTemplate.execute("PRAGMA journal_mode=WAL");
        jdbcTemplate.execute("PRAGMA busy_timeout=30000");
        jdbcTemplate.execute("PRAGMA foreign_keys=ON");
        jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS source ("
                + "id INTEGER PRIMARY KEY AUTOINCREMENT,"
                + "name TEXT NOT NULL,"
                + "type TEXT NOT NULL,"
                + "url TEXT NOT NULL,"
                + "enabled INTEGER NOT NULL DEFAULT 1,"
                + "fetch_frequency_minutes INTEGER NOT NULL DEFAULT 60,"
                + "scheduled_enabled INTEGER NOT NULL DEFAULT 0,"
                + "schedule_times TEXT,"
                + "max_items_per_run INTEGER NOT NULL DEFAULT 10,"
                + "credibility INTEGER NOT NULL DEFAULT 3,"
                + "tags TEXT,"
                + "created_at TEXT NOT NULL,"
                + "updated_at TEXT NOT NULL)");
        ensureColumn("source", "scheduled_enabled", "INTEGER NOT NULL DEFAULT 0");
        ensureColumn("source", "schedule_times", "TEXT");
        ensureColumn("source", "max_items_per_run", "INTEGER NOT NULL DEFAULT 10");
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
        jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS raw_snapshot ("
                + "id INTEGER PRIMARY KEY AUTOINCREMENT,"
                + "fetch_run_id INTEGER,source_id INTEGER,purpose TEXT NOT NULL,method TEXT NOT NULL,"
                + "request_url TEXT NOT NULL,final_url TEXT,request_headers_json TEXT,"
                + "status TEXT NOT NULL,http_status INTEGER,error_type TEXT,error_message TEXT,"
                + "content_type TEXT,charset_name TEXT,body_bytes INTEGER NOT NULL DEFAULT 0,"
                + "body_sha256 TEXT,body_path TEXT,attempt_count INTEGER NOT NULL DEFAULT 0,"
                + "duration_ms INTEGER NOT NULL DEFAULT 0,policy_version TEXT,parser_version TEXT,"
                + "fetched_at TEXT NOT NULL,parsed_at TEXT)");
        jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_raw_snapshot_fetch_run ON raw_snapshot(fetch_run_id)");
        jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_raw_snapshot_source_time ON raw_snapshot(source_id,fetched_at)");
        jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_raw_snapshot_hash ON raw_snapshot(body_sha256)");
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
                + "interpretation_source TEXT NOT NULL DEFAULT 'UNKNOWN',"
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
                + "analysis_sections TEXT,"
                + "created_at TEXT NOT NULL,"
                + "updated_at TEXT NOT NULL)");
        jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS news_category ("
                + "code TEXT PRIMARY KEY,"
                + "name TEXT NOT NULL,"
                + "classification_guidance TEXT NOT NULL,"
                + "enabled INTEGER NOT NULL DEFAULT 1,"
                + "display_order INTEGER NOT NULL DEFAULT 0,"
                + "created_at TEXT NOT NULL,"
                + "updated_at TEXT NOT NULL)");
        jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS news_item_classification ("
                + "item_id TEXT PRIMARY KEY,"
                + "status TEXT NOT NULL,"
                + "category_code TEXT,"
                + "confidence REAL,"
                + "reason TEXT,"
                + "model_name TEXT,"
                + "error_message TEXT,"
                + "manual_category_code TEXT,"
                + "manual_reason TEXT,"
                + "review_status TEXT,"
                + "reviewed_at TEXT,"
                + "created_at TEXT NOT NULL,"
                + "updated_at TEXT NOT NULL,"
                + "FOREIGN KEY(category_code) REFERENCES news_category(code))");
        ensureColumn("news_item_classification", "manual_category_code", "TEXT");
        ensureColumn("news_item_classification", "manual_reason", "TEXT");
        ensureColumn("news_item_classification", "review_status", "TEXT");
        ensureColumn("news_item_classification", "reviewed_at", "TEXT");
        jdbcTemplate.update("UPDATE news_item_classification SET review_status="
                + "CASE WHEN confidence < 0.70 THEN 'PENDING_REVIEW' ELSE 'AUTO_CONFIRMED' END "
                + "WHERE status='CLASSIFIED' AND review_status IS NULL");
        jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_news_classification_category "
                + "ON news_item_classification(status,category_code,updated_at)");
        jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_news_classification_review "
                + "ON news_item_classification(status,review_status,updated_at)");
        jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS radar_signal ("
                + "id INTEGER PRIMARY KEY AUTOINCREMENT,item_id TEXT NOT NULL UNIQUE,provider_code TEXT,source_name TEXT,"
                + "source_tier TEXT,category_code TEXT,title TEXT NOT NULL,content TEXT,url TEXT,published_at TEXT,"
                + "first_seen_at TEXT NOT NULL,last_seen_at TEXT NOT NULL,content_hash TEXT NOT NULL,status TEXT NOT NULL)");
        ensureColumn("radar_signal", "source_rank", "INTEGER");
        ensureColumn("radar_signal", "previous_source_rank", "INTEGER");
        ensureColumn("radar_signal", "source_weight", "REAL NOT NULL DEFAULT 0");
        jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_radar_signal_active ON radar_signal(status,last_seen_at)");
        jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_radar_signal_category ON radar_signal(category_code,last_seen_at)");
        jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS radar_refresh_run ("
                + "id INTEGER PRIMARY KEY AUTOINCREMENT,run_key TEXT NOT NULL UNIQUE,trigger_type TEXT NOT NULL,"
                + "status TEXT NOT NULL,started_at TEXT NOT NULL,completed_at TEXT,source_count INTEGER NOT NULL DEFAULT 0,"
                + "signal_count INTEGER NOT NULL DEFAULT 0,event_count INTEGER NOT NULL DEFAULT 0,warning TEXT,error TEXT)");
        jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_radar_refresh_run_status "
                + "ON radar_refresh_run(status,completed_at DESC)");
        jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS radar_refresh_step ("
                + "id INTEGER PRIMARY KEY AUTOINCREMENT,run_id INTEGER NOT NULL,step_code TEXT NOT NULL,"
                + "status TEXT NOT NULL,started_at TEXT NOT NULL,completed_at TEXT,input_count INTEGER NOT NULL DEFAULT 0,"
                + "output_count INTEGER NOT NULL DEFAULT 0,details TEXT,error TEXT,UNIQUE(run_id,step_code),"
                + "FOREIGN KEY(run_id) REFERENCES radar_refresh_run(id) ON DELETE CASCADE)");
        jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_radar_refresh_step_run ON radar_refresh_step(run_id,id)");
        jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS radar_event ("
                + "id INTEGER PRIMARY KEY AUTOINCREMENT,event_key TEXT NOT NULL UNIQUE,canonical_title TEXT NOT NULL,summary TEXT,"
                + "category_code TEXT,dashboard_category TEXT NOT NULL DEFAULT 'UNCLASSIFIED',status TEXT NOT NULL,first_seen_at TEXT NOT NULL,last_seen_at TEXT NOT NULL,"
                + "source_count INTEGER NOT NULL DEFAULT 0,signal_count INTEGER NOT NULL DEFAULT 0,"
                + "hotspot_score INTEGER NOT NULL DEFAULT 0,hotspot_explanation TEXT,hotspot_lifecycle_state TEXT,"
                + "priority_score INTEGER NOT NULL DEFAULT 0,score_explanation TEXT,watchlist_relevance INTEGER NOT NULL DEFAULT 0,"
                + "watchlist_explanation TEXT,uncertainty TEXT,next_observation TEXT,updated_at TEXT NOT NULL)");
        ensureColumn("radar_event", "hotspot_score", "INTEGER NOT NULL DEFAULT 0");
        ensureColumn("radar_event", "hotspot_explanation", "TEXT");
        ensureColumn("radar_event", "hotspot_lifecycle_state", "TEXT");
        ensureColumn("radar_event", "confidence_score", "INTEGER NOT NULL DEFAULT 0");
        ensureColumn("radar_event", "confidence_explanation", "TEXT");
        ensureColumn("radar_event", "score_version", "TEXT NOT NULL DEFAULT 'HOTSPOT_V1'");
        ensureColumn("radar_event", "dashboard_category", "TEXT NOT NULL DEFAULT 'UNCLASSIFIED'");
        ensureColumn("radar_event", "evidence_status", "TEXT");
        ensureColumn("radar_event", "evidence_summary", "TEXT");
        ensureColumn("radar_event", "evidence_warning", "TEXT");
        ensureColumn("radar_event", "evidence_fingerprint", "TEXT");
        ensureColumn("radar_event", "evidence_count", "INTEGER NOT NULL DEFAULT 0");
        ensureColumn("radar_event", "evidence_source_count", "INTEGER NOT NULL DEFAULT 0");
        ensureColumn("radar_event", "evidence_updated_at", "TEXT");
        jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_radar_event_rank ON radar_event(status,category_code,hotspot_score DESC,priority_score DESC,last_seen_at DESC)");
        jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_radar_event_dashboard_rank ON radar_event(status,dashboard_category,hotspot_score DESC,last_seen_at DESC)");
        jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS radar_event_snapshot ("
                + "id INTEGER PRIMARY KEY AUTOINCREMENT,event_id INTEGER NOT NULL,snapshot_at TEXT NOT NULL,"
                + "signal_count INTEGER NOT NULL DEFAULT 0,independent_source_count INTEGER NOT NULL DEFAULT 0,"
                + "velocity_score REAL NOT NULL DEFAULT 0,hotness_score INTEGER NOT NULL DEFAULT 0,"
                + "lifecycle_state TEXT NOT NULL,explanation TEXT,UNIQUE(event_id,snapshot_at),"
                + "FOREIGN KEY(event_id) REFERENCES radar_event(id) ON DELETE CASCADE)");
        ensureColumn("radar_event_snapshot", "confirmation_score", "REAL NOT NULL DEFAULT 0");
        ensureColumn("radar_event_snapshot", "freshness_score", "REAL NOT NULL DEFAULT 0");
        ensureColumn("radar_event_snapshot", "rank_trend_score", "REAL NOT NULL DEFAULT 0");
        ensureColumn("radar_event_snapshot", "confidence_score", "INTEGER NOT NULL DEFAULT 0");
        ensureColumn("radar_event_snapshot", "score_version", "TEXT NOT NULL DEFAULT 'HOTSPOT_V1'");
        jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_radar_event_snapshot_time "
                + "ON radar_event_snapshot(event_id,snapshot_at DESC)");
        jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS radar_event_signal ("
                + "event_id INTEGER NOT NULL,signal_id INTEGER NOT NULL,relation_type TEXT NOT NULL,match_score REAL NOT NULL,"
                + "match_reason TEXT,PRIMARY KEY(event_id,signal_id),"
                + "FOREIGN KEY(event_id) REFERENCES radar_event(id) ON DELETE CASCADE,"
                + "FOREIGN KEY(signal_id) REFERENCES radar_signal(id) ON DELETE CASCADE)");
        jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_radar_event_signal_signal ON radar_event_signal(signal_id)");
        jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS radar_pair_decision ("
                + "pair_key TEXT PRIMARY KEY,left_fingerprint TEXT NOT NULL,right_fingerprint TEXT NOT NULL,"
                + "same_event INTEGER NOT NULL,confidence REAL NOT NULL,reason TEXT,decision_source TEXT NOT NULL,"
                + "created_at TEXT NOT NULL,updated_at TEXT NOT NULL)");
        jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_radar_pair_decision_updated ON radar_pair_decision(updated_at)");
        jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS radar_evidence ("
                + "id INTEGER PRIMARY KEY AUTOINCREMENT,event_id INTEGER NOT NULL,tool_code TEXT NOT NULL,"
                + "evidence_type TEXT,title TEXT NOT NULL,summary TEXT,url TEXT,source_name TEXT,source_tier TEXT,"
                + "published_at TEXT,created_at TEXT NOT NULL,"
                + "FOREIGN KEY(event_id) REFERENCES radar_event(id) ON DELETE CASCADE)");
        jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_radar_evidence_event ON radar_evidence(event_id,id DESC)");
        jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS radar_event_interpretation("
                + "id INTEGER PRIMARY KEY AUTOINCREMENT,event_id INTEGER NOT NULL,event_fingerprint TEXT NOT NULL,"
                + "status TEXT NOT NULL,result_json TEXT,failure_code TEXT,failure_message TEXT,duration_ms INTEGER,"
                + "created_at TEXT NOT NULL,started_at TEXT,completed_at TEXT,UNIQUE(event_id,event_fingerprint),"
                + "FOREIGN KEY(event_id) REFERENCES radar_event(id) ON DELETE CASCADE)");
        jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_radar_interpretation_event "
                + "ON radar_event_interpretation(event_id,id DESC)");
        jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS radar_event_user_state("
                + "event_id INTEGER PRIMARY KEY,read_at TEXT,followed INTEGER NOT NULL DEFAULT 0,"
                + "disposition TEXT NOT NULL DEFAULT 'ACTIVE',last_viewed_fingerprint TEXT,updated_at TEXT NOT NULL,"
                + "FOREIGN KEY(event_id) REFERENCES radar_event(id) ON DELETE CASCADE)");
        jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_radar_user_state_filter "
                + "ON radar_event_user_state(disposition,followed,read_at)");
        jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS radar_event_observation("
                + "id INTEGER PRIMARY KEY AUTOINCREMENT,event_id INTEGER NOT NULL,content TEXT NOT NULL,"
                + "normalized_content TEXT NOT NULL,status TEXT NOT NULL,source TEXT NOT NULL,created_at TEXT NOT NULL,"
                + "completed_at TEXT,updated_at TEXT NOT NULL,UNIQUE(event_id,normalized_content,source),"
                + "FOREIGN KEY(event_id) REFERENCES radar_event(id) ON DELETE CASCADE)");
        jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_radar_observation_event "
                + "ON radar_event_observation(event_id,status,created_at)");
        jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS radar_event_timeline("
                + "id INTEGER PRIMARY KEY AUTOINCREMENT,event_id INTEGER NOT NULL,event_fingerprint TEXT NOT NULL,"
                + "event_type TEXT NOT NULL,title TEXT NOT NULL,summary TEXT,reference_type TEXT,reference_id INTEGER,"
                + "occurred_at TEXT NOT NULL,created_at TEXT NOT NULL,"
                + "UNIQUE(event_id,event_fingerprint,event_type,reference_type,reference_id),"
                + "FOREIGN KEY(event_id) REFERENCES radar_event(id) ON DELETE CASCADE)");
        jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_radar_timeline_event "
                + "ON radar_event_timeline(event_id,occurred_at DESC,id DESC)");
        jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS radar_event_research_link("
                + "id INTEGER PRIMARY KEY AUTOINCREMENT,event_id INTEGER NOT NULL,research_run_id INTEGER NOT NULL,"
                + "question_snapshot TEXT,created_at TEXT NOT NULL,UNIQUE(event_id,research_run_id),"
                + "FOREIGN KEY(event_id) REFERENCES radar_event(id) ON DELETE CASCADE)");
        jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_radar_research_link_event "
                + "ON radar_event_research_link(event_id,created_at DESC)");
        jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS radar_event_notification("
                + "id INTEGER PRIMARY KEY AUTOINCREMENT,event_id INTEGER,notification_type TEXT NOT NULL,"
                + "fingerprint TEXT NOT NULL,title TEXT NOT NULL,message TEXT,read_at TEXT,created_at TEXT NOT NULL,"
                + "UNIQUE(notification_type,fingerprint),"
                + "FOREIGN KEY(event_id) REFERENCES radar_event(id) ON DELETE CASCADE)");
        jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_radar_notification_unread "
                + "ON radar_event_notification(read_at,created_at DESC)");
        jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS major_event ("
                + "id INTEGER PRIMARY KEY AUTOINCREMENT,origin_type TEXT NOT NULL,origin_key TEXT NOT NULL,"
                + "title TEXT NOT NULL,summary TEXT,source_name TEXT,source_url TEXT,category_code TEXT,"
                + "occurred_date TEXT NOT NULL,note TEXT,created_at TEXT NOT NULL,updated_at TEXT NOT NULL,"
                + "UNIQUE(origin_type,origin_key))");
        jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_major_event_timeline "
                + "ON major_event(occurred_date DESC,created_at DESC)");
        jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_major_event_filters "
                + "ON major_event(origin_type,category_code,occurred_date DESC)");
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
        ensureColumn("insight_card", "analysis_sections", "TEXT");
        ensureColumn("insight_card", "interpretation_source", "TEXT NOT NULL DEFAULT 'UNKNOWN'");
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
        jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_event_cluster_status_theme_seen "
                + "ON event_cluster(status, theme_code, last_seen_at)");
        jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS event_article_link ("
                + "event_id INTEGER NOT NULL,"
                + "article_id INTEGER NOT NULL,"
                + "relation_type TEXT NOT NULL,"
                + "match_score REAL NOT NULL,"
                + "novelty_type TEXT NOT NULL,"
                + "novelty_reason TEXT,"
                + "created_at TEXT NOT NULL,"
                + "PRIMARY KEY(event_id, article_id),"
                + "UNIQUE(article_id),"
                + "FOREIGN KEY(event_id) REFERENCES event_cluster(id) ON DELETE CASCADE,"
                + "FOREIGN KEY(article_id) REFERENCES article(id) ON DELETE CASCADE)");
        jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_event_article_article ON event_article_link(article_id)");
        jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_event_article_novelty ON event_article_link(novelty_type)");
        jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS evidence_item ("
                + "id INTEGER PRIMARY KEY AUTOINCREMENT,"
                + "event_id INTEGER NOT NULL,"
                + "article_id INTEGER NOT NULL,"
                + "source_tier TEXT NOT NULL,"
                + "evidence_type TEXT NOT NULL,"
                + "claim TEXT NOT NULL,"
                + "claim_key TEXT,"
                + "confidence INTEGER NOT NULL DEFAULT 0,"
                + "created_at TEXT NOT NULL,"
                + "FOREIGN KEY(event_id) REFERENCES event_cluster(id) ON DELETE CASCADE,"
                + "FOREIGN KEY(article_id) REFERENCES article(id) ON DELETE CASCADE)");
        ensureColumn("evidence_item", "claim_key", "TEXT");
        jdbcTemplate.update("UPDATE evidence_item SET claim_key = lower(trim(replace(replace(claim, char(10), ' '), char(13), ' '))) "
                + "WHERE claim_key IS NULL OR trim(claim_key) = ''");
        jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_evidence_event ON evidence_item(event_id)");
        jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_evidence_article ON evidence_item(article_id)");
        createUniqueIndexIfNoDuplicates("uq_evidence_event_article_claim", "evidence_item",
                "event_id, article_id, claim_key");
        jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS event_evidence_thesis (id INTEGER PRIMARY KEY AUTOINCREMENT,event_id INTEGER NOT NULL,statement TEXT NOT NULL,kind TEXT NOT NULL,status TEXT NOT NULL,rationale TEXT,evidence_gap TEXT,created_at TEXT NOT NULL,updated_at TEXT NOT NULL,UNIQUE(event_id, statement),FOREIGN KEY(event_id) REFERENCES event_cluster(id) ON DELETE CASCADE)");
        jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS event_evidence_thesis_link (thesis_id INTEGER NOT NULL,evidence_id INTEGER NOT NULL,PRIMARY KEY(thesis_id,evidence_id),FOREIGN KEY(thesis_id) REFERENCES event_evidence_thesis(id) ON DELETE CASCADE,FOREIGN KEY(evidence_id) REFERENCES evidence_item(id) ON DELETE CASCADE)");
        jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_event_evidence_thesis_event_status ON event_evidence_thesis(event_id,status)");
        jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_event_evidence_thesis_link_evidence ON event_evidence_thesis_link(evidence_id)");
        createUniqueIndexIfNoDuplicates("uq_event_article_single_event", "event_article_link", "article_id");
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
                + "mode TEXT NOT NULL DEFAULT 'DEEP',"
                + "theme_codes TEXT NOT NULL,"
                + "source_count INTEGER NOT NULL DEFAULT 0,"
                + "fetched_source_count INTEGER NOT NULL DEFAULT 0,"
                + "article_count INTEGER NOT NULL DEFAULT 0,"
                + "event_count INTEGER NOT NULL DEFAULT 0,"
                + "evidence_count INTEGER NOT NULL DEFAULT 0,"
                + "learning_task_count INTEGER NOT NULL DEFAULT 0,"
                + "content_idea_count INTEGER NOT NULL DEFAULT 0,"
                + "brief_date TEXT,"
                + "thesis_id INTEGER,"
                + "status TEXT NOT NULL,"
                + "summary TEXT,"
                + "error_message TEXT,"
                + "created_at TEXT NOT NULL,"
                + "updated_at TEXT NOT NULL)");
        ensureColumn("research_run", "fetched_source_count", "INTEGER NOT NULL DEFAULT 0");
        ensureColumn("research_run", "mode", "TEXT NOT NULL DEFAULT 'DEEP'");
        ensureColumn("research_run", "article_count", "INTEGER NOT NULL DEFAULT 0");
        ensureColumn("research_run", "event_count", "INTEGER NOT NULL DEFAULT 0");
        ensureColumn("research_run", "evidence_count", "INTEGER NOT NULL DEFAULT 0");
        ensureColumn("research_run", "learning_task_count", "INTEGER NOT NULL DEFAULT 0");
        ensureColumn("research_run", "content_idea_count", "INTEGER NOT NULL DEFAULT 0");
        ensureColumn("research_run", "brief_date", "TEXT");
        ensureColumn("research_run", "thesis_id", "INTEGER");
        jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_research_run_date ON research_run(run_date)");
        jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_research_run_status ON research_run(status)");
        jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_research_run_thesis ON research_run(thesis_id)");
        jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS research_runtime_checkpoint ("
                + "research_run_id INTEGER PRIMARY KEY,"
                + "state_version INTEGER NOT NULL DEFAULT 0,"
                + "phase TEXT NOT NULL,"
                + "current_node TEXT,"
                + "status TEXT NOT NULL,"
                + "iteration INTEGER NOT NULL DEFAULT 0,"
                + "consumed_actions INTEGER NOT NULL DEFAULT 0,"
                + "max_actions INTEGER NOT NULL,"
                + "no_progress_count INTEGER NOT NULL DEFAULT 0,"
                + "last_state_hash TEXT,"
                + "resume_count INTEGER NOT NULL DEFAULT 0,"
                + "termination_reason TEXT,"
                + "last_error TEXT,"
                + "created_at TEXT NOT NULL,"
                + "updated_at TEXT NOT NULL)");
        jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_research_runtime_status "
                + "ON research_runtime_checkpoint(status,updated_at)");
        jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS research_runtime_event ("
                + "id INTEGER PRIMARY KEY AUTOINCREMENT,"
                + "research_run_id INTEGER NOT NULL,"
                + "sequence_no INTEGER NOT NULL,"
                + "event_type TEXT NOT NULL,"
                + "node_id TEXT,"
                + "status TEXT NOT NULL,"
                + "action_fingerprint TEXT,"
                + "input_summary TEXT,"
                + "output_summary TEXT,"
                + "state_hash TEXT,"
                + "progress_delta INTEGER NOT NULL DEFAULT 0,"
                + "error_type TEXT,"
                + "error_message TEXT,"
                + "created_at TEXT NOT NULL,"
                + "UNIQUE(research_run_id,sequence_no))");
        jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_research_runtime_event_run "
                + "ON research_runtime_event(research_run_id,sequence_no)");
        jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_research_runtime_event_action "
                + "ON research_runtime_event(research_run_id,action_fingerprint,event_type)");
        jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS research_mission ("
                + "research_run_id INTEGER PRIMARY KEY,"
                + "goal TEXT NOT NULL,"
                + "subject TEXT,"
                + "scope_summary TEXT NOT NULL,"
                + "success_criteria TEXT NOT NULL,"
                + "research_type TEXT,"
                + "method_codes TEXT,"
                + "required_evidence TEXT,"
                + "required_calculations TEXT,"
                + "counter_checks TEXT,"
                + "completion_criteria TEXT,"
                + "status TEXT NOT NULL,"
                + "planning_mode TEXT NOT NULL,"
                + "plan_version INTEGER NOT NULL DEFAULT 1,"
                + "max_actions INTEGER NOT NULL,"
                + "active_task_key TEXT,"
                + "fallback_reason TEXT,"
                + "fallback_detail TEXT,"
                + "created_at TEXT NOT NULL,"
                + "updated_at TEXT NOT NULL,"
                + "FOREIGN KEY(research_run_id) REFERENCES research_run(id) ON DELETE CASCADE)");
        ensureColumn("research_mission", "fallback_detail", "TEXT");
        ensureColumn("research_mission", "research_type", "TEXT");
        ensureColumn("research_mission", "method_codes", "TEXT");
        ensureColumn("research_mission", "required_evidence", "TEXT");
        ensureColumn("research_mission", "required_calculations", "TEXT");
        ensureColumn("research_mission", "counter_checks", "TEXT");
        ensureColumn("research_mission", "completion_criteria", "TEXT");
        jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_research_mission_status "
                + "ON research_mission(status,updated_at)");
        jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS research_mission_task ("
                + "id INTEGER PRIMARY KEY AUTOINCREMENT,"
                + "research_run_id INTEGER NOT NULL,"
                + "task_key TEXT NOT NULL,"
                + "title TEXT NOT NULL,"
                + "question TEXT NOT NULL,"
                + "task_type TEXT NOT NULL,"
                + "tool_code TEXT NOT NULL,"
                + "intent TEXT NOT NULL,"
                + "status TEXT NOT NULL,"
                + "dependencies TEXT,"
                + "parallel_group TEXT,"
                + "query_text TEXT,"
                + "rationale TEXT,"
                + "expected_evidence TEXT,"
                + "output_summary TEXT,"
                + "evidence_delta INTEGER NOT NULL DEFAULT 0,"
                + "source_delta INTEGER NOT NULL DEFAULT 0,"
                + "skip_reason TEXT,"
                + "started_at TEXT,"
                + "ended_at TEXT,"
                + "created_at TEXT NOT NULL,"
                + "updated_at TEXT NOT NULL,"
                + "UNIQUE(research_run_id,task_key),"
                + "FOREIGN KEY(research_run_id) REFERENCES research_run(id) ON DELETE CASCADE)");
        jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_research_mission_task_run "
                + "ON research_mission_task(research_run_id,id)");
        jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_research_mission_task_status "
                + "ON research_mission_task(research_run_id,status)");
        jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS research_mission_gap ("
                + "id INTEGER PRIMARY KEY AUTOINCREMENT,"
                + "research_run_id INTEGER NOT NULL,"
                + "assessment_index INTEGER NOT NULL,"
                + "after_task_key TEXT,"
                + "sufficient INTEGER NOT NULL,"
                + "evidence_count INTEGER NOT NULL,"
                + "source_count INTEGER NOT NULL,"
                + "support_count INTEGER NOT NULL,"
                + "counter_count INTEGER NOT NULL,"
                + "warnings TEXT,"
                + "recommended_intent TEXT NOT NULL,"
                + "state_hash TEXT NOT NULL,"
                + "created_at TEXT NOT NULL,"
                + "UNIQUE(research_run_id,assessment_index),"
                + "FOREIGN KEY(research_run_id) REFERENCES research_run(id) ON DELETE CASCADE)");
        jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_research_mission_gap_run "
                + "ON research_mission_gap(research_run_id,assessment_index)");
        jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS research_agent_state ("
                + "research_run_id INTEGER PRIMARY KEY,"
                + "status TEXT NOT NULL,"
                + "state_version INTEGER NOT NULL DEFAULT 0,"
                + "current_subgoal TEXT,"
                + "plan_summary TEXT,"
                + "memory_summary TEXT,"
                + "evidence_summary TEXT,"
                + "attempted_fingerprints TEXT,"
                + "last_observation_id INTEGER,"
                + "decision_count INTEGER NOT NULL DEFAULT 0,"
                + "replan_count INTEGER NOT NULL DEFAULT 0,"
                + "no_progress_count INTEGER NOT NULL DEFAULT 0,"
                + "finish_rejection_count INTEGER NOT NULL DEFAULT 0,"
                + "fallback_count INTEGER NOT NULL DEFAULT 0,"
                + "created_at TEXT NOT NULL,"
                + "updated_at TEXT NOT NULL,"
                + "FOREIGN KEY(research_run_id) REFERENCES research_run(id) ON DELETE CASCADE)");
        jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS research_agent_decision ("
                + "id INTEGER PRIMARY KEY AUTOINCREMENT,"
                + "research_run_id INTEGER NOT NULL,"
                + "iteration INTEGER NOT NULL,"
                + "decision_type TEXT NOT NULL,"
                + "current_subgoal TEXT NOT NULL,"
                + "mission_task_key TEXT,"
                + "tool_code TEXT,"
                + "arguments_json TEXT,"
                + "target_gap TEXT,"
                + "expected_observation TEXT,"
                + "decision_summary TEXT NOT NULL,"
                + "confidence REAL NOT NULL,"
                + "decision_mode TEXT NOT NULL,"
                + "action_fingerprint TEXT,"
                + "status TEXT NOT NULL,"
                + "validation_error TEXT,"
                + "created_at TEXT NOT NULL,"
                + "updated_at TEXT NOT NULL,"
                + "UNIQUE(research_run_id,iteration),"
                + "FOREIGN KEY(research_run_id) REFERENCES research_run(id) ON DELETE CASCADE)");
        ensureColumn("research_agent_decision", "mission_task_key", "TEXT");
        jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_research_agent_decision_run "
                + "ON research_agent_decision(research_run_id,iteration)");
        jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS research_tool_observation ("
                + "id INTEGER PRIMARY KEY AUTOINCREMENT,"
                + "research_run_id INTEGER NOT NULL,"
                + "decision_id INTEGER NOT NULL UNIQUE,"
                + "tool_code TEXT NOT NULL,"
                + "status TEXT NOT NULL,"
                + "observation_summary TEXT NOT NULL,"
                + "new_information TEXT,"
                + "evidence_delta INTEGER NOT NULL DEFAULT 0,"
                + "source_delta INTEGER NOT NULL DEFAULT 0,"
                + "data_refs TEXT,"
                + "error_type TEXT,"
                + "retryable INTEGER NOT NULL DEFAULT 0,"
                + "attempt_count INTEGER NOT NULL DEFAULT 1,"
                + "state_hash TEXT NOT NULL,"
                + "created_at TEXT NOT NULL,"
                + "FOREIGN KEY(research_run_id) REFERENCES research_run(id) ON DELETE CASCADE,"
                + "FOREIGN KEY(decision_id) REFERENCES research_agent_decision(id) ON DELETE CASCADE)");
        ensureColumn("research_tool_observation", "attempt_count", "INTEGER NOT NULL DEFAULT 1");
        jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_research_tool_observation_run "
                + "ON research_tool_observation(research_run_id,id)");
        jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS research_search_evidence ("
                + "id INTEGER PRIMARY KEY AUTOINCREMENT,"
                + "research_run_id INTEGER NOT NULL,"
                + "decision_id INTEGER NOT NULL,"
                + "provider TEXT NOT NULL,"
                + "query_text TEXT NOT NULL,"
                + "intent TEXT NOT NULL,"
                + "title TEXT NOT NULL,"
                + "url TEXT NOT NULL,"
                + "content TEXT,"
                + "search_snippet TEXT,"
                + "content_origin TEXT NOT NULL DEFAULT 'SEARCH_SNIPPET',"
                + "extraction_method TEXT,"
                + "fetch_status TEXT NOT NULL DEFAULT 'NOT_ATTEMPTED',"
                + "content_char_count INTEGER NOT NULL DEFAULT 0,"
                + "fetched_at TEXT,"
                + "source_domain TEXT,"
                + "source_tier TEXT NOT NULL,"
                + "relevance_score REAL NOT NULL DEFAULT 0,"
                + "published_at TEXT,"
                + "created_at TEXT NOT NULL,"
                + "UNIQUE(research_run_id,url),"
                + "FOREIGN KEY(research_run_id) REFERENCES research_run(id) ON DELETE CASCADE,"
                + "FOREIGN KEY(decision_id) REFERENCES research_agent_decision(id) ON DELETE CASCADE)");
        ensureColumn("research_search_evidence", "search_snippet", "TEXT");
        ensureColumn("research_search_evidence", "content_origin", "TEXT NOT NULL DEFAULT 'SEARCH_SNIPPET'");
        ensureColumn("research_search_evidence", "extraction_method", "TEXT");
        ensureColumn("research_search_evidence", "fetch_status", "TEXT NOT NULL DEFAULT 'NOT_ATTEMPTED'");
        ensureColumn("research_search_evidence", "content_char_count", "INTEGER NOT NULL DEFAULT 0");
        ensureColumn("research_search_evidence", "fetched_at", "TEXT");
        jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_research_search_evidence_run "
                + "ON research_search_evidence(research_run_id,relevance_score DESC)");
        jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS research_evaluation ("
                + "id INTEGER PRIMARY KEY AUTOINCREMENT,"
                + "research_run_id INTEGER NOT NULL,"
                + "evaluator_version TEXT NOT NULL,"
                + "input_fingerprint TEXT NOT NULL,"
                + "score INTEGER NOT NULL,"
                + "gate_status TEXT NOT NULL,"
                + "summary TEXT,"
                + "critical_issues TEXT,"
                + "created_at TEXT NOT NULL,"
                + "UNIQUE(research_run_id,evaluator_version,input_fingerprint),"
                + "FOREIGN KEY(research_run_id) REFERENCES research_run(id) ON DELETE CASCADE)");
        jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_research_evaluation_run "
                + "ON research_evaluation(research_run_id,created_at DESC)");
        jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS research_evaluation_metric ("
                + "evaluation_id INTEGER NOT NULL,"
                + "metric_code TEXT NOT NULL,"
                + "label TEXT NOT NULL,"
                + "score INTEGER NOT NULL,"
                + "max_score INTEGER NOT NULL,"
                + "status TEXT NOT NULL,"
                + "evidence TEXT,"
                + "recommendation TEXT,"
                + "PRIMARY KEY(evaluation_id,metric_code),"
                + "FOREIGN KEY(evaluation_id) REFERENCES research_evaluation(id) ON DELETE CASCADE)");
        jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS research_run_output ("
                + "id INTEGER PRIMARY KEY AUTOINCREMENT,research_run_id INTEGER NOT NULL,output_type TEXT NOT NULL,"
                + "output_id INTEGER NOT NULL,created_at TEXT NOT NULL,UNIQUE(research_run_id,output_type,output_id))");
        jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_research_run_output_run ON research_run_output(research_run_id)");
        jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS research_thesis ("
                + "id INTEGER PRIMARY KEY AUTOINCREMENT,"
                + "question TEXT NOT NULL,"
                + "subject_type TEXT NOT NULL,"
                + "subject_name TEXT NOT NULL,"
                + "subject_code TEXT,"
                + "status TEXT NOT NULL,"
                + "conclusion TEXT,"
                + "confidence TEXT,"
                + "next_validation TEXT,"
                + "created_at TEXT NOT NULL,"
                + "updated_at TEXT NOT NULL)");
        jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_research_thesis_status ON research_thesis(status)");
        jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_research_thesis_subject "
                + "ON research_thesis(subject_type, subject_code)");
        jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS thesis_finding ("
                + "id INTEGER PRIMARY KEY AUTOINCREMENT,"
                + "thesis_id INTEGER NOT NULL,"
                + "research_run_id INTEGER,"
                + "stance TEXT NOT NULL,"
                + "summary TEXT NOT NULL,"
                + "evidence_id INTEGER,"
                + "created_at TEXT NOT NULL,"
                + "updated_at TEXT NOT NULL)");
        ensureColumn("thesis_finding", "research_run_id", "INTEGER");
        jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_thesis_finding_thesis ON thesis_finding(thesis_id)");
        jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_thesis_finding_run ON thesis_finding(research_run_id)");
        jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_thesis_finding_stance ON thesis_finding(stance)");
        jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS research_report ("
                + "id INTEGER PRIMARY KEY AUTOINCREMENT,"
                + "research_run_id INTEGER NOT NULL UNIQUE,"
                + "thesis_id INTEGER,"
                + "report_type TEXT NOT NULL,"
                + "status TEXT NOT NULL,"
                + "title TEXT NOT NULL,"
                + "conclusion TEXT NOT NULL,"
                + "conclusion_direction TEXT NOT NULL,"
                + "confidence TEXT NOT NULL,"
                + "executive_summary TEXT NOT NULL,"
                + "content_markdown TEXT NOT NULL,"
                + "markdown_path TEXT NOT NULL,"
                + "generation_mode TEXT NOT NULL,"
                + "warning_message TEXT,"
                + "evidence_count INTEGER NOT NULL DEFAULT 0,"
                + "source_count INTEGER NOT NULL DEFAULT 0,"
                + "character_count INTEGER NOT NULL DEFAULT 0,"
                + "created_at TEXT NOT NULL,"
                + "updated_at TEXT NOT NULL)");
        jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_research_report_thesis ON research_report(thesis_id)");
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
        jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS research_run_plan ("
                + "id INTEGER PRIMARY KEY AUTOINCREMENT,"
                + "research_run_id INTEGER NOT NULL,"
                + "step_id TEXT NOT NULL,"
                + "title TEXT NOT NULL,"
                + "step_type TEXT NOT NULL,"
                + "executor TEXT NOT NULL,"
                + "status TEXT NOT NULL,"
                + "dependencies TEXT,"
                + "input_summary TEXT,"
                + "output_summary TEXT,"
                + "error_type TEXT,"
                + "error_message TEXT,"
                + "fallback_used INTEGER NOT NULL DEFAULT 0,"
                + "fallback_reason TEXT,"
                + "termination_reason TEXT,"
                + "attempt INTEGER NOT NULL DEFAULT 0,"
                + "max_attempts INTEGER NOT NULL DEFAULT 1,"
                + "progress_delta INTEGER NOT NULL DEFAULT 0,"
                + "started_at TEXT,"
                + "ended_at TEXT,"
                + "created_at TEXT NOT NULL,"
                + "updated_at TEXT NOT NULL,"
                + "metadata_json TEXT,"
                + "UNIQUE(research_run_id, step_id))");
        jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_research_run_plan_run "
                + "ON research_run_plan(research_run_id)");
        jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_research_run_plan_status "
                + "ON research_run_plan(status)");
        jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS fetch_batch ("
                + "id INTEGER PRIMARY KEY AUTOINCREMENT,"
                + "source_id INTEGER,"
                + "source_name TEXT,"
                + "trigger_type TEXT NOT NULL,"
                + "status TEXT NOT NULL,"
                + "started_at TEXT NOT NULL,"
                + "ended_at TEXT,"
                + "lookback_days INTEGER NOT NULL DEFAULT 3,"
                + "max_items_requested INTEGER NOT NULL DEFAULT 10,"
                + "raw_item_count INTEGER NOT NULL DEFAULT 0,"
                + "candidate_count INTEGER NOT NULL DEFAULT 0,"
                + "agent_reviewed_count INTEGER NOT NULL DEFAULT 0,"
                + "duplicate_count INTEGER NOT NULL DEFAULT 0,"
                + "low_value_count INTEGER NOT NULL DEFAULT 0,"
                + "error_message TEXT,"
                + "batch_summary_json TEXT,"
                + "batch_summary_text TEXT,"
                + "created_at TEXT NOT NULL,"
                + "updated_at TEXT NOT NULL)");
        jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_fetch_batch_source ON fetch_batch(source_id)");
        jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_fetch_batch_status ON fetch_batch(status)");
        jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_fetch_batch_started ON fetch_batch(started_at)");
        jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS intake_candidate ("
                + "id INTEGER PRIMARY KEY AUTOINCREMENT,"
                + "batch_id INTEGER NOT NULL,"
                + "source_id INTEGER,"
                + "source_name TEXT,"
                + "source_type TEXT,"
                + "original_title TEXT,"
                + "original_url TEXT,"
                + "original_summary TEXT,"
                + "original_body TEXT,"
                + "content_type TEXT,"
                + "extraction_method TEXT,"
                + "extraction_quality_score INTEGER NOT NULL DEFAULT 0,"
                + "published_at TEXT,"
                + "fetched_at TEXT NOT NULL,"
                + "chinese_title TEXT,"
                + "decision_summary TEXT,"
                + "key_facts_json TEXT,"
                + "why_it_matters TEXT,"
                + "novelty_judgment TEXT,"
                + "risk_flags_json TEXT,"
                + "agent_score INTEGER NOT NULL DEFAULT 0,"
                + "agent_recommendation TEXT,"
                + "agent_reason TEXT,"
                + "agent_model TEXT,"
                + "agent_status TEXT NOT NULL,"
                + "agent_error_message TEXT,"
                + "agent_review_json TEXT,"
                + "human_status TEXT NOT NULL,"
                + "human_note TEXT,"
                + "promoted_article_id INTEGER,"
                + "promoted_at TEXT,"
                + "duplicate_of_candidate_id INTEGER,"
                + "duplicate_of_article_id INTEGER,"
                + "url_fingerprint TEXT,"
                + "title_fingerprint TEXT,"
                + "body_fingerprint TEXT,"
                + "created_at TEXT NOT NULL,"
                + "updated_at TEXT NOT NULL)");
        jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_intake_candidate_batch ON intake_candidate(batch_id)");
        jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_intake_candidate_source ON intake_candidate(source_id)");
        jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_intake_candidate_human_status ON intake_candidate(human_status)");
        jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_intake_candidate_url ON intake_candidate(url_fingerprint)");
        jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS export_manifest ("
                + "id INTEGER PRIMARY KEY AUTOINCREMENT,"
                + "file_name TEXT NOT NULL,"
                + "manifest_json TEXT NOT NULL,"
                + "created_at TEXT NOT NULL)");
        jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS instrument ("
                + "id INTEGER PRIMARY KEY AUTOINCREMENT,"
                + "code TEXT NOT NULL,"
                + "type TEXT NOT NULL,"
                + "name TEXT,"
                + "market TEXT,"
                + "aliases TEXT,"
                + "sector_code TEXT,"
                + "chain_tags TEXT,"
                + "created_at TEXT NOT NULL,"
                + "updated_at TEXT NOT NULL)");
        jdbcTemplate.execute("DROP INDEX IF EXISTS idx_instrument_code_type");
        jdbcTemplate.execute("CREATE UNIQUE INDEX IF NOT EXISTS idx_instrument_code_type_market "
                + "ON instrument(code,type,market)");
        jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS watchlist_item ("
                + "id INTEGER PRIMARY KEY AUTOINCREMENT,"
                + "instrument_id INTEGER NOT NULL,"
                + "group_name TEXT,"
                + "sort_order INTEGER NOT NULL DEFAULT 0,"
                + "created_at TEXT NOT NULL)");
        jdbcTemplate.execute("CREATE UNIQUE INDEX IF NOT EXISTS idx_watchlist_instrument ON watchlist_item(instrument_id)");
        jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS investment_recognition_candidate ("
                + "id INTEGER PRIMARY KEY AUTOINCREMENT,"
                + "fingerprint TEXT NOT NULL UNIQUE,"
                + "subject_type TEXT NOT NULL,"
                + "subject_code TEXT NOT NULL,"
                + "subject_name TEXT NOT NULL,"
                + "status TEXT NOT NULL,"
                + "thesis TEXT NOT NULL,"
                + "observed_change TEXT NOT NULL,"
                + "mechanism TEXT NOT NULL,"
                + "supporting_data_json TEXT NOT NULL,"
                + "counter_data_json TEXT NOT NULL,"
                + "validation_metrics_json TEXT NOT NULL,"
                + "invalidation_conditions TEXT NOT NULL,"
                + "horizon TEXT NOT NULL,"
                + "confidence TEXT NOT NULL,"
                + "evidence_completeness TEXT NOT NULL,"
                + "trigger_summary TEXT,"
                + "data_as_of TEXT,"
                + "topic_id INTEGER,"
                + "revision INTEGER NOT NULL DEFAULT 0,"
                + "generated_at TEXT NOT NULL,"
                + "updated_at TEXT NOT NULL,"
                + "FOREIGN KEY(topic_id) REFERENCES topic(id) ON DELETE SET NULL)");
        jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_investment_recognition_status "
                + "ON investment_recognition_candidate(status, updated_at DESC)");
        jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_investment_recognition_subject "
                + "ON investment_recognition_candidate(subject_type, subject_code)");
        jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS attribution_report ("
                + "id INTEGER PRIMARY KEY AUTOINCREMENT,"
                + "instrument_code TEXT NOT NULL,"
                + "instrument_name TEXT,"
                + "instrument_type TEXT,"
                + "report_date TEXT NOT NULL,"
                + "change_pct REAL,"
                + "status TEXT NOT NULL,"
                + "summary TEXT,"
                + "drivers_json TEXT,"
                + "narrative_json TEXT,"
                + "disclaimer TEXT,"
                + "error_message TEXT,"
                + "warning_message TEXT,"
                + "uncertainties_json TEXT,"
                + "observation_windows_json TEXT,"
                + "duration_ms INTEGER,"
                + "created_at TEXT NOT NULL,"
                + "updated_at TEXT NOT NULL)");
        ensureColumn("attribution_report", "warning_message", "TEXT");
        ensureColumn("attribution_report", "uncertainties_json", "TEXT");
        ensureColumn("attribution_report", "observation_windows_json", "TEXT");
        ensureColumn("attribution_report", "narrative_json", "TEXT");
        jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_attribution_report_code ON attribution_report(instrument_code)");
        jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_attribution_report_identity "
                + "ON attribution_report(instrument_code, instrument_type, id)");
        jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS attribution_evidence ("
                + "id INTEGER PRIMARY KEY AUTOINCREMENT,"
                + "report_id INTEGER NOT NULL,"
                + "origin TEXT,"
                + "title TEXT,"
                + "url TEXT,"
                + "snippet TEXT,"
                + "source_domain TEXT,"
                + "source_tier TEXT,"
                + "relevance INTEGER,"
                + "event_type TEXT,"
                + "stance TEXT,"
                + "directness TEXT,"
                + "published_at TEXT,"
                + "event_key TEXT,"
                + "historical_context INTEGER NOT NULL DEFAULT 0,"
                + "created_at TEXT NOT NULL)");
        ensureColumn("attribution_evidence", "event_type", "TEXT");
        ensureColumn("attribution_evidence", "stance", "TEXT");
        ensureColumn("attribution_evidence", "directness", "TEXT");
        ensureColumn("attribution_evidence", "published_at", "TEXT");
        ensureColumn("attribution_evidence", "event_key", "TEXT");
        ensureColumn("attribution_evidence", "historical_context", "INTEGER NOT NULL DEFAULT 0");
        jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_attribution_evidence_report ON attribution_evidence(report_id)");
        jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS strategy_holding ("
                + "id INTEGER PRIMARY KEY AUTOINCREMENT,"
                + "instrument_id INTEGER NOT NULL UNIQUE,"
                + "role TEXT NOT NULL CHECK(role IN ('CORE','SATELLITE','DEFENSIVE','OBSERVE','SIMULATED','LIVE_VALIDATION')),"
                + "target_weight REAL NOT NULL CHECK(target_weight >= 0 AND target_weight <= 100),"
                + "current_weight REAL NOT NULL CHECK(current_weight >= 0 AND current_weight <= 100),"
                + "note TEXT,"
                + "sort_order INTEGER NOT NULL DEFAULT 0,"
                + "revision INTEGER NOT NULL DEFAULT 0,"
                + "created_at TEXT NOT NULL,"
                + "updated_at TEXT NOT NULL,"
                + "FOREIGN KEY(instrument_id) REFERENCES instrument(id) ON DELETE RESTRICT)");
        jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_strategy_holding_instrument ON strategy_holding(instrument_id)");
        ensureColumn("strategy_holding", "quantity", "REAL");
        ensureColumn("strategy_holding", "average_cost", "REAL");
        jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS strategy_playbook ("
                + "id INTEGER PRIMARY KEY AUTOINCREMENT,"
                + "code TEXT NOT NULL UNIQUE,"
                + "title TEXT NOT NULL,"
                + "scope TEXT NOT NULL,"
                + "summary TEXT NOT NULL,"
                + "cadence TEXT NOT NULL,"
                + "risk_boundary TEXT NOT NULL,"
                + "author TEXT NOT NULL,"
                + "source_title TEXT NOT NULL,"
                + "source_type TEXT NOT NULL,"
                + "source_ref TEXT,"
                + "source_published_at TEXT,"
                + "validation_status TEXT NOT NULL DEFAULT 'UNVALIDATED',"
                + "status TEXT NOT NULL CHECK(status IN ('RESEARCHING','ACTIVE','PAUSED')),"
                + "note TEXT,"
                + "revision INTEGER NOT NULL DEFAULT 0,"
                + "created_at TEXT NOT NULL,"
                + "updated_at TEXT NOT NULL)");
        ensureColumn("strategy_playbook", "title", "TEXT");
        ensureColumn("strategy_playbook", "scope", "TEXT");
        ensureColumn("strategy_playbook", "summary", "TEXT");
        ensureColumn("strategy_playbook", "cadence", "TEXT");
        ensureColumn("strategy_playbook", "risk_boundary", "TEXT");
        ensureColumn("strategy_playbook", "author", "TEXT");
        ensureColumn("strategy_playbook", "source_title", "TEXT");
        ensureColumn("strategy_playbook", "source_type", "TEXT");
        ensureColumn("strategy_playbook", "source_ref", "TEXT");
        ensureColumn("strategy_playbook", "source_published_at", "TEXT");
        ensureColumn("strategy_playbook", "validation_status", "TEXT NOT NULL DEFAULT 'UNVALIDATED'");
        jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_strategy_playbook_status ON strategy_playbook(status)");
        jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS strategy_playbook_rule ("
                + "id INTEGER PRIMARY KEY AUTOINCREMENT,"
                + "playbook_id INTEGER NOT NULL,"
                + "section_code TEXT NOT NULL,"
                + "section_title TEXT NOT NULL,"
                + "rule_type TEXT NOT NULL,"
                + "rule_text TEXT NOT NULL,"
                + "testability TEXT NOT NULL,"
                + "source_page INTEGER,"
                + "parameter_json TEXT,"
                + "sort_order INTEGER NOT NULL,"
                + "FOREIGN KEY(playbook_id) REFERENCES strategy_playbook(id) ON DELETE CASCADE)");
        jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_strategy_playbook_rule_order "
                + "ON strategy_playbook_rule(playbook_id,sort_order,id)");
        jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS strategy_stock_thesis ("
                + "id INTEGER PRIMARY KEY AUTOINCREMENT,"
                + "instrument_id INTEGER NOT NULL UNIQUE,"
                + "stage TEXT NOT NULL CHECK(stage IN ('RESEARCH_POOL','WATCH_POOL','SIMULATED_PORTFOLIO','LIVE_VALIDATION')),"
                + "thesis TEXT NOT NULL,"
                + "buy_conditions TEXT NOT NULL,"
                + "invalidation_conditions TEXT NOT NULL,"
                + "watch_focus TEXT NOT NULL,"
                + "note TEXT,"
                + "revision INTEGER NOT NULL DEFAULT 0,"
                + "created_at TEXT NOT NULL,"
                + "updated_at TEXT NOT NULL,"
                + "FOREIGN KEY(instrument_id) REFERENCES instrument(id) ON DELETE RESTRICT)");
        jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_strategy_stock_thesis_stage ON strategy_stock_thesis(stage)");
        jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS strategy_review ("
                + "id INTEGER PRIMARY KEY AUTOINCREMENT,"
                + "review_date TEXT NOT NULL,"
                + "facts TEXT NOT NULL,"
                + "reasoning TEXT NOT NULL,"
                + "next_action TEXT NOT NULL,"
                + "created_at TEXT NOT NULL)");
        jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_strategy_review_date ON strategy_review(review_date DESC)");
        jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS attribution_research_run ("
                + "id INTEGER PRIMARY KEY AUTOINCREMENT,"
                + "report_id INTEGER NOT NULL UNIQUE,"
                + "status TEXT NOT NULL,"
                + "plan_json TEXT,"
                + "budget_json TEXT,"
                + "current_step TEXT,"
                + "termination_reason TEXT,"
                + "error_message TEXT,"
                + "created_at TEXT NOT NULL,"
                + "updated_at TEXT NOT NULL)");
        jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_attribution_research_run_status ON attribution_research_run(status)");
        jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS attribution_research_step ("
                + "id INTEGER PRIMARY KEY AUTOINCREMENT,"
                + "run_id INTEGER NOT NULL,"
                + "step_id TEXT NOT NULL,"
                + "track TEXT,"
                + "status TEXT NOT NULL,"
                + "input_summary TEXT,"
                + "output_summary TEXT,"
                + "attempt INTEGER NOT NULL DEFAULT 0,"
                + "max_attempts INTEGER NOT NULL DEFAULT 1,"
                + "error_message TEXT,"
                + "started_at TEXT,"
                + "ended_at TEXT,"
                + "UNIQUE(run_id, step_id))");
        jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_attribution_research_step_run ON attribution_research_step(run_id)");
        initializeMarketDataSchema();
        initializeQuantSchema();
    }

    private void seedNewsCategories() {
        String now = LocalDateTime.now().toString();
        insertNewsCategory("COMPANY", "公司动态", "单一或多家公司的公告、业绩、订单、产品、并购、治理和经营变化", 10, now);
        insertNewsCategory("INDUSTRY", "行业产业", "行业供需、产业链、技术路线、产能、价格和竞争格局变化", 20, now);
        insertNewsCategory("MACRO_POLICY", "政策宏观", "监管政策、财政货币政策和重要宏观经济数据", 30, now);
        insertNewsCategory("GLOBAL", "全球市场", "海外市场、地缘政治、汇率及国际大宗商品事件", 40, now);
        insertNewsCategory("MARKET_MOVE", "市场异动", "指数、板块、个股、成交、资金或波动异常", 50, now);
    }

    private void insertNewsCategory(String code, String name, String guidance, int order, String now) {
        jdbcTemplate.update("INSERT OR IGNORE INTO news_category(code,name,classification_guidance,enabled,"
                        + "display_order,created_at,updated_at) VALUES(?,?,?,1,?,?,?)",
                code, name, guidance, order, now, now);
    }

    private void initializeMarketDataSchema() {
        jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS market_data_snapshot ("
                + "capability TEXT NOT NULL,"
                + "scope_key TEXT NOT NULL,"
                + "provider_code TEXT NOT NULL,"
                + "provider_family TEXT NOT NULL,"
                + "as_of TEXT,"
                + "retrieved_at TEXT NOT NULL,"
                + "payload_json TEXT NOT NULL,"
                + "payload_hash TEXT NOT NULL,"
                + "schema_version INTEGER NOT NULL,"
                + "updated_at TEXT NOT NULL,"
                + "PRIMARY KEY(capability,scope_key))");
        jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS market_data_refresh_run ("
                + "id INTEGER PRIMARY KEY AUTOINCREMENT,"
                + "capability TEXT NOT NULL,"
                + "scope_summary TEXT NOT NULL,"
                + "trigger_type TEXT NOT NULL,"
                + "status TEXT NOT NULL,"
                + "started_at TEXT NOT NULL,"
                + "finished_at TEXT,"
                + "requested_count INTEGER NOT NULL DEFAULT 0,"
                + "fresh_count INTEGER NOT NULL DEFAULT 0,"
                + "stale_count INTEGER NOT NULL DEFAULT 0,"
                + "failed_count INTEGER NOT NULL DEFAULT 0,"
                + "selected_sources TEXT,"
                + "warning_message TEXT)");
        jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_market_data_refresh_started "
                + "ON market_data_refresh_run(started_at)");
        jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS market_data_provider_attempt ("
                + "id INTEGER PRIMARY KEY AUTOINCREMENT,"
                + "refresh_run_id INTEGER NOT NULL,"
                + "capability TEXT NOT NULL,"
                + "provider_code TEXT NOT NULL,"
                + "provider_family TEXT NOT NULL,"
                + "status TEXT NOT NULL,"
                + "error_type TEXT,"
                + "retry_count INTEGER NOT NULL DEFAULT 0,"
                + "latency_ms INTEGER NOT NULL DEFAULT 0,"
                + "requested_count INTEGER NOT NULL DEFAULT 0,"
                + "accepted_count INTEGER NOT NULL DEFAULT 0,"
                + "started_at TEXT NOT NULL,"
                + "finished_at TEXT NOT NULL,"
                + "FOREIGN KEY(refresh_run_id) REFERENCES market_data_refresh_run(id) ON DELETE CASCADE)");
        jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_market_data_attempt_provider_started "
                + "ON market_data_provider_attempt(provider_code,started_at)");
        jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_market_data_attempt_run "
                + "ON market_data_provider_attempt(refresh_run_id)");
    }

    private void initializeQuantSchema() {
        jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS quant_dataset ("
                + "id INTEGER PRIMARY KEY AUTOINCREMENT,"
                + "name TEXT NOT NULL,market TEXT NOT NULL,universe_type TEXT NOT NULL,"
                + "source_type TEXT NOT NULL,data_kind TEXT NOT NULL CHECK(data_kind IN ('REAL','LEARNING_SAMPLE')),"
                + "dataset_level TEXT NOT NULL DEFAULT 'LEARNING',as_of_time TEXT,"
                + "fingerprint_version TEXT NOT NULL DEFAULT 'quant-dataset-v1',"
                + "partition_manifest TEXT NOT NULL DEFAULT '[]',"
                + "start_date TEXT,end_date TEXT,status TEXT NOT NULL,fingerprint TEXT,quality_summary TEXT,"
                + "revision INTEGER NOT NULL DEFAULT 0,created_at TEXT NOT NULL,updated_at TEXT NOT NULL)");
        jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS quant_daily_bar ("
                + "id INTEGER PRIMARY KEY AUTOINCREMENT,dataset_id INTEGER NOT NULL,trade_date TEXT NOT NULL,"
                + "instrument_code TEXT NOT NULL,open REAL NOT NULL,high REAL NOT NULL,low REAL NOT NULL,close REAL NOT NULL,"
                + "adjusted_close REAL NOT NULL,volume REAL NOT NULL,amount REAL NOT NULL,trade_status TEXT NOT NULL,"
                + "is_st INTEGER NOT NULL DEFAULT 0,limit_up INTEGER NOT NULL DEFAULT 0,limit_down INTEGER NOT NULL DEFAULT 0,"
                + "FOREIGN KEY(dataset_id) REFERENCES quant_dataset(id) ON DELETE CASCADE,"
                + "UNIQUE(dataset_id,trade_date,instrument_code))");
        jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_quant_bar_code_date "
                + "ON quant_daily_bar(dataset_id,instrument_code,trade_date)");
        jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_quant_bar_date "
                + "ON quant_daily_bar(dataset_id,trade_date)");
        jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS quant_fundamental_snapshot ("
                + "id INTEGER PRIMARY KEY AUTOINCREMENT,dataset_id INTEGER NOT NULL,instrument_code TEXT NOT NULL,"
                + "report_period TEXT NOT NULL,disclosed_at TEXT NOT NULL,pe REAL,pb REAL,market_cap REAL,roe REAL,"
                + "revenue_growth REAL,profit_growth REAL,debt_ratio REAL,"
                + "FOREIGN KEY(dataset_id) REFERENCES quant_dataset(id) ON DELETE CASCADE,"
                + "UNIQUE(dataset_id,instrument_code,report_period,disclosed_at))");
        jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_quant_fundamental_visible "
                + "ON quant_fundamental_snapshot(dataset_id,instrument_code,disclosed_at)");
        jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS quant_universe_member ("
                + "dataset_id INTEGER NOT NULL,trade_date TEXT NOT NULL,instrument_code TEXT NOT NULL,"
                + "member INTEGER NOT NULL DEFAULT 1,source_kind TEXT NOT NULL,"
                + "PRIMARY KEY(dataset_id,trade_date,instrument_code),"
                + "FOREIGN KEY(dataset_id) REFERENCES quant_dataset(id) ON DELETE CASCADE)");
        jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_quant_universe_date "
                + "ON quant_universe_member(dataset_id,trade_date,member)");
        jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS quant_data_sync_run ("
                + "id INTEGER PRIMARY KEY AUTOINCREMENT,dataset_id INTEGER NOT NULL,"
                + "trigger_type TEXT NOT NULL,status TEXT NOT NULL,requested_instruments INTEGER NOT NULL DEFAULT 0,"
                + "succeeded_instruments INTEGER NOT NULL DEFAULT 0,failed_instruments INTEGER NOT NULL DEFAULT 0,"
                + "inserted_rows INTEGER NOT NULL DEFAULT 0,degraded_instruments INTEGER NOT NULL DEFAULT 0,"
                + "source_summary TEXT,warning_summary TEXT,started_at TEXT NOT NULL,finished_at TEXT,"
                + "FOREIGN KEY(dataset_id) REFERENCES quant_dataset(id) ON DELETE CASCADE)");
        jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_quant_data_sync_dataset "
                + "ON quant_data_sync_run(dataset_id,id DESC)");
        jdbcTemplate.execute("CREATE UNIQUE INDEX IF NOT EXISTS uq_quant_data_sync_active "
                + "ON quant_data_sync_run(dataset_id) WHERE status='RUNNING'");
        jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS quant_dataset_issue ("
                + "id INTEGER PRIMARY KEY AUTOINCREMENT,dataset_id INTEGER NOT NULL,severity TEXT NOT NULL,"
                + "issue_code TEXT NOT NULL,trade_date TEXT,instrument_code TEXT,message TEXT NOT NULL,issue_count INTEGER NOT NULL,"
                + "created_at TEXT NOT NULL,FOREIGN KEY(dataset_id) REFERENCES quant_dataset(id) ON DELETE CASCADE)");
        jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_quant_issue_dataset "
                + "ON quant_dataset_issue(dataset_id,severity)");
        jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS quant_strategy_draft ("
                + "id INTEGER PRIMARY KEY AUTOINCREMENT,dataset_id INTEGER NOT NULL,prompt TEXT NOT NULL,"
                + "raw_response TEXT,normalized_spec TEXT,status TEXT NOT NULL,model TEXT,validation_issues TEXT,"
                + "created_at TEXT NOT NULL,FOREIGN KEY(dataset_id) REFERENCES quant_dataset(id) ON DELETE RESTRICT)");
        jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_quant_draft_dataset ON quant_strategy_draft(dataset_id,id DESC)");
        ensureColumn("quant_strategy_draft", "validated_dataset_fingerprint", "TEXT");
        jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS quant_strategy_version ("
                + "id INTEGER PRIMARY KEY AUTOINCREMENT,name TEXT NOT NULL,dataset_id INTEGER NOT NULL,version INTEGER NOT NULL,"
                + "spec_json TEXT NOT NULL,strategy_fingerprint TEXT NOT NULL UNIQUE,dataset_fingerprint TEXT NOT NULL,"
                + "engine_version TEXT NOT NULL,source TEXT NOT NULL,created_at TEXT NOT NULL,"
                + "FOREIGN KEY(dataset_id) REFERENCES quant_dataset(id) ON DELETE RESTRICT,UNIQUE(name,version))");
        jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_quant_strategy_dataset ON quant_strategy_version(dataset_id,id DESC)");
        jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS quant_catalog_source ("
                + "code TEXT PRIMARY KEY,repository_url TEXT NOT NULL,branch TEXT NOT NULL,commit_sha TEXT NOT NULL,"
                + "status TEXT NOT NULL,last_synced_at TEXT NOT NULL,error_message TEXT)");
        jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS quant_strategy_candidate ("
                + "id INTEGER PRIMARY KEY AUTOINCREMENT,source_code TEXT NOT NULL,external_key TEXT NOT NULL,"
                + "source_commit_sha TEXT NOT NULL,title TEXT NOT NULL,asset_class TEXT NOT NULL,reported_sharpe REAL,"
                + "reported_volatility REAL,rebalance_cadence TEXT,implementation_url TEXT,paper_url TEXT,"
                + "compatibility_status TEXT NOT NULL CHECK(compatibility_status IN ('ADAPTABLE','NEEDS_FACTOR','UNSUPPORTED')),"
                + "adaptation_note TEXT NOT NULL,mapped_factors TEXT NOT NULL DEFAULT '',missing_factors TEXT NOT NULL DEFAULT '',"
                + "archived INTEGER NOT NULL DEFAULT 0,created_at TEXT NOT NULL,updated_at TEXT NOT NULL,"
                + "FOREIGN KEY(source_code) REFERENCES quant_catalog_source(code) ON DELETE RESTRICT,"
                + "UNIQUE(source_code,external_key))");
        jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_quant_candidate_active_status "
                + "ON quant_strategy_candidate(archived,compatibility_status,id DESC)");
        jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS quant_strategy_candidate_origin ("
                + "candidate_id INTEGER NOT NULL,draft_id INTEGER NOT NULL UNIQUE,version_id INTEGER,created_at TEXT NOT NULL,"
                + "FOREIGN KEY(candidate_id) REFERENCES quant_strategy_candidate(id) ON DELETE RESTRICT)");
        jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_quant_candidate_origin_candidate "
                + "ON quant_strategy_candidate_origin(candidate_id,draft_id DESC)");
        ensureColumn("quant_strategy_candidate_origin", "source_commit_sha", "TEXT");
        jdbcTemplate.update("UPDATE quant_strategy_candidate_origin SET source_commit_sha=("
                + "SELECT c.source_commit_sha FROM quant_strategy_candidate c "
                + "WHERE c.id=quant_strategy_candidate_origin.candidate_id) "
                + "WHERE source_commit_sha IS NULL");
        jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS quant_experiment ("
                + "id INTEGER PRIMARY KEY AUTOINCREMENT,strategy_version_id INTEGER NOT NULL,request_fingerprint TEXT NOT NULL,"
                + "dataset_fingerprint TEXT NOT NULL,engine_version TEXT NOT NULL,status TEXT NOT NULL,error_message TEXT,"
                + "created_at TEXT NOT NULL,started_at TEXT,completed_at TEXT,"
                + "FOREIGN KEY(strategy_version_id) REFERENCES quant_strategy_version(id) ON DELETE RESTRICT)");
        jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_quant_experiment_status ON quant_experiment(status,id DESC)");
        jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_quant_experiment_request ON quant_experiment(request_fingerprint,status)");
        Integer activeDuplicates = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM (SELECT request_fingerprint FROM quant_experiment "
                + "WHERE status IN ('QUEUED','RUNNING') GROUP BY request_fingerprint HAVING COUNT(*) > 1)", Integer.class);
        if (activeDuplicates != null && activeDuplicates == 0) {
            jdbcTemplate.execute("CREATE UNIQUE INDEX IF NOT EXISTS uq_quant_experiment_active_request ON quant_experiment(request_fingerprint) "
                    + "WHERE status IN ('QUEUED','RUNNING')");
        }
        jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS quant_experiment_metric ("
                + "experiment_id INTEGER NOT NULL,metric_code TEXT NOT NULL,metric_value REAL NOT NULL,"
                + "PRIMARY KEY(experiment_id,metric_code),FOREIGN KEY(experiment_id) REFERENCES quant_experiment(id) ON DELETE CASCADE)");
        jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS quant_equity_point ("
                + "experiment_id INTEGER NOT NULL,trade_date TEXT NOT NULL,portfolio_nav REAL NOT NULL,benchmark_nav REAL NOT NULL,"
                + "cash REAL NOT NULL,total_asset REAL NOT NULL,drawdown REAL NOT NULL,PRIMARY KEY(experiment_id,trade_date),"
                + "FOREIGN KEY(experiment_id) REFERENCES quant_experiment(id) ON DELETE CASCADE)");
        jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS quant_trade ("
                + "id INTEGER PRIMARY KEY AUTOINCREMENT,experiment_id INTEGER NOT NULL,signal_date TEXT NOT NULL,trade_date TEXT NOT NULL,"
                + "instrument_code TEXT NOT NULL,side TEXT NOT NULL,quantity INTEGER NOT NULL,price REAL NOT NULL,notional REAL NOT NULL,"
                + "fee REAL NOT NULL,reason TEXT,FOREIGN KEY(experiment_id) REFERENCES quant_experiment(id) ON DELETE CASCADE)");
        jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_quant_trade_experiment ON quant_trade(experiment_id,trade_date)");
        jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS quant_experiment_warning ("
                + "experiment_id INTEGER NOT NULL,warning_index INTEGER NOT NULL,message TEXT NOT NULL,"
                + "PRIMARY KEY(experiment_id,warning_index),FOREIGN KEY(experiment_id) REFERENCES quant_experiment(id) ON DELETE CASCADE)");
        jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS quant_experiment_year ("
                + "experiment_id INTEGER NOT NULL,year INTEGER NOT NULL,portfolio_return REAL NOT NULL,benchmark_return REAL NOT NULL,"
                + "excess_return REAL NOT NULL,max_drawdown REAL NOT NULL,PRIMARY KEY(experiment_id,year),"
                + "FOREIGN KEY(experiment_id) REFERENCES quant_experiment(id) ON DELETE CASCADE)");
        jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS quant_position_snapshot ("
                + "experiment_id INTEGER NOT NULL,trade_date TEXT NOT NULL,instrument_code TEXT NOT NULL,quantity INTEGER NOT NULL,"
                + "price REAL NOT NULL,market_value REAL NOT NULL,weight REAL NOT NULL,PRIMARY KEY(experiment_id,trade_date,instrument_code),"
                + "FOREIGN KEY(experiment_id) REFERENCES quant_experiment(id) ON DELETE CASCADE)");
        jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS quant_experiment_interpretation ("
                + "experiment_id INTEGER PRIMARY KEY,content_json TEXT NOT NULL,model TEXT NOT NULL,created_at TEXT NOT NULL,"
                + "FOREIGN KEY(experiment_id) REFERENCES quant_experiment(id) ON DELETE CASCADE)");
        jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS single_stock_forecast_run ("
                + "id INTEGER PRIMARY KEY AUTOINCREMENT,instrument_code TEXT NOT NULL,as_of_date TEXT NOT NULL,"
                + "horizon_days INTEGER NOT NULL DEFAULT 20,status TEXT NOT NULL,up_probability REAL,"
                + "data_fingerprint TEXT NOT NULL,model_version TEXT NOT NULL,"
                + "report_schema_version TEXT NOT NULL,same_data_as_previous INTEGER NOT NULL DEFAULT 0,"
                + "maturity_status TEXT NOT NULL DEFAULT 'PENDING',report_json TEXT NOT NULL,"
                + "holding_snapshot_json TEXT,entry_date TEXT,exit_date TEXT,entry_open REAL,exit_open REAL,"
                + "actual_net_return REAL,actual_direction TEXT,prediction_correct INTEGER,settled_at TEXT,"
                + "outcome_source_code TEXT,outcome_note TEXT,created_at TEXT NOT NULL)");
        ensureColumn("single_stock_forecast_run", "horizon_days", "INTEGER NOT NULL DEFAULT 20");
        ensureColumn("single_stock_forecast_run", "maturity_status", "TEXT NOT NULL DEFAULT 'PENDING'");
        ensureColumn("single_stock_forecast_run", "entry_date", "TEXT");
        ensureColumn("single_stock_forecast_run", "exit_date", "TEXT");
        ensureColumn("single_stock_forecast_run", "entry_open", "REAL");
        ensureColumn("single_stock_forecast_run", "exit_open", "REAL");
        ensureColumn("single_stock_forecast_run", "actual_net_return", "REAL");
        ensureColumn("single_stock_forecast_run", "actual_direction", "TEXT");
        ensureColumn("single_stock_forecast_run", "prediction_correct", "INTEGER");
        ensureColumn("single_stock_forecast_run", "settled_at", "TEXT");
        ensureColumn("single_stock_forecast_run", "outcome_source_code", "TEXT");
        ensureColumn("single_stock_forecast_run", "outcome_note", "TEXT");
        jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_single_stock_forecast_code_created "
                + "ON single_stock_forecast_run(instrument_code,created_at DESC,id DESC)");
        jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_single_stock_forecast_pending "
                + "ON single_stock_forecast_run(instrument_code,maturity_status,as_of_date,id)");
        jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_single_stock_forecast_health "
                + "ON single_stock_forecast_run(instrument_code,horizon_days,model_version,"
                + "maturity_status,data_fingerprint,id)");
        jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS single_stock_forecast_candidate_run ("
                + "id INTEGER PRIMARY KEY AUTOINCREMENT,forecast_run_id INTEGER NOT NULL,"
                + "instrument_code TEXT NOT NULL,as_of_date TEXT NOT NULL,horizon_days INTEGER NOT NULL,"
                + "data_fingerprint TEXT NOT NULL,model_code TEXT NOT NULL,model_name TEXT NOT NULL,"
                + "model_version TEXT NOT NULL,role TEXT NOT NULL,raw_probability REAL NOT NULL,"
                + "calibrated_probability REAL NOT NULL,shadow_decision TEXT NOT NULL,"
                + "qualification_status TEXT NOT NULL,locked_sample_count INTEGER NOT NULL,"
                + "locked_accuracy REAL NOT NULL,locked_brier_score REAL NOT NULL,"
                + "locked_log_loss REAL NOT NULL,locked_brier_skill_score REAL NOT NULL,"
                + "maturity_status TEXT NOT NULL DEFAULT 'PENDING',actual_net_return REAL,"
                + "actual_direction TEXT,prediction_correct INTEGER,settled_at TEXT,created_at TEXT NOT NULL,"
                + "UNIQUE(forecast_run_id,model_code),"
                + "FOREIGN KEY(forecast_run_id) REFERENCES single_stock_forecast_run(id) ON DELETE CASCADE)");
        jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_forecast_candidate_evidence "
                + "ON single_stock_forecast_candidate_run(instrument_code,horizon_days,maturity_status,"
                + "data_fingerprint,forecast_run_id,model_code)");
        jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS stock_discovery_run ("
                + "id INTEGER PRIMARY KEY AUTOINCREMENT,run_key TEXT NOT NULL UNIQUE,business_date TEXT NOT NULL,"
                + "trigger_type TEXT NOT NULL,status TEXT NOT NULL,budget REAL NOT NULL,policy_version TEXT NOT NULL,"
                + "as_of_date TEXT,source_family TEXT,quality_status TEXT,data_fingerprint TEXT,"
                + "sector_count INTEGER NOT NULL DEFAULT 0,constituent_count INTEGER NOT NULL DEFAULT 0,"
                + "admitted_count INTEGER NOT NULL DEFAULT 0,deep_review_count INTEGER NOT NULL DEFAULT 0,"
                + "final_count INTEGER NOT NULL DEFAULT 0,report_json TEXT,error_message TEXT,"
                + "attempt_token TEXT,created_at TEXT NOT NULL,started_at TEXT,completed_at TEXT)");
        ensureColumn("stock_discovery_run", "attempt_token", "TEXT");
        jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_stock_discovery_run_latest "
                + "ON stock_discovery_run(status,business_date DESC,id DESC)");
        jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS stock_discovery_sector ("
                + "id INTEGER PRIMARY KEY AUTOINCREMENT,run_id INTEGER NOT NULL,sector_code TEXT NOT NULL,"
                + "sector_name TEXT NOT NULL,category TEXT NOT NULL,source_code TEXT NOT NULL,source_family TEXT NOT NULL,"
                + "period TEXT NOT NULL,source_rank INTEGER NOT NULL,change_pct REAL,main_net_inflow REAL,"
                + "main_net_inflow_ratio REAL,leader_stock_name TEXT,retrieved_at TEXT NOT NULL,"
                + "UNIQUE(run_id,sector_code,category),"
                + "FOREIGN KEY(run_id) REFERENCES stock_discovery_run(id) ON DELETE CASCADE)");
        jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS stock_discovery_candidate ("
                + "id INTEGER PRIMARY KEY AUTOINCREMENT,run_id INTEGER NOT NULL,instrument_code TEXT NOT NULL,"
                + "name TEXT NOT NULL,price REAL NOT NULL,lot_cost REAL NOT NULL,admitted INTEGER NOT NULL,"
                + "rejection_reasons_json TEXT NOT NULL,sector_codes_json TEXT NOT NULL,sector_names_json TEXT NOT NULL,"
                + "lightweight_score REAL,lightweight_rank INTEGER,deep_score REAL,final_rank INTEGER,"
                + "conclusion TEXT,calibrated_probability REAL,health_status TEXT,detail_json TEXT NOT NULL,"
                + "UNIQUE(run_id,instrument_code),"
                + "FOREIGN KEY(run_id) REFERENCES stock_discovery_run(id) ON DELETE CASCADE)");
        jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_stock_discovery_candidate_rank "
                + "ON stock_discovery_candidate(run_id,final_rank,lightweight_rank)");
        jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS stock_learning_card ("
                + "id INTEGER PRIMARY KEY AUTOINCREMENT,instrument_id INTEGER NOT NULL UNIQUE,framework_code TEXT NOT NULL,"
                + "latest_run_id INTEGER,status TEXT NOT NULL,revision INTEGER NOT NULL DEFAULT 0,created_at TEXT NOT NULL,updated_at TEXT NOT NULL,"
                + "FOREIGN KEY(instrument_id) REFERENCES instrument(id) ON DELETE RESTRICT)");
        jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS stock_learning_card_run ("
                + "id INTEGER PRIMARY KEY AUTOINCREMENT,card_id INTEGER NOT NULL,research_run_id INTEGER,framework_code TEXT NOT NULL,status TEXT NOT NULL,"
                + "stage TEXT,failed_stage TEXT,error_code TEXT,user_message TEXT,retryable INTEGER NOT NULL DEFAULT 0,"
                + "conclusion_status TEXT,summary TEXT,evidence_completeness TEXT,warning_message TEXT,source_fingerprint TEXT,generation_mode TEXT NOT NULL,"
                + "created_at TEXT NOT NULL,completed_at TEXT,FOREIGN KEY(card_id) REFERENCES stock_learning_card(id) ON DELETE CASCADE)");
        ensureColumn("stock_learning_card_run", "stage", "TEXT");
        ensureColumn("stock_learning_card_run", "failed_stage", "TEXT");
        ensureColumn("stock_learning_card_run", "error_code", "TEXT");
        ensureColumn("stock_learning_card_run", "user_message", "TEXT");
        ensureColumn("stock_learning_card_run", "retryable", "INTEGER NOT NULL DEFAULT 0");
        jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_stock_learning_card_run_card ON stock_learning_card_run(card_id,id DESC)");
        jdbcTemplate.update("UPDATE stock_learning_card_run SET status='FAILED',stage='COMPLETED',"
                + "failed_stage=COALESCE(failed_stage,'QUEUED'),error_code=COALESCE(error_code,'STALE_DUPLICATE_RUN'),"
                + "user_message=COALESCE(user_message,'检测到重复的历史运行，可以重新生成'),retryable=1,"
                + "completed_at=COALESCE(completed_at,created_at) WHERE status='RUNNING' AND id NOT IN "
                + "(SELECT MAX(id) FROM stock_learning_card_run WHERE status='RUNNING' GROUP BY card_id)");
        jdbcTemplate.execute("CREATE UNIQUE INDEX IF NOT EXISTS idx_stock_learning_card_run_active "
                + "ON stock_learning_card_run(card_id) WHERE status='RUNNING'");
        jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS stock_learning_card_claim ("
                + "id INTEGER PRIMARY KEY AUTOINCREMENT,run_id INTEGER NOT NULL,dimension_code TEXT NOT NULL,judgment TEXT NOT NULL,rationale TEXT NOT NULL,"
                + "status TEXT,failure_message TEXT,counterargument TEXT NOT NULL,unknowns TEXT NOT NULL,confidence TEXT NOT NULL,sort_order INTEGER NOT NULL,"
                + "UNIQUE(run_id,dimension_code),FOREIGN KEY(run_id) REFERENCES stock_learning_card_run(id) ON DELETE CASCADE)");
        ensureColumn("stock_learning_card_claim", "status", "TEXT");
        ensureColumn("stock_learning_card_claim", "failure_message", "TEXT");
        ensureColumn("stock_learning_card_claim", "headline", "TEXT");
        ensureColumn("stock_learning_card_claim", "rating_label", "TEXT");
        ensureColumn("stock_learning_card_claim", "rating_value", "TEXT");
        jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS stock_learning_card_section ("
                + "id INTEGER PRIMARY KEY AUTOINCREMENT,claim_id INTEGER NOT NULL,section_key TEXT NOT NULL,title TEXT NOT NULL,"
                + "content TEXT NOT NULL,evidence_refs_json TEXT NOT NULL,verification_status TEXT NOT NULL,sort_order INTEGER NOT NULL,"
                + "UNIQUE(claim_id,section_key),FOREIGN KEY(claim_id) REFERENCES stock_learning_card_claim(id) ON DELETE CASCADE)");
        jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_stock_learning_card_section_claim "
                + "ON stock_learning_card_section(claim_id,sort_order)");
        jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS stock_learning_card_evidence ("
                + "id INTEGER PRIMARY KEY AUTOINCREMENT,run_id INTEGER NOT NULL,dimension_code TEXT NOT NULL,"
                + "evidence_code TEXT NOT NULL,title TEXT NOT NULL,url TEXT,source TEXT,published_at TEXT,content TEXT NOT NULL,"
                + "content_origin TEXT,sort_order INTEGER NOT NULL,UNIQUE(run_id,dimension_code,evidence_code),"
                + "FOREIGN KEY(run_id) REFERENCES stock_learning_card_run(id) ON DELETE CASCADE)");
        jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_stock_learning_card_evidence_run "
                + "ON stock_learning_card_evidence(run_id,dimension_code,sort_order)");
        jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS stock_learning_card_watch_item ("
                + "id INTEGER PRIMARY KEY AUTOINCREMENT,run_id INTEGER NOT NULL,metric TEXT NOT NULL,baseline TEXT NOT NULL,frequency TEXT NOT NULL,"
                + "upgrade_condition TEXT NOT NULL,downgrade_condition TEXT NOT NULL,next_review_at TEXT,sort_order INTEGER NOT NULL,"
                + "FOREIGN KEY(run_id) REFERENCES stock_learning_card_run(id) ON DELETE CASCADE)");
        jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS stock_supply_chain_snapshot ("
                + "id INTEGER PRIMARY KEY AUTOINCREMENT,instrument_id INTEGER NOT NULL UNIQUE,payload_json TEXT NOT NULL,"
                + "schema_version TEXT NOT NULL,model TEXT,evidence_as_of TEXT,generated_at TEXT NOT NULL,updated_at TEXT NOT NULL,"
                + "FOREIGN KEY(instrument_id) REFERENCES instrument(id) ON DELETE CASCADE)");
        jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS stock_supply_chain_refresh_run ("
                + "id INTEGER PRIMARY KEY AUTOINCREMENT,instrument_id INTEGER NOT NULL,status TEXT NOT NULL,stage TEXT NOT NULL,"
                + "message TEXT,error_code TEXT,error_message TEXT,retryable INTEGER NOT NULL DEFAULT 0,"
                + "created_at TEXT NOT NULL,completed_at TEXT,"
                + "FOREIGN KEY(instrument_id) REFERENCES instrument(id) ON DELETE CASCADE)");
        jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_stock_supply_chain_run_instrument "
                + "ON stock_supply_chain_refresh_run(instrument_id,id DESC)");
        jdbcTemplate.execute("CREATE UNIQUE INDEX IF NOT EXISTS idx_stock_supply_chain_active_run "
                + "ON stock_supply_chain_refresh_run(instrument_id) WHERE status='RUNNING'");
        jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS industry_chain ("
                + "id INTEGER PRIMARY KEY AUTOINCREMENT,name TEXT NOT NULL,normalized_name TEXT NOT NULL UNIQUE,"
                + "summary TEXT,current_revision_id INTEGER,created_at TEXT NOT NULL,updated_at TEXT NOT NULL)");
        jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS industry_chain_revision ("
                + "id INTEGER PRIMARY KEY AUTOINCREMENT,chain_id INTEGER NOT NULL,status TEXT NOT NULL,stage TEXT NOT NULL,"
                + "message TEXT,error_code TEXT,retryable INTEGER NOT NULL DEFAULT 0,graph_summary TEXT,limitations TEXT,"
                + "research_content_json TEXT,schema_version TEXT,model TEXT,generated_at TEXT,created_at TEXT NOT NULL,completed_at TEXT,"
                + "FOREIGN KEY(chain_id) REFERENCES industry_chain(id) ON DELETE CASCADE)");
        ensureColumn("industry_chain_revision", "research_content_json", "TEXT");
        ensureColumn("industry_chain_revision", "lease_token", "TEXT");
        ensureColumn("industry_chain_revision", "lease_updated_at", "TEXT");
        jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_industry_chain_revision_chain "
                + "ON industry_chain_revision(chain_id,id DESC)");
        jdbcTemplate.execute("CREATE UNIQUE INDEX IF NOT EXISTS idx_industry_chain_active_revision "
                + "ON industry_chain_revision(chain_id) WHERE status='RUNNING'");
        jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS industry_chain_evidence ("
                + "id INTEGER PRIMARY KEY AUTOINCREMENT,revision_id INTEGER NOT NULL,evidence_code TEXT NOT NULL,title TEXT NOT NULL,"
                + "url TEXT,source TEXT,source_tier TEXT,published_at TEXT,excerpt TEXT NOT NULL,sort_order INTEGER NOT NULL,"
                + "UNIQUE(revision_id,evidence_code),FOREIGN KEY(revision_id) REFERENCES industry_chain_revision(id) ON DELETE CASCADE)");
        jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS industry_chain_node ("
                + "id INTEGER PRIMARY KEY AUTOINCREMENT,revision_id INTEGER NOT NULL,node_key TEXT NOT NULL,type TEXT NOT NULL,"
                + "name TEXT NOT NULL,description TEXT NOT NULL,stage_order INTEGER,stock_code TEXT,confidence TEXT NOT NULL,"
                + "evidence_refs_json TEXT NOT NULL,sort_order INTEGER NOT NULL,UNIQUE(revision_id,node_key),"
                + "FOREIGN KEY(revision_id) REFERENCES industry_chain_revision(id) ON DELETE CASCADE)");
        jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS industry_chain_edge ("
                + "id INTEGER PRIMARY KEY AUTOINCREMENT,revision_id INTEGER NOT NULL,edge_key TEXT NOT NULL,source_key TEXT NOT NULL,"
                + "target_key TEXT NOT NULL,type TEXT NOT NULL,nature TEXT NOT NULL,description TEXT NOT NULL,confidence TEXT NOT NULL,"
                + "strength TEXT,direction_note TEXT,evidence_refs_json TEXT NOT NULL,sort_order INTEGER NOT NULL,UNIQUE(revision_id,edge_key),"
                + "FOREIGN KEY(revision_id) REFERENCES industry_chain_revision(id) ON DELETE CASCADE)");
        ensureColumn("industry_chain_edge", "strength", "TEXT");
        ensureColumn("industry_chain_edge", "direction_note", "TEXT");
        jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS industry_chain_event_impact ("
                + "id INTEGER PRIMARY KEY AUTOINCREMENT,chain_id INTEGER NOT NULL,radar_event_id INTEGER NOT NULL,"
                + "direct_node_key TEXT NOT NULL,direction TEXT NOT NULL,mechanism TEXT NOT NULL,horizon TEXT NOT NULL,"
                + "confidence TEXT NOT NULL,impact_summary TEXT NOT NULL,analysis_version TEXT NOT NULL,"
                + "created_at TEXT NOT NULL,updated_at TEXT NOT NULL,UNIQUE(chain_id,radar_event_id),"
                + "FOREIGN KEY(chain_id) REFERENCES industry_chain(id) ON DELETE CASCADE,"
                + "FOREIGN KEY(radar_event_id) REFERENCES radar_event(id) ON DELETE CASCADE)");
        jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_industry_chain_event_impact_chain "
                + "ON industry_chain_event_impact(chain_id,updated_at DESC)");
        jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS industry_chain_event_path ("
                + "impact_id INTEGER NOT NULL,node_key TEXT NOT NULL,position INTEGER NOT NULL,"
                + "PRIMARY KEY(impact_id,position),FOREIGN KEY(impact_id) "
                + "REFERENCES industry_chain_event_impact(id) ON DELETE CASCADE)");
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

    private void seedStrategyPlaybooks() throws Exception {
        ClassPathResource resource = new ClassPathResource("db/seed/strategy-playbooks-v1.json");
        List<Map<String, Object>> seeds = new ObjectMapper().readValue(resource.getInputStream(),
                new TypeReference<List<Map<String, Object>>>() { });
        String now = LocalDateTime.now().toString();
        for (Map<String, Object> seed : seeds) {
            jdbcTemplate.update("INSERT INTO strategy_playbook(code,title,scope,summary,cadence,risk_boundary,"
                            + "author,source_title,source_type,source_ref,source_published_at,validation_status,"
                            + "status,note,revision,created_at,updated_at) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?) "
                            + "ON CONFLICT(code) DO UPDATE SET title=excluded.title,scope=excluded.scope,"
                            + "summary=excluded.summary,cadence=excluded.cadence,risk_boundary=excluded.risk_boundary,"
                            + "author=excluded.author,source_title=excluded.source_title,source_type=excluded.source_type,"
                            + "source_ref=excluded.source_ref,source_published_at=excluded.source_published_at",
                    seed.get("code"), seed.get("title"), seed.get("scope"), seed.get("summary"),
                    seed.get("cadence"), seed.get("riskBoundary"), seed.get("author"), seed.get("sourceTitle"),
                    seed.get("sourceType"), seed.get("sourceRef"), seed.get("sourcePublishedAt"),
                    seed.get("validationStatus"), seed.get("status"), null, 0, now, now);
        }
    }

    /**
     * Existing local databases may contain legacy duplicates. Never discard user history during startup:
     * install the stronger invariant only once the stored data is already compatible.
     */
    private void createUniqueIndexIfNoDuplicates(String indexName, String table, String columns) {
        Integer duplicateGroups = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM (SELECT " + columns
                        + " FROM " + table + " GROUP BY " + columns + " HAVING COUNT(*) > 1)", Integer.class);
        if (duplicateGroups != null && duplicateGroups == 0) {
            jdbcTemplate.execute("CREATE UNIQUE INDEX IF NOT EXISTS " + indexName + " ON " + table + "(" + columns + ")");
        }
    }
}
