package com.finscope.dao.factorresearch;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.finscope.dao.config.DatabaseInitializer;
import com.finscope.domain.factorresearch.FactorIdentity;
import com.finscope.domain.factorresearch.ResearchDraft;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sqlite.SQLiteDataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class ResearchDraftRepositoryTest {
    @TempDir Path tempDir;
    private ResearchDraftRepository repository;
    private JdbcTemplate jdbc;

    @BeforeEach
    void setUp() throws Exception {
        SQLiteDataSource dataSource = new SQLiteDataSource();
        dataSource.setUrl("jdbc:sqlite:" + tempDir.resolve("research-draft.db"));
        jdbc = new JdbcTemplate(dataSource);
        DatabaseInitializer initializer = new DatabaseInitializer();
        ReflectionTestUtils.setField(initializer, "jdbcTemplate", jdbc);
        ReflectionTestUtils.setField(initializer, "dataRoot", tempDir.toString());
        initializer.afterPropertiesSet();
        new FactorResearchSchemaMigrator(jdbc, new DataSourceTransactionManager(dataSource)).migrate();
        repository = new ResearchDraftRepository(jdbc, new ObjectMapper().findAndRegisterModules());
    }

    @Test
    void persistsAnAuditableCapitalResearchDraftWithoutStartingAnExperiment() {
        ResearchDraft saved = repository.save(draft());
        ResearchDraft restored = repository.findById(saved.getId()).orElseThrow(AssertionError::new);

        assertNotNull(saved.getId());
        assertEquals("CAPITAL_BEHAVIOR", restored.getSourceType());
        assertEquals("600519.SH", restored.getInstrumentCode());
        assertEquals(new FactorIdentity("capital", "MAIN_FLOW_SHARE", "1.0.0"), restored.getFactor());
        assertEquals(Arrays.asList("snapshot:42", "daily-flow:2026-07-15"), restored.getEvidenceRefs());
        assertEquals(Arrays.asList("PRICE_FLOW_DIVERGENCE"), restored.getObjectiveTags());
        assertEquals(Arrays.asList("冻结同日股票池资金数据", "预注册持有期与失败条件"), restored.getRequiredNextSteps());
        assertEquals("DRAFT", restored.getStatus());
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM quant_experiment", Integer.class));
    }

    private ResearchDraft draft() {
        ResearchDraft value = new ResearchDraft();
        value.setSourceType("CAPITAL_BEHAVIOR");
        value.setInstrumentCode("600519.SH");
        value.setInstrumentName("贵州茅台");
        value.setObservedAt(LocalDateTime.of(2026, 7, 15, 15, 0));
        value.setSignalCode("PRICE_FLOW_DIVERGENCE");
        value.setFactor(new FactorIdentity("capital", "MAIN_FLOW_SHARE", "1.0.0"));
        value.setSnapshotId(42L);
        value.setSnapshotFingerprint("snapshot-fingerprint");
        value.setEvidenceRefs(Arrays.asList("snapshot:42", "daily-flow:2026-07-15"));
        value.setObjectiveTags(Arrays.asList("PRICE_FLOW_DIVERGENCE"));
        value.setEvaluationMode("CROSS_SECTIONAL_FACTOR_STUDY");
        value.setStatus("DRAFT");
        value.setRequiredNextSteps(Arrays.asList("冻结同日股票池资金数据", "预注册持有期与失败条件"));
        value.setCreatedAt(LocalDateTime.of(2026, 7, 16, 1, 0));
        return value;
    }
}
