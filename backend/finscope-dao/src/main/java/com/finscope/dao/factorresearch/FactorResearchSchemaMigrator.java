package com.finscope.dao.factorresearch;

import org.springframework.beans.factory.InitializingBean;
import org.springframework.context.annotation.DependsOn;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Restart-safe schema evolution for the quant factor-research persistence layer.
 */
@Component
@DependsOn("databaseInitializer")
public class FactorResearchSchemaMigrator implements InitializingBean {
    private static final int CAPITAL_FLOW_VERSION = 200;
    private static final int DATASET_PARTITION_VERSION = 201;
    private static final int DATASET_METADATA_VERSION = 202;

    private final JdbcTemplate jdbcTemplate;
    private final TransactionTemplate transactionTemplate;

    public FactorResearchSchemaMigrator(JdbcTemplate jdbcTemplate,
                                        PlatformTransactionManager transactionManager) {
        this.jdbcTemplate = jdbcTemplate;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    @Override
    public void afterPropertiesSet() {
        migrate();
    }

    public void migrate() {
        createMigrationLedger();
        migrateVersion(CAPITAL_FLOW_VERSION, "quant capital-flow frozen facts", this::createCapitalFlowSchema);
        migrateVersion(DATASET_PARTITION_VERSION, "quant dataset partition manifest", this::createPartitionSchema);
        migrateVersion(DATASET_METADATA_VERSION, "quant dataset research metadata", this::upgradeDatasetSchema);
    }

    private void migrateVersion(int version, String description, Runnable migration) {
        transactionTemplate.executeWithoutResult(status -> {
            if (isApplied(version)) {
                return;
            }
            migration.run();
            jdbcTemplate.update(
                    "INSERT INTO schema_migration(version,description,applied_at) VALUES(?,?,?)",
                    version, description, LocalDateTime.now().toString());
        });
    }

    private void createMigrationLedger() {
        jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS schema_migration ("
                + "version INTEGER PRIMARY KEY,description TEXT NOT NULL,applied_at TEXT NOT NULL)");
    }

    private boolean isApplied(int version) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM schema_migration WHERE version=?", Integer.class, version);
        return count != null && count > 0;
    }

    private void createCapitalFlowSchema() {
        jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS quant_capital_flow_daily ("
                + "dataset_id INTEGER NOT NULL,"
                + "trade_date TEXT NOT NULL,"
                + "instrument_code TEXT NOT NULL,"
                + "available_at TEXT NOT NULL,"
                + "source_flow_id INTEGER NOT NULL,"
                + "provider_code TEXT NOT NULL,"
                + "main_net_inflow TEXT,"
                + "main_flow_share TEXT,"
                + "super_large_net_inflow TEXT,"
                + "large_net_inflow TEXT,"
                + "medium_net_inflow TEXT,"
                + "small_net_inflow TEXT,"
                + "turnover_rate TEXT,"
                + "amount TEXT,"
                + "quality_status TEXT NOT NULL,"
                + "source_fingerprint TEXT NOT NULL,"
                + "calculation_version TEXT NOT NULL,"
                + "PRIMARY KEY(dataset_id,trade_date,instrument_code),"
                + "FOREIGN KEY(dataset_id) REFERENCES quant_dataset(id) ON DELETE CASCADE)");
        jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_quant_capital_flow_code_date "
                + "ON quant_capital_flow_daily(dataset_id,instrument_code,trade_date)");
        jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_quant_capital_flow_date "
                + "ON quant_capital_flow_daily(dataset_id,trade_date)");
    }

    private void createPartitionSchema() {
        jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS quant_dataset_partition ("
                + "dataset_id INTEGER NOT NULL,"
                + "partition_type TEXT NOT NULL,"
                + "row_count INTEGER NOT NULL,"
                + "min_date TEXT,"
                + "max_date TEXT,"
                + "partition_fingerprint TEXT NOT NULL,"
                + "quality_status TEXT NOT NULL,"
                + "created_at TEXT NOT NULL,"
                + "PRIMARY KEY(dataset_id,partition_type),"
                + "FOREIGN KEY(dataset_id) REFERENCES quant_dataset(id) ON DELETE CASCADE)");
    }

    private void upgradeDatasetSchema() {
        boolean datasetLevelAdded = addColumnIfMissing(
                "quant_dataset", "dataset_level", "TEXT NOT NULL DEFAULT 'LEARNING'");
        addColumnIfMissing("quant_dataset", "as_of_time", "TEXT");
        addColumnIfMissing("quant_dataset", "fingerprint_version",
                "TEXT NOT NULL DEFAULT 'quant-dataset-v1'");
        addColumnIfMissing("quant_dataset", "partition_manifest", "TEXT NOT NULL DEFAULT '[]'");

        if (datasetLevelAdded) {
            jdbcTemplate.update("UPDATE quant_dataset SET dataset_level=CASE data_kind "
                    + "WHEN 'REAL' THEN 'RESEARCH' WHEN 'LEARNING_SAMPLE' THEN 'LEARNING' "
                    + "ELSE dataset_level END");
        } else {
            jdbcTemplate.update("UPDATE quant_dataset SET dataset_level=CASE data_kind "
                    + "WHEN 'REAL' THEN 'RESEARCH' WHEN 'LEARNING_SAMPLE' THEN 'LEARNING' "
                    + "ELSE 'LEARNING' END WHERE dataset_level IS NULL OR trim(dataset_level)='' ");
        }
        jdbcTemplate.update("UPDATE quant_dataset SET fingerprint_version='quant-dataset-v1' "
                + "WHERE fingerprint_version IS NULL OR trim(fingerprint_version)=''");
        jdbcTemplate.update("UPDATE quant_dataset SET partition_manifest='[]' "
                + "WHERE partition_manifest IS NULL OR trim(partition_manifest)=''");
    }

    private boolean addColumnIfMissing(String table, String column, String definition) {
        if (hasColumn(table, column)) {
            return false;
        }
        jdbcTemplate.execute("ALTER TABLE " + table + " ADD COLUMN " + column + " " + definition);
        return true;
    }

    private boolean hasColumn(String table, String column) {
        List<Map<String, Object>> columns = jdbcTemplate.queryForList("PRAGMA table_info(" + table + ")");
        for (Map<String, Object> metadata : columns) {
            if (column.equals(metadata.get("name"))) {
                return true;
            }
        }
        return false;
    }
}
