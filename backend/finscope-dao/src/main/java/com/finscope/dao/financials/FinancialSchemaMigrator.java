package com.finscope.dao.financials;

import org.springframework.beans.factory.InitializingBean;
import org.springframework.context.annotation.DependsOn;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;

@Component
@DependsOn("databaseInitializer")
public class FinancialSchemaMigrator implements InitializingBean {
    private static final int VERSION = 300;
    private static final int DOCUMENT_VERSION = 301;
    private static final int INTERPRETATION_VERSION = 302;
    private final JdbcTemplate jdbc;
    private final TransactionTemplate transaction;

    public FinancialSchemaMigrator(JdbcTemplate jdbc, PlatformTransactionManager transactionManager) {
        this.jdbc = jdbc;
        this.transaction = new TransactionTemplate(transactionManager);
    }

    @Override
    public void afterPropertiesSet() {
        migrate();
    }

    public void migrate() {
        jdbc.execute("CREATE TABLE IF NOT EXISTS schema_migration (" +
                "version INTEGER PRIMARY KEY,description TEXT NOT NULL,applied_at TEXT NOT NULL)");
        transaction.executeWithoutResult(status -> {
            Integer count = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM schema_migration WHERE version=?",
                    Integer.class, VERSION);
            if (count != null && count > 0) {
                return;
            }
            createTables();
            jdbc.update(
                    "INSERT INTO schema_migration(version,description,applied_at) VALUES(?,?,?)",
                    VERSION, "company financial statements workspace", LocalDateTime.now().toString());
        });
        transaction.executeWithoutResult(status -> {
            Integer count = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM schema_migration WHERE version=?",
                    Integer.class, DOCUMENT_VERSION);
            if (count != null && count > 0) {
                return;
            }
            createDocumentTables();
            jdbc.update(
                    "INSERT INTO schema_migration(version,description,applied_at) VALUES(?,?,?)",
                    DOCUMENT_VERSION, "financial report pdf documents", LocalDateTime.now().toString());
        });
        transaction.executeWithoutResult(status -> {
            Integer count = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM schema_migration WHERE version=?",
                    Integer.class, INTERPRETATION_VERSION);
            if (count != null && count > 0) {
                return;
            }
            createInterpretationTables();
            jdbc.update(
                    "INSERT INTO schema_migration(version,description,applied_at) VALUES(?,?,?)",
                    INTERPRETATION_VERSION, "financial interpretation snapshots and history",
                    LocalDateTime.now().toString());
        });
    }

    private void createTables() {
        jdbc.execute("CREATE TABLE IF NOT EXISTS financial_report (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT,instrument_id INTEGER NOT NULL," +
                "period_end TEXT NOT NULL,report_type TEXT NOT NULL,scope TEXT NOT NULL," +
                "currency TEXT NOT NULL,published_at TEXT,audited INTEGER," +
                "quality_status TEXT NOT NULL,source_code TEXT NOT NULL,warning_message TEXT," +
                "created_at TEXT NOT NULL,updated_at TEXT NOT NULL," +
                "UNIQUE(instrument_id,period_end,report_type,scope)," +
                "FOREIGN KEY(instrument_id) REFERENCES instrument(id) ON DELETE RESTRICT)");
        jdbc.execute("CREATE INDEX IF NOT EXISTS idx_financial_report_instrument_period ON " +
                "financial_report(instrument_id,period_end DESC,id DESC)");
        jdbc.execute("CREATE TABLE IF NOT EXISTS financial_line_item (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT,report_id INTEGER NOT NULL," +
                "statement_type TEXT NOT NULL,source_label TEXT NOT NULL,concept_code TEXT," +
                "period_role TEXT NOT NULL,normalized_value TEXT,currency TEXT," +
                "unit_multiplier TEXT NOT NULL DEFAULT '1',value_origin TEXT NOT NULL," +
                "source_field TEXT,source_code TEXT NOT NULL,display_order INTEGER NOT NULL DEFAULT 0," +
                "quality_status TEXT NOT NULL," +
                "UNIQUE(report_id,statement_type,source_label,period_role,source_code)," +
                "FOREIGN KEY(report_id) REFERENCES financial_report(id) ON DELETE CASCADE)");
        jdbc.execute("CREATE INDEX IF NOT EXISTS idx_financial_line_report_type ON " +
                "financial_line_item(report_id,statement_type,display_order,id)");
        jdbc.execute("CREATE INDEX IF NOT EXISTS idx_financial_line_concept ON " +
                "financial_line_item(report_id,concept_code,period_role)");
        jdbc.execute("CREATE TABLE IF NOT EXISTS financial_metric (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT,report_id INTEGER NOT NULL," +
                "metric_code TEXT NOT NULL,label TEXT NOT NULL,value TEXT,unit TEXT," +
                "formula_version TEXT NOT NULL,input_refs TEXT,quality_status TEXT NOT NULL," +
                "UNIQUE(report_id,metric_code,formula_version)," +
                "FOREIGN KEY(report_id) REFERENCES financial_report(id) ON DELETE CASCADE)");
        jdbc.execute("CREATE TABLE IF NOT EXISTS financial_finding (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT,report_id INTEGER NOT NULL," +
                "rule_code TEXT NOT NULL,rule_version TEXT NOT NULL,severity TEXT NOT NULL," +
                "direction TEXT NOT NULL,title TEXT NOT NULL,explanation TEXT NOT NULL," +
                "metric_refs TEXT,limitations TEXT," +
                "UNIQUE(report_id,rule_code,rule_version)," +
                "FOREIGN KEY(report_id) REFERENCES financial_report(id) ON DELETE CASCADE)");
        jdbc.execute("CREATE TABLE IF NOT EXISTS financial_refresh_run (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT,instrument_id INTEGER NOT NULL," +
                "status TEXT NOT NULL,period_end TEXT,report_type TEXT," +
                "success_count INTEGER NOT NULL DEFAULT 0,failure_count INTEGER NOT NULL DEFAULT 0," +
                "error_message TEXT,started_at TEXT NOT NULL,finished_at TEXT," +
                "FOREIGN KEY(instrument_id) REFERENCES instrument(id) ON DELETE RESTRICT)");
    }

    private void createDocumentTables() {
        jdbc.execute("CREATE TABLE IF NOT EXISTS financial_document (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT,instrument_id INTEGER NOT NULL," +
                "report_id INTEGER,original_file_name TEXT NOT NULL,relative_path TEXT NOT NULL," +
                "mime_type TEXT NOT NULL,file_size INTEGER NOT NULL,file_hash TEXT NOT NULL UNIQUE," +
                "page_count INTEGER,parse_status TEXT NOT NULL,extracted_text TEXT," +
                "error_message TEXT,created_at TEXT NOT NULL," +
                "FOREIGN KEY(instrument_id) REFERENCES instrument(id) ON DELETE RESTRICT," +
                "FOREIGN KEY(report_id) REFERENCES financial_report(id) ON DELETE SET NULL)");
        jdbc.execute("CREATE INDEX IF NOT EXISTS idx_financial_document_report ON " +
                "financial_document(report_id,created_at DESC,id DESC)");
    }

    private void createInterpretationTables() {
        jdbc.execute("CREATE TABLE IF NOT EXISTS financial_analysis_snapshot (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT,report_id INTEGER NOT NULL," +
                "algorithm_version TEXT NOT NULL,source_hash TEXT NOT NULL,input_hash TEXT NOT NULL," +
                "payload_json TEXT NOT NULL,quality_level TEXT NOT NULL,created_at TEXT NOT NULL," +
                "UNIQUE(report_id,algorithm_version,input_hash)," +
                "FOREIGN KEY(report_id) REFERENCES financial_report(id) ON DELETE CASCADE)");
        jdbc.execute("CREATE INDEX IF NOT EXISTS idx_financial_snapshot_report ON " +
                "financial_analysis_snapshot(report_id,id DESC)");
        jdbc.execute("CREATE TABLE IF NOT EXISTS financial_interpretation (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT,report_id INTEGER NOT NULL,snapshot_id INTEGER NOT NULL," +
                "generation_key TEXT NOT NULL,prompt_version TEXT NOT NULL,model_name TEXT,status TEXT NOT NULL," +
                "generation_mode TEXT,result_json TEXT,validation_errors_json TEXT NOT NULL DEFAULT '[]'," +
                "failure_code TEXT,failure_message TEXT,duration_ms INTEGER,created_at TEXT NOT NULL," +
                "started_at TEXT,completed_at TEXT," +
                "FOREIGN KEY(report_id) REFERENCES financial_report(id) ON DELETE CASCADE," +
                "FOREIGN KEY(snapshot_id) REFERENCES financial_analysis_snapshot(id) ON DELETE RESTRICT)");
        jdbc.execute("CREATE INDEX IF NOT EXISTS idx_financial_interpretation_report ON " +
                "financial_interpretation(report_id,id DESC)");
        jdbc.execute("CREATE INDEX IF NOT EXISTS idx_financial_interpretation_snapshot ON " +
                "financial_interpretation(snapshot_id,status,id DESC)");
        jdbc.execute("CREATE INDEX IF NOT EXISTS idx_financial_interpretation_generation ON " +
                "financial_interpretation(generation_key,status,id DESC)");
    }
}
