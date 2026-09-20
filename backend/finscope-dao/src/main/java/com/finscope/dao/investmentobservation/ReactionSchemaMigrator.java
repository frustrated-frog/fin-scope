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
    }
}
