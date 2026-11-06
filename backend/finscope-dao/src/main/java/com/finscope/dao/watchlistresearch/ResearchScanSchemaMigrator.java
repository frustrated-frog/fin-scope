package com.finscope.dao.watchlistresearch;

import org.springframework.beans.factory.InitializingBean;
import org.springframework.context.annotation.DependsOn;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import javax.annotation.Resource;
import java.time.Instant;

@Component
@DependsOn("databaseInitializer")
public class ResearchScanSchemaMigrator implements InitializingBean {
    private static final int VERSION = 403;
    @Resource
    private JdbcTemplate jdbc;
    @Resource
    private PlatformTransactionManager transactionManager;

    @Override
    public void afterPropertiesSet() {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            jdbc.execute("CREATE TABLE IF NOT EXISTS schema_migration ("
                    + "version INTEGER PRIMARY KEY,description TEXT NOT NULL,applied_at TEXT NOT NULL)");
            jdbc.execute("CREATE TABLE IF NOT EXISTS watchlist_research_scan ("
                    + "id TEXT PRIMARY KEY,scan_date TEXT NOT NULL,membership_key TEXT NOT NULL,automatic_key TEXT UNIQUE,"
                    + "status TEXT NOT NULL,stage TEXT NOT NULL,active_slot INTEGER UNIQUE,processed_stocks INTEGER NOT NULL,"
                    + "total_stocks INTEGER NOT NULL,message TEXT NOT NULL,started_at TEXT NOT NULL,"
                    + "finished_at TEXT,snapshot_json TEXT,"
                    + "CHECK((status='RUNNING' AND active_slot IS 1) OR "
                    + "(status IN ('READY','PARTIAL','FAILED') AND active_slot IS NULL)))");
            jdbc.execute("CREATE INDEX IF NOT EXISTS idx_research_scan_membership ON "
                    + "watchlist_research_scan(membership_key,started_at DESC)");
            jdbc.update("INSERT OR IGNORE INTO schema_migration(version,description,applied_at) VALUES(?,?,?)",
                    VERSION, "watchlist research scan archive", Instant.now().toString());
        });
    }
}
