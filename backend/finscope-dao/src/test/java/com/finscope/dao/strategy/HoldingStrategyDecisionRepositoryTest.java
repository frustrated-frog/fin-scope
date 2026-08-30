package com.finscope.dao.strategy;

import com.finscope.dao.config.DatabaseInitializer;
import com.finscope.domain.strategy.holding.HoldingStrategyDecision;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.sqlite.SQLiteDataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HoldingStrategyDecisionRepositoryTest {
    private HoldingStrategyDecisionRepository repository;

    @BeforeEach
    void setUp() throws Exception {
        Path dataRoot = Files.createTempDirectory("finscope-holding-decision-test");
        SQLiteDataSource dataSource = new SQLiteDataSource();
        dataSource.setUrl("jdbc:sqlite:" + dataRoot.resolve("finance.db"));
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        DatabaseInitializer initializer = new DatabaseInitializer();
        ReflectionTestUtils.setField(initializer, "jdbcTemplate", jdbcTemplate);
        ReflectionTestUtils.setField(initializer, "dataRoot", dataRoot.toString());
        initializer.afterPropertiesSet();
        repository = new HoldingStrategyDecisionRepository();
        ReflectionTestUtils.setField(repository, "jdbcTemplate", jdbcTemplate);
    }

    @Test
    void freezesEvidenceAndFindsSameDayDecisionIdempotently() {
        HoldingStrategyDecision value = new HoldingStrategyDecision();
        value.setInstrumentCode("600570.SH");
        value.setInstrumentName("恒生电子");
        value.setDecisionDate(LocalDate.of(2026, 8, 31));
        value.setHorizonDays(5);
        value.setModelVersion("panel-logit-v10");
        value.setDataFingerprint("sha256:abc");
        value.setAction("HOLD");
        value.setEvidence(Arrays.asList("概率门禁通过"));
        value.setBlockers(Arrays.asList("不足一手"));
        value.setExplanation("保持持仓");
        value.setBenchmark("同一只股票保持当时持仓不动");
        value.setPolicyVersion("holding-policy-v1");
        value.setValidationStatus("PENDING");
        value.setMaturityDate(LocalDate.of(2026, 9, 7));
        value.setInputJson("{}");
        value.setOutputJson("{}");

        HoldingStrategyDecision saved = repository.save(value);

        assertTrue(repository.findUnique("600570.SH", value.getDecisionDate(),
                "holding-policy-v1").isPresent());
        assertEquals("概率门禁通过", saved.getEvidence().get(0));
        assertEquals("不足一手", saved.getBlockers().get(0));
        assertEquals(1, repository.findAll(50).size());
        assertEquals(1, repository.findPendingDue(LocalDate.of(2026, 9, 7), 50).size());
        assertTrue(repository.settle(saved.getId(), 0.04d, 0.03d, 0.01d));
        assertTrue(!repository.settle(saved.getId(), 0.04d, 0.03d, 0.01d));
        HoldingStrategyDecision settled = repository.findById(saved.getId())
                .orElseThrow(AssertionError::new);
        assertEquals("MATURED", settled.getValidationStatus());
        assertEquals(0.01d, settled.getIncrementalReturn(), 0.000001d);
    }
}
