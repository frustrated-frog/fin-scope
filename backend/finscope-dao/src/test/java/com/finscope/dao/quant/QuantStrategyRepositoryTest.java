package com.finscope.dao.quant;

import com.finscope.common.enums.quant.QuantStrategyDraftStatus;
import com.finscope.dao.config.DatabaseInitializer;
import com.finscope.domain.quant.strategy.QuantStrategyDraft;
import com.finscope.domain.quant.strategy.QuantStrategyVersion;
import com.finscope.domain.quant.strategy.QuantStrategySpec;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.sqlite.SQLiteDataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.file.Files;

import static org.junit.jupiter.api.Assertions.assertEquals;

class QuantStrategyRepositoryTest {
    private QuantStrategyRepository repository;

    @BeforeEach
    void setUp() throws Exception {
        SQLiteDataSource dataSource = new SQLiteDataSource();
        dataSource.setUrl("jdbc:sqlite:" + Files.createTempDirectory("quant-strategy-repo").resolve("finance.db"));
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        DatabaseInitializer initializer = new DatabaseInitializer();
        ReflectionTestUtils.setField(initializer, "jdbcTemplate", jdbc);
        ReflectionTestUtils.setField(initializer, "dataRoot", Files.createTempDirectory("quant-root").toString());
        initializer.afterPropertiesSet();
        jdbc.update("INSERT INTO quant_dataset(name,market,universe_type,source_type,data_kind,status,revision,created_at,updated_at) "
                + "VALUES('学习集','A_SHARE','CUSTOM','BUILT_IN','LEARNING_SAMPLE','READY',0,'2026-01-01T00:00:00','2026-01-01T00:00:00')");
        repository = new QuantStrategyRepository();
        ReflectionTestUtils.setField(repository, "jdbcTemplate", jdbc);
    }

    @Test
    void persistsValidatedDraftAndImmutableVersion() {
        QuantStrategyDraft draft = new QuantStrategyDraft();
        draft.setDatasetId(1L); draft.setPrompt("质量动量"); draft.setRawResponse("{}");
        draft.setNormalizedSpec("{\"name\":\"质量动量\"}"); draft.setStatus(QuantStrategyDraftStatus.VALIDATED); draft.setModel("test");
        QuantStrategySpec spec = new QuantStrategySpec(); spec.setName("质量动量"); draft.setSpec(spec);
        QuantStrategyDraft saved = repository.saveDraft(draft);
        QuantStrategyVersion version = new QuantStrategyVersion();
        version.setName("质量动量"); version.setDatasetId(1L); version.setVersion(1);
        version.setSpecJson(draft.getNormalizedSpec()); version.setStrategyFingerprint("strategy-sha");
        version.setDatasetFingerprint("dataset-sha"); version.setEngineVersion("quant-java-v1"); version.setSource("AGENT");
        QuantStrategyVersion persisted = repository.saveVersion(version);

        assertEquals(QuantStrategyDraftStatus.VALIDATED,
                repository.findDraft(saved.getId()).orElseThrow(AssertionError::new).getStatus());
        assertEquals("质量动量", saved.getSpec().getName());
        assertEquals("strategy-sha", repository.findVersion(persisted.getId()).orElseThrow(AssertionError::new).getStrategyFingerprint());
    }
}
