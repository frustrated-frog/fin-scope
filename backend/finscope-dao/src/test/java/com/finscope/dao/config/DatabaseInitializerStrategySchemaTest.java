package com.finscope.dao.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.sqlite.SQLiteDataSource;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

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
    void createsStrategyTablesAndRejectsInvalidWeight() {
        assertEquals(1, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM sqlite_master WHERE type='table' AND name='strategy_holding'", Integer.class));
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
                "SELECT COUNT(*) FROM sqlite_master WHERE type='table' AND name='strategy_stock_thesis'", Integer.class));
        assertEquals(1, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM sqlite_master WHERE type='table' AND name='strategy_review'", Integer.class));
        assertEquals(1, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM pragma_foreign_key_list('strategy_holding') WHERE \"table\"='instrument'",
                Integer.class));

        jdbcTemplate.update("INSERT INTO instrument(code,type,name,created_at,updated_at) VALUES(?,?,?,?,?)",
                "020608", "FUND", "测试基金", "2026-07-12T00:00:00", "2026-07-12T00:00:00");

        assertThrows(DataAccessException.class, () -> jdbcTemplate.update(
                "INSERT INTO strategy_holding(instrument_id,role,target_weight,current_weight,revision,created_at,updated_at) "
                        + "VALUES(1,'CORE',101,0,0,'2026-07-12T00:00:00','2026-07-12T00:00:00')"));
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
