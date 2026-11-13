package com.finscope.dao.fundamentals;

import org.springframework.beans.factory.InitializingBean;
import org.springframework.context.annotation.DependsOn;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;

@Component
@DependsOn("databaseInitializer")
public class FundamentalResearchSchemaMigrator implements InitializingBean {
    @Resource
    private JdbcTemplate jdbc;

    @Override
    public void afterPropertiesSet() {
        jdbc.execute("CREATE TABLE IF NOT EXISTS company_business_profile (instrument_id INTEGER PRIMARY KEY,"
                + "profile_json TEXT NOT NULL,updated_at TEXT NOT NULL,FOREIGN KEY(instrument_id) REFERENCES instrument(id))");
        jdbc.execute("CREATE TABLE IF NOT EXISTS company_business_notes (instrument_id INTEGER PRIMARY KEY,"
                + "notes_json TEXT NOT NULL,revision INTEGER NOT NULL,updated_at TEXT NOT NULL,"
                + "FOREIGN KEY(instrument_id) REFERENCES instrument(id))");
        jdbc.execute("CREATE TABLE IF NOT EXISTS fundamental_watch (id INTEGER PRIMARY KEY AUTOINCREMENT,"
                + "instrument_id INTEGER NOT NULL,origin_key TEXT NOT NULL,snapshot_json TEXT NOT NULL,"
                + "status TEXT NOT NULL,revision INTEGER NOT NULL DEFAULT 0,next_review_date TEXT NOT NULL,"
                + "created_at TEXT NOT NULL,UNIQUE(instrument_id,origin_key),FOREIGN KEY(instrument_id) REFERENCES instrument(id))");
        jdbc.execute("CREATE INDEX IF NOT EXISTS idx_fundamental_watch_instrument ON fundamental_watch(instrument_id,status,next_review_date)");
        jdbc.execute("CREATE TABLE IF NOT EXISTS fundamental_watch_review (id INTEGER PRIMARY KEY AUTOINCREMENT,"
                + "watch_id INTEGER NOT NULL,status TEXT NOT NULL,note TEXT NOT NULL,reviewed_at TEXT NOT NULL,"
                + "FOREIGN KEY(watch_id) REFERENCES fundamental_watch(id))");
    }
}
