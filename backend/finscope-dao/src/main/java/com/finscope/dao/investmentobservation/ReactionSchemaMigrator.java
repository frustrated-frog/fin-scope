package com.finscope.dao.investmentobservation;

import org.springframework.beans.factory.InitializingBean;
import org.springframework.context.annotation.DependsOn;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import javax.annotation.Resource;
import java.time.LocalDateTime;

@Component
@DependsOn("databaseInitializer")
public class ReactionSchemaMigrator implements InitializingBean {
    private static final int VERSION = 410;
    @Resource
    private JdbcTemplate jdbcTemplate;
    @Resource
    private PlatformTransactionManager transactionManager;

    @Override
    public void afterPropertiesSet() {
        jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS schema_migration ("
                + "version INTEGER PRIMARY KEY,description TEXT NOT NULL,applied_at TEXT NOT NULL)");
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            Integer count = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM schema_migration WHERE version=?", Integer.class, VERSION);
            if (count != null && count > 0) {
                return;
            }
            jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS investment_reaction_sample ("
                    + "id INTEGER PRIMARY KEY AUTOINCREMENT,major_event_id INTEGER NOT NULL,"
                    + "instrument_code TEXT NOT NULL DEFAULT '',state TEXT NOT NULL,"
                    + "snapshot_json TEXT NOT NULL,calculation_json TEXT,revision INTEGER NOT NULL DEFAULT 0,"
                    + "last_attempt_at TEXT,refresh_error TEXT,registered_at TEXT NOT NULL,completed INTEGER NOT NULL DEFAULT 0,"
                    + "UNIQUE(major_event_id,instrument_code))");
            jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_reaction_state_id "
                    + "ON investment_reaction_sample(state,id)");
            jdbcTemplate.update("INSERT INTO schema_migration(version,description,applied_at) VALUES(?,?,?)",
                    VERSION, "event reaction samples with immutable source snapshots", LocalDateTime.now().toString());
        });
        migrateAutomaticSources();
        migrateEventSources();
    }

    private void migrateAutomaticSources() {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            Integer count = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM schema_migration WHERE version=411", Integer.class);
            if (count != null && count > 0) {
                return;
            }
            jdbcTemplate.execute("CREATE TABLE investment_reaction_sample_next ("
                    + "id INTEGER PRIMARY KEY AUTOINCREMENT,major_event_id INTEGER,source_identity TEXT NOT NULL,"
                    + "instrument_code TEXT NOT NULL DEFAULT '',state TEXT NOT NULL,"
                    + "snapshot_json TEXT NOT NULL,calculation_json TEXT,revision INTEGER NOT NULL DEFAULT 0,"
                    + "last_attempt_at TEXT,refresh_error TEXT,registered_at TEXT NOT NULL,completed INTEGER NOT NULL DEFAULT 0,"
                    + "enrichment_attempt_at TEXT,UNIQUE(source_identity,instrument_code))");
            jdbcTemplate.execute("INSERT INTO investment_reaction_sample_next SELECT id,major_event_id,"
                    + "'MAJOR_EVENT:' || major_event_id,instrument_code,state,snapshot_json,calculation_json,revision,"
                    + "last_attempt_at,refresh_error,registered_at,completed,NULL FROM investment_reaction_sample");
            jdbcTemplate.execute("DROP TABLE investment_reaction_sample");
            jdbcTemplate.execute("ALTER TABLE investment_reaction_sample_next RENAME TO investment_reaction_sample");
            jdbcTemplate.execute("CREATE INDEX idx_reaction_state_id ON investment_reaction_sample(state,id)");
            jdbcTemplate.update("INSERT INTO schema_migration(version,description,applied_at) VALUES(411,?,?)",
                    "automatic news identity independent of saved major events", LocalDateTime.now().toString());
        });
    }
    private void migrateEventSources() {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            if (jdbcTemplate.queryForObject("SELECT COUNT(*) FROM schema_migration WHERE version=412", Integer.class) > 0) {
                return;
            }
            jdbcTemplate.execute("CREATE TABLE investment_reaction_source (origin_type TEXT NOT NULL,origin_key TEXT NOT NULL,"
                    + "event_key TEXT NOT NULL,title TEXT,url TEXT,published_at TEXT,captured_at TEXT,PRIMARY KEY(origin_type,origin_key))");
            jdbcTemplate.execute("CREATE INDEX idx_reaction_source_event ON investment_reaction_source(event_key)");
            jdbcTemplate.execute("INSERT OR IGNORE INTO investment_reaction_source SELECT "
                    + "json_extract(snapshot_json,'$.sourceOriginType'),json_extract(snapshot_json,'$.sourceOriginKey'),"
                    + "source_identity,json_extract(snapshot_json,'$.title'),json_extract(snapshot_json,'$.sourceUrl'),"
                    + "json_extract(snapshot_json,'$.publishedAt'),registered_at FROM investment_reaction_sample "
                    + "WHERE json_extract(snapshot_json,'$.sourceOriginType') IS NOT NULL "
                    + "AND json_extract(snapshot_json,'$.sourceOriginKey') IS NOT NULL ORDER BY id");
            jdbcTemplate.execute("ALTER TABLE investment_reaction_sample ADD COLUMN followed INTEGER NOT NULL DEFAULT 0");
            jdbcTemplate.execute("ALTER TABLE investment_reaction_sample ADD COLUMN next_attempt_at TEXT");
            jdbcTemplate.update("INSERT INTO schema_migration VALUES(412,?,?)", "stable sources and bounded observation retries", LocalDateTime.now().toString());
        });
    }

}
