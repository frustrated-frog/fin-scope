package com.finscope.dao.news;

import org.springframework.beans.factory.InitializingBean;
import org.springframework.context.annotation.DependsOn;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import javax.annotation.Resource;

@Component
@DependsOn("databaseInitializer")
public class NewsWindowSchemaMigrator implements InitializingBean {
    @Resource
    private JdbcTemplate jdbc;
    @Resource
    private PlatformTransactionManager transactionManager;

    @Override
    public void afterPropertiesSet() {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            jdbc.execute("CREATE TABLE IF NOT EXISTS news_report (arrival_sequence INTEGER PRIMARY KEY AUTOINCREMENT,"
                    + "id TEXT NOT NULL UNIQUE,provider_code TEXT NOT NULL,source_name TEXT NOT NULL,source_tier TEXT,kind TEXT NOT NULL,"
                    + "title TEXT NOT NULL,content TEXT NOT NULL,url TEXT,published_at TEXT,first_seen_at TEXT NOT NULL,last_seen_at TEXT NOT NULL,"
                    + "content_version INTEGER NOT NULL DEFAULT 1,category_code TEXT,category_name TEXT,classification_reason TEXT,"
                    + "rule_version TEXT,manually_reviewed INTEGER NOT NULL DEFAULT 0,manual_reason TEXT)");
            jdbc.execute("CREATE INDEX IF NOT EXISTS idx_news_report_window ON news_report(published_at,arrival_sequence)");
            jdbc.execute("CREATE INDEX IF NOT EXISTS idx_news_report_title ON news_report(title)");
            jdbc.execute("CREATE INDEX IF NOT EXISTS idx_news_report_source ON news_report(source_name,published_at)");
            jdbc.execute("CREATE INDEX IF NOT EXISTS idx_news_report_effective_time "
                    + "ON news_report(COALESCE(published_at,first_seen_at),arrival_sequence)");
            jdbc.execute("CREATE TABLE IF NOT EXISTS news_report_version (report_id TEXT NOT NULL,version INTEGER NOT NULL,"
                    + "title TEXT NOT NULL,content TEXT NOT NULL,detected_at TEXT NOT NULL,PRIMARY KEY(report_id,version))");
            if (jdbc.queryForList("PRAGMA table_info(news_report_version)").stream()
                    .noneMatch(column -> "ai_due_at".equals(column.get("name")))) {
                jdbc.execute("ALTER TABLE news_report_version ADD COLUMN ai_due_at TEXT");
                // 仅在升级时承接原生产窗口中的当前版本；重启不重置已处理记录。
                var now = java.time.LocalDateTime.now(java.time.ZoneId.of("Asia/Shanghai"));
                jdbc.update("""
                        UPDATE news_report_version AS v SET ai_due_at=max(v.detected_at,
                            (SELECT coalesce(n.published_at,n.first_seen_at) FROM news_report n WHERE n.id=v.report_id))
                        WHERE (v.report_id,v.version) IN (
                            SELECT id,content_version FROM news_report WHERE coalesce(published_at,first_seen_at)>=?)
                        """, now.minusHours(36).toString());
            }
            jdbc.execute("CREATE INDEX IF NOT EXISTS idx_news_version_due ON news_report_version(ai_due_at,report_id) WHERE ai_due_at IS NOT NULL");
            jdbc.execute("CREATE TABLE IF NOT EXISTS news_report_read (report_id TEXT PRIMARY KEY,read_version INTEGER NOT NULL)");
            jdbc.execute("CREATE TABLE IF NOT EXISTS news_ai_report (report_id TEXT PRIMARY KEY,content_key TEXT NOT NULL,"
                    + "event_key TEXT NOT NULL,relation TEXT NOT NULL,content_version INTEGER NOT NULL)");
            jdbc.execute("CREATE INDEX IF NOT EXISTS idx_news_ai_report_event ON news_ai_report(event_key,report_id)");
            jdbc.execute("CREATE TABLE IF NOT EXISTS news_report_interpretation (report_id TEXT NOT NULL,"
                    + "content_version INTEGER NOT NULL,status TEXT NOT NULL,claim_token TEXT NOT NULL,"
                    + "queued_at_ms INTEGER NOT NULL,result_json TEXT,failure_message TEXT,"
                    + "PRIMARY KEY(report_id,content_version),"
                    + "FOREIGN KEY(report_id) REFERENCES news_report(id) ON DELETE CASCADE)");
            jdbc.execute("""
                    CREATE TABLE IF NOT EXISTS news_fetch_batch(
                        batch_id TEXT NOT NULL, provider_code TEXT NOT NULL, started_at TEXT NOT NULL, completed_at TEXT,
                        source_status TEXT NOT NULL, fetched INTEGER NOT NULL, cached INTEGER NOT NULL,
                        added INTEGER NOT NULL DEFAULT 0, revised INTEGER NOT NULL DEFAULT 0,
                        unchanged INTEGER NOT NULL DEFAULT 0, PRIMARY KEY(batch_id,provider_code))
                    """);
            jdbc.execute("CREATE INDEX IF NOT EXISTS idx_news_fetch_batch_time ON news_fetch_batch(started_at)");
            jdbc.execute("CREATE TABLE IF NOT EXISTS news_saved_filter (id TEXT PRIMARY KEY,name TEXT NOT NULL,query_json TEXT NOT NULL)");
        });
    }
}
