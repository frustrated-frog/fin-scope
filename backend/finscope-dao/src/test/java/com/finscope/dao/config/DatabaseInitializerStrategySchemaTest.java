package com.finscope.dao.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.sqlite.SQLiteDataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DatabaseInitializerStrategySchemaTest {
    private JdbcTemplate jdbcTemplate;
    private DatabaseInitializer initializer;

    @BeforeEach
    void setUp() throws Exception {
        Path dataRoot = Files.createTempDirectory("finscope-strategy-schema-test");
        SQLiteDataSource dataSource = new SQLiteDataSource();
        dataSource.setUrl("jdbc:sqlite:" + dataRoot.resolve("finance.db"));
        jdbcTemplate = new JdbcTemplate(dataSource);

        initializer = new DatabaseInitializer();
        ReflectionTestUtils.setField(initializer, "jdbcTemplate", jdbcTemplate);
        ReflectionTestUtils.setField(initializer, "dataRoot", dataRoot.toString());
        initializer.afterPropertiesSet();
    }

    @Test
    void createsRetainedStrategyTables() {
        assertEquals(1, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM sqlite_master WHERE type='table' AND name='strategy_playbook'", Integer.class));
        assertEquals(1, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM sqlite_master WHERE type='table' AND name='strategy_playbook_rule'", Integer.class));
        assertEquals(1, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM pragma_table_info('strategy_playbook') WHERE name='validation_status'",
                Integer.class));
        assertEquals(1, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM pragma_foreign_key_list('strategy_playbook_rule') WHERE \"table\"='strategy_playbook'",
                Integer.class));
        assertEquals(1, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM sqlite_master WHERE type='table' AND name='stock_transaction'", Integer.class));
        assertEquals(1, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM sqlite_master WHERE type='table' AND name='holding_strategy_decision'",
                Integer.class));
        assertEquals(1, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM pragma_index_list('stock_transaction') indexes "
                        + "JOIN pragma_index_info(indexes.name) columns ON columns.name='client_request_id' "
                        + "WHERE indexes.\"unique\"=1",
                Integer.class));

    }

    @Test
    void removesLegacyPortfolioDataOnceAndPreservesForecastReports() throws Exception {
        for (String table : new String[]{"strategy_holding", "strategy_stock_thesis", "strategy_review"}) {
            jdbcTemplate.execute("CREATE TABLE " + table + " (id INTEGER PRIMARY KEY)");
            jdbcTemplate.update("INSERT INTO " + table + " VALUES (1)");
        }
        jdbcTemplate.update("INSERT INTO single_stock_forecast_run(instrument_code,as_of_date,status,"
                + "data_fingerprint,model_version,report_schema_version,report_json,holding_snapshot_json,created_at) "
                + "VALUES ('600519.SH','2026-01-01','READY','sample','v1','v1','{}','{}','2026-01-01')");
        initializer.afterPropertiesSet();
        assertEquals(0, jdbcTemplate.queryForObject("SELECT count(*) FROM sqlite_master WHERE name IN "
                + "('strategy_holding','strategy_stock_thesis','strategy_review')", Integer.class));
        assertEquals(1, jdbcTemplate.queryForObject("SELECT count(*) FROM single_stock_forecast_run "
                + "WHERE report_json='{}' AND holding_snapshot_json IS NULL", Integer.class));
        jdbcTemplate.update("UPDATE single_stock_forecast_run SET holding_snapshot_json='{}'");
        initializer.afterPropertiesSet();
        assertEquals(1, jdbcTemplate.queryForObject("SELECT count(*) FROM single_stock_forecast_run "
                + "WHERE holding_snapshot_json='{}'", Integer.class));
    }

    @Test
    void seedsLegacyPlaybooksIdempotentlyWithoutOverwritingUserState() throws Exception {
        assertEquals(5, jdbcTemplate.queryForObject("SELECT COUNT(*) FROM strategy_playbook", Integer.class));
        assertEquals("长期定投", jdbcTemplate.queryForObject(
                "SELECT title FROM strategy_playbook WHERE code='FUND_DCA'", String.class));

        jdbcTemplate.update("UPDATE strategy_playbook SET title='旧版定投',status='ACTIVE',note='每月八日执行' WHERE code='FUND_DCA'");
        initializer.afterPropertiesSet();

        assertEquals(5, jdbcTemplate.queryForObject("SELECT COUNT(*) FROM strategy_playbook", Integer.class));
        assertEquals("长期定投", jdbcTemplate.queryForObject(
                "SELECT title FROM strategy_playbook WHERE code='FUND_DCA'", String.class));
        assertEquals("ACTIVE", jdbcTemplate.queryForObject(
                "SELECT status FROM strategy_playbook WHERE code='FUND_DCA'", String.class));
        assertEquals("每月八日执行", jdbcTemplate.queryForObject(
                "SELECT note FROM strategy_playbook WHERE code='FUND_DCA'", String.class));
    }
}
