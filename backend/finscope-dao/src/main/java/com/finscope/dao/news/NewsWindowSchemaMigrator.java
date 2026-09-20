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
            jdbc.execute("CREATE INDEX IF NOT EXISTS idx_news_report_source ON news_report(source_name,published_at)");
            jdbc.execute("CREATE INDEX IF NOT EXISTS idx_news_report_effective_time "
                    + "ON news_report(COALESCE(published_at,first_seen_at),arrival_sequence)");
            jdbc.execute("CREATE TABLE IF NOT EXISTS news_report_version (report_id TEXT NOT NULL,version INTEGER NOT NULL,"
                    + "title TEXT NOT NULL,content TEXT NOT NULL,detected_at TEXT NOT NULL,PRIMARY KEY(report_id,version))");
            jdbc.execute("CREATE TABLE IF NOT EXISTS news_report_read (report_id TEXT PRIMARY KEY,read_version INTEGER NOT NULL)");
            jdbc.execute("CREATE TABLE IF NOT EXISTS news_saved_filter (id TEXT PRIMARY KEY,name TEXT NOT NULL,query_json TEXT NOT NULL)");
        });
    }
}
