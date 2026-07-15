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
    private static final int INITIAL_VERSION = 100;
    private static final int CALCULATION_IDENTITY_VERSION = 102;
    private static final int SNAPSHOT_WARNING_VERSION = 103;
    private static final int AGENT_EVIDENCE_VERSION = 104;
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
                    Integer.class, INITIAL_VERSION);
            if (count != null && count > 0) return;
            createTables();
            jdbc.update("INSERT INTO schema_migration(version,description,applied_at) VALUES(?,?,?)",
                    INITIAL_VERSION, "market intel capital behavior phase 1a", LocalDateTime.now().toString());
        });
        transaction.executeWithoutResult(status -> {
            Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM schema_migration WHERE version=?",
                    Integer.class, CALCULATION_IDENTITY_VERSION);
            if (count != null && count > 0) return;
            jdbc.execute("DROP INDEX IF EXISTS idx_capital_flow_identity");
            jdbc.execute("CREATE UNIQUE INDEX idx_capital_flow_identity ON market_capital_flow_snapshot(" +
                    "instrument_id,provider_code,granularity,observed_at,payload_hash,calculation_version)");
            jdbc.update("INSERT INTO schema_migration(version,description,applied_at) VALUES(?,?,?)",
                    CALCULATION_IDENTITY_VERSION, "资金事实按计算版本保留", LocalDateTime.now().toString());
        });
        transaction.executeWithoutResult(status -> {
            Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM schema_migration WHERE version=?",
                    Integer.class, SNAPSHOT_WARNING_VERSION);
            if (count != null && count > 0) return;
            jdbc.execute("ALTER TABLE market_capital_behavior_snapshot ADD COLUMN warnings_json TEXT NOT NULL DEFAULT '[]'");
            jdbc.execute("CREATE INDEX IF NOT EXISTS idx_capital_snapshot_created_latest ON " +
                    "market_capital_behavior_snapshot(instrument_id,created_at DESC,id DESC)");
            jdbc.update("INSERT INTO schema_migration(version,description,applied_at) VALUES(?,?,?)",
                    SNAPSHOT_WARNING_VERSION, "资金快照保存降级原因并按刷新时间读取", LocalDateTime.now().toString());
        });
        transaction.executeWithoutResult(status -> {
            Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM schema_migration WHERE version=?",
                    Integer.class, AGENT_EVIDENCE_VERSION);
            if (count != null && count > 0) return;
            jdbc.execute("ALTER TABLE market_capital_interpretation ADD COLUMN market_state TEXT");
            jdbc.execute("ALTER TABLE market_capital_interpretation ADD COLUMN executive_summary TEXT");
            jdbc.execute("ALTER TABLE market_capital_interpretation ADD COLUMN observations_json TEXT NOT NULL DEFAULT '[]'");
            jdbc.execute("ALTER TABLE market_capital_interpretation ADD COLUMN counter_evidence_json TEXT NOT NULL DEFAULT '[]'");
            jdbc.execute("ALTER TABLE market_capital_interpretation ADD COLUMN watch_condition_refs_json TEXT NOT NULL DEFAULT '[]'");
            jdbc.execute("ALTER TABLE market_capital_interpretation ADD COLUMN confidence TEXT");
            jdbc.execute("ALTER TABLE market_capital_interpretation ADD COLUMN factor_version TEXT");
            jdbc.execute("ALTER TABLE market_capital_interpretation ADD COLUMN signal_version TEXT");
            jdbc.execute("ALTER TABLE market_capital_interpretation ADD COLUMN evidence_refs_json TEXT NOT NULL DEFAULT '[]'");
            jdbc.execute("ALTER TABLE market_capital_interpretation ADD COLUMN rejected_output_count INTEGER NOT NULL DEFAULT 0");
            jdbc.execute("ALTER TABLE market_capital_interpretation ADD COLUMN rejection_reasons_json TEXT NOT NULL DEFAULT '[]'");
            jdbc.update("INSERT INTO schema_migration(version,description,applied_at) VALUES(?,?,?)",
                    AGENT_EVIDENCE_VERSION, "资金行为Agent保存因子证据与门禁结果", LocalDateTime.now().toString());
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
