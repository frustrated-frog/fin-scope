package com.finscope.dao.valuation;

import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.DependsOn;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;

@Component
@DependsOn("databaseInitializer")
public class ValuationSchemaMigrator implements InitializingBean {
    private static final int VERSION = 320;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private PlatformTransactionManager transactionManager;

    public ValuationSchemaMigrator() {
    }

    ValuationSchemaMigrator(JdbcTemplate jdbc, PlatformTransactionManager transactionManager) {
        this.jdbc = jdbc;
        this.transactionManager = transactionManager;
    }

    @Override
    public void afterPropertiesSet() {
        migrate();
    }

    public void migrate() {
        jdbc.execute("CREATE TABLE IF NOT EXISTS schema_migration ("
                + "version INTEGER PRIMARY KEY,description TEXT NOT NULL,applied_at TEXT NOT NULL)");
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            Integer count = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM schema_migration WHERE version=?", Integer.class, VERSION);
            if (count != null && count > 0) {
                return;
            }
            createTables();
            jdbc.update("INSERT INTO schema_migration(version,description,applied_at) VALUES(?,?,?)",
                    VERSION, "stock valuation snapshots and corporate actions",
                    LocalDateTime.now().toString());
        });
    }

    private void createTables() {
        jdbc.execute("CREATE TABLE IF NOT EXISTS stock_valuation_snapshot ("
                + "id INTEGER PRIMARY KEY AUTOINCREMENT,instrument_id INTEGER NOT NULL,"
                + "observed_date TEXT NOT NULL,observed_at TEXT NOT NULL,name TEXT,"
                + "pe_ttm TEXT,pe_mrq TEXT,pb_mrq TEXT,ps_ttm TEXT,pcf_ttm TEXT,"
                + "source_code TEXT NOT NULL,quality_status TEXT NOT NULL,"
                + "created_at TEXT NOT NULL,updated_at TEXT NOT NULL,"
                + "UNIQUE(instrument_id,observed_date,source_code),"
                + "FOREIGN KEY(instrument_id) REFERENCES instrument(id) ON DELETE RESTRICT)");
        jdbc.execute("CREATE INDEX IF NOT EXISTS idx_valuation_instrument_date ON "
                + "stock_valuation_snapshot(instrument_id,observed_date DESC,id DESC)");
        jdbc.execute("CREATE TABLE IF NOT EXISTS stock_corporate_action ("
                + "id INTEGER PRIMARY KEY AUTOINCREMENT,instrument_id INTEGER NOT NULL,"
                + "ex_date TEXT NOT NULL,event_types TEXT NOT NULL,dividend_per_share TEXT,"
                + "per_share_bonus TEXT,allotment_ratio TEXT,allotment_price TEXT,"
                + "currency TEXT NOT NULL,source_code TEXT NOT NULL,retrieved_at TEXT NOT NULL,"
                + "UNIQUE(instrument_id,ex_date,source_code),"
                + "FOREIGN KEY(instrument_id) REFERENCES instrument(id) ON DELETE RESTRICT)");
        jdbc.execute("CREATE INDEX IF NOT EXISTS idx_corporate_action_instrument_date ON "
                + "stock_corporate_action(instrument_id,ex_date DESC,id DESC)");
    }
}
