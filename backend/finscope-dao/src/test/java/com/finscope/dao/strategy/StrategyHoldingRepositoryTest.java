package com.finscope.dao.strategy;

import com.finscope.dao.config.DatabaseInitializer;
import com.finscope.domain.strategy.StrategyHolding;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.sqlite.SQLiteDataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StrategyHoldingRepositoryTest {
    private StrategyHoldingRepository repository;

    @BeforeEach
    void setUp() throws Exception {
        Path dataRoot = Files.createTempDirectory("finscope-strategy-holding-test");
        SQLiteDataSource dataSource = new SQLiteDataSource();
        dataSource.setUrl("jdbc:sqlite:" + dataRoot.resolve("finance.db"));
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        DatabaseInitializer initializer = new DatabaseInitializer();
        ReflectionTestUtils.setField(initializer, "jdbcTemplate", jdbcTemplate);
        ReflectionTestUtils.setField(initializer, "dataRoot", dataRoot.toString());
        initializer.afterPropertiesSet();
        jdbcTemplate.update("INSERT INTO instrument(code,type,name,created_at,updated_at) VALUES(?,?,?,?,?)",
                "020608", "FUND", "测试基金", "2026-07-12T00:00:00", "2026-07-12T00:00:00");

        repository = new StrategyHoldingRepository();
        ReflectionTestUtils.setField(repository, "jdbcTemplate", jdbcTemplate);
    }

    @Test
    void updatesOnlyWhenRevisionMatches() {
        StrategyHolding saved = new StrategyHolding();
        saved.setInstrumentId(1L);
        saved.setRole("CORE");
        saved.setTargetWeight(60);
        saved.setCurrentWeight(0);
        saved.setNote("长期核心");

        StrategyHolding persisted = repository.save(saved);

        assertTrue(repository.update(persisted.getId(), "CORE", 55, 0, "调整", 0));
        assertFalse(repository.update(persisted.getId(), "CORE", 40, 0, "陈旧写入", 0));
        assertEquals(1, repository.findById(persisted.getId()).orElseThrow(AssertionError::new).getRevision());
        assertEquals(55, repository.sumTargetWeightExcluding(null), 0.001);
    }
}
