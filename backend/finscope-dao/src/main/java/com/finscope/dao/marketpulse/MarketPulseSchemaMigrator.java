package com.finscope.dao.marketpulse;

import org.springframework.beans.factory.InitializingBean;
import org.springframework.context.annotation.DependsOn;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;

@Component
@DependsOn("databaseInitializer")
public class MarketPulseSchemaMigrator implements InitializingBean {
    @Resource
    private JdbcTemplate jdbcTemplate;

    @Override
    public void afterPropertiesSet() {
        migrate();
    }

    public void migrate() {
        jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS market_regime_snapshot ("
                + "id INTEGER PRIMARY KEY AUTOINCREMENT,business_date TEXT NOT NULL UNIQUE,"
                + "confidence_score INTEGER NOT NULL,quality_status TEXT NOT NULL,source_fingerprint TEXT NOT NULL,"
                + "snapshot_json TEXT NOT NULL,calculated_at TEXT NOT NULL)");
        jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS market_breadth_snapshot ("
                + "business_date TEXT NOT NULL PRIMARY KEY,quality_status TEXT NOT NULL,"
                + "source_code TEXT NOT NULL,snapshot_json TEXT NOT NULL,retrieved_at TEXT)");
        jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS sector_rotation_snapshot ("
                + "id INTEGER PRIMARY KEY AUTOINCREMENT,business_date TEXT NOT NULL UNIQUE,"
                + "quality_status TEXT NOT NULL,source_fingerprint TEXT NOT NULL,calculated_at TEXT NOT NULL)");
        jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS sector_rotation_item ("
                + "id INTEGER PRIMARY KEY AUTOINCREMENT,snapshot_id INTEGER NOT NULL,sector_code TEXT NOT NULL,"
                + "sector_name TEXT NOT NULL,rotation_score INTEGER NOT NULL,stage TEXT NOT NULL,item_json TEXT NOT NULL,"
                + "UNIQUE(snapshot_id,sector_code),FOREIGN KEY(snapshot_id) REFERENCES sector_rotation_snapshot(id) ON DELETE CASCADE)");
        jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS market_event_confirmation ("
                + "id INTEGER PRIMARY KEY AUTOINCREMENT,business_date TEXT NOT NULL,radar_event_id INTEGER NOT NULL,"
                + "sector_code TEXT NOT NULL,confirmation_state TEXT NOT NULL,confirmation_json TEXT NOT NULL,"
                + "UNIQUE(business_date,radar_event_id,sector_code))");
        jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS market_opportunity_run ("
                + "id INTEGER PRIMARY KEY AUTOINCREMENT,business_date TEXT NOT NULL UNIQUE,status TEXT NOT NULL,"
                + "quality_status TEXT NOT NULL,workspace_json TEXT NOT NULL,generated_at TEXT NOT NULL)");
        jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS daily_market_review_snapshot ("
                + "business_date TEXT NOT NULL PRIMARY KEY,quality_status TEXT NOT NULL,"
                + "source_fingerprint TEXT NOT NULL,review_json TEXT NOT NULL,generated_at TEXT NOT NULL)");
        jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_market_pulse_date ON market_opportunity_run(business_date DESC)");
    }
}
