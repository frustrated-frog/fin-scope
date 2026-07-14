package com.finscope.dao.marketintel;

import org.springframework.beans.factory.InitializingBean;
import org.springframework.context.annotation.DependsOn;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;

@Component
@DependsOn("databaseInitializer")
public class MarketIntelSchemaMigrator implements InitializingBean {
    private static final int VERSION = 100;
    private final JdbcTemplate jdbc;
    private final TransactionTemplate transaction;

    public MarketIntelSchemaMigrator(JdbcTemplate jdbc, PlatformTransactionManager transactionManager) {
        this.jdbc = jdbc;
        this.transaction = new TransactionTemplate(transactionManager);
    }

    @Override public void afterPropertiesSet() { migrate(); }

    public void migrate() {
        jdbc.execute("CREATE TABLE IF NOT EXISTS schema_migration (version INTEGER PRIMARY KEY," +
                "description TEXT NOT NULL,applied_at TEXT NOT NULL)");
        transaction.executeWithoutResult(status -> {
            Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM schema_migration WHERE version=?",
                    Integer.class, VERSION);
            if (count != null && count > 0) return;
            createTables();
            jdbc.update("INSERT INTO schema_migration(version,description,applied_at) VALUES(?,?,?)",
                    VERSION, "market intel capital behavior phase 1a", LocalDateTime.now().toString());
        });
    }

    private void createTables() {
        jdbc.execute("CREATE TABLE market_capital_flow_snapshot (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT,instrument_id INTEGER NOT NULL," +
                "provider_code TEXT NOT NULL,granularity TEXT NOT NULL,data_date TEXT NOT NULL," +
                "observed_at TEXT NOT NULL,price TEXT,trade_volume TEXT,interval_trade_amount TEXT," +
                "cumulative_trade_amount TEXT,turnover_rate TEXT,volume_ratio TEXT,main_inflow TEXT," +
                "main_outflow TEXT,main_net_inflow TEXT,super_large_net_inflow TEXT,large_net_inflow TEXT," +
                "medium_net_inflow TEXT,small_net_inflow TEXT,calculation_version TEXT NOT NULL," +
                "retrieved_at TEXT NOT NULL,payload_hash TEXT NOT NULL,quality_status TEXT NOT NULL," +
                "FOREIGN KEY(instrument_id) REFERENCES instrument(id) ON DELETE RESTRICT)");
        jdbc.execute("CREATE UNIQUE INDEX idx_capital_flow_identity ON market_capital_flow_snapshot(" +
                "instrument_id,provider_code,granularity,observed_at,payload_hash)");
        jdbc.execute("CREATE INDEX idx_capital_flow_range ON market_capital_flow_snapshot(" +
                "instrument_id,observed_at,granularity)");

        jdbc.execute("CREATE TABLE market_capital_behavior_snapshot (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT,instrument_id INTEGER NOT NULL,as_of TEXT NOT NULL," +
                "fingerprint TEXT NOT NULL,quality_status TEXT NOT NULL,facts_json TEXT NOT NULL," +
                "signals_json TEXT NOT NULL,created_at TEXT NOT NULL," +
                "UNIQUE(instrument_id,as_of,fingerprint)," +
                "FOREIGN KEY(instrument_id) REFERENCES instrument(id) ON DELETE RESTRICT)");
        jdbc.execute("CREATE INDEX idx_capital_snapshot_latest ON market_capital_behavior_snapshot(" +
                "instrument_id,as_of DESC,id DESC)");

        jdbc.execute("CREATE TABLE market_capital_interpretation (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT,instrument_id INTEGER NOT NULL,snapshot_id INTEGER NOT NULL," +
                "interpretation_type TEXT NOT NULL,status TEXT NOT NULL,plain_summary TEXT,facts_json TEXT NOT NULL," +
                "hypotheses_json TEXT NOT NULL,data_gaps_json TEXT NOT NULL,observation_points_json TEXT NOT NULL," +
                "disclaimer TEXT,fallback_reason TEXT,rule_version TEXT,model_name TEXT,prompt_version TEXT," +
                "input_hash TEXT NOT NULL,output_hash TEXT,created_at TEXT NOT NULL,updated_at TEXT NOT NULL," +
                "FOREIGN KEY(instrument_id) REFERENCES instrument(id) ON DELETE RESTRICT," +
                "FOREIGN KEY(snapshot_id) REFERENCES market_capital_behavior_snapshot(id) ON DELETE RESTRICT)");
        jdbc.execute("CREATE UNIQUE INDEX idx_capital_interpretation_action ON market_capital_interpretation(" +
                "snapshot_id,interpretation_type,input_hash)");

        jdbc.execute("CREATE TABLE market_intel_refresh_run (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT,instrument_id INTEGER NOT NULL,trigger_type TEXT NOT NULL," +
                "status TEXT NOT NULL,success_count INTEGER NOT NULL DEFAULT 0,failure_count INTEGER NOT NULL DEFAULT 0," +
                "started_at TEXT NOT NULL,finished_at TEXT," +
                "FOREIGN KEY(instrument_id) REFERENCES instrument(id) ON DELETE RESTRICT)");
        jdbc.execute("CREATE TABLE market_intel_refresh_step (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT,run_id INTEGER NOT NULL,dimension TEXT NOT NULL," +
                "provider_code TEXT NOT NULL,attempt INTEGER NOT NULL,status TEXT NOT NULL," +
                "fallback_used INTEGER NOT NULL DEFAULT 0,error_type TEXT,error_message TEXT," +
                "output_count INTEGER NOT NULL DEFAULT 0,started_at TEXT NOT NULL,finished_at TEXT," +
                "UNIQUE(run_id,dimension,provider_code,attempt)," +
                "FOREIGN KEY(run_id) REFERENCES market_intel_refresh_run(id) ON DELETE CASCADE)");
        jdbc.execute("CREATE INDEX idx_market_refresh_run ON market_intel_refresh_step(run_id,id)");
    }
}
