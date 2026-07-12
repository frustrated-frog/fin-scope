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

    @BeforeEach
    void setUp() throws Exception {
        Path dataRoot = Files.createTempDirectory("finscope-strategy-schema-test");
        SQLiteDataSource dataSource = new SQLiteDataSource();
        dataSource.setUrl("jdbc:sqlite:" + dataRoot.resolve("finance.db"));
        jdbcTemplate = new JdbcTemplate(dataSource);

        DatabaseInitializer initializer = new DatabaseInitializer();
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
                "SELECT COUNT(*) FROM sqlite_master WHERE type='table' AND name='strategy_stock_thesis'", Integer.class));
        assertEquals(1, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM sqlite_master WHERE type='table' AND name='strategy_review'", Integer.class));

        jdbcTemplate.update("INSERT INTO instrument(code,type,name,created_at,updated_at) VALUES(?,?,?,?,?)",
                "020608", "FUND", "测试基金", "2026-07-12T00:00:00", "2026-07-12T00:00:00");

        assertThrows(DataAccessException.class, () -> jdbcTemplate.update(
                "INSERT INTO strategy_holding(instrument_id,role,target_weight,current_weight,revision,created_at,updated_at) "
                        + "VALUES(1,'CORE',101,0,0,'2026-07-12T00:00:00','2026-07-12T00:00:00')"));
    }
}
