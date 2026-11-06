package com.finscope.dao.research.quality;

import com.finscope.dao.config.DatabaseInitializer;
import com.finscope.domain.research.quality.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sqlite.SQLiteDataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

class ResearchQualityRepositoryTest {
    @TempDir
    Path directory;
    private ResearchQualityRepository repository;

    @BeforeEach
    void setup() throws Exception {
        SQLiteDataSource source = new SQLiteDataSource();
        source.setUrl("jdbc:sqlite:" + directory.resolve("quality.db"));
        JdbcTemplate jdbc = new JdbcTemplate(source);
        DatabaseInitializer initializer = new DatabaseInitializer();
        ReflectionTestUtils.setField(initializer, "jdbcTemplate", jdbc);
        ReflectionTestUtils.setField(initializer, "dataRoot", directory.toString());
        initializer.afterPropertiesSet();
        repository = new ResearchQualityRepository();
        ReflectionTestUtils.setField(repository, "jdbcTemplate", jdbc);
    }

    @Test
    void freezesContractAndDeduplicatesSnapshotsAcrossRestart() {
        ResearchTaskContract contract = new ResearchTaskContract();
        contract.setResearchRunId(21L);
        contract.setQuestion("原始问题");
        repository.initializeContract(contract);
        contract.setQuestion("恢复时不得覆盖");
        assertEquals("原始问题", repository.initializeContract(contract).getQuestion());
        ResearchQualityAssessment first = new ResearchQualityAssessment();
        first.setContract(contract);
        first.setFingerprint("fingerprint-1");
        first.getMissingConditions().add("日期未核实");
        repository.save(first);
        first.getMissingConditions().clear();
        assertFalse(repository.save(first).getMissingConditions().isEmpty());
        assertFalse(repository.findLatest(21L).orElseThrow().complete());
        assertTrue(repository.findLatest(22L).isEmpty());
    }

    @Test
    void reservesAtMostOneModelExtractionPerRun() {
        assertTrue(repository.reserveModelAttempt(21L));
        assertFalse(repository.reserveModelAttempt(21L));
        assertTrue(repository.reserveModelAttempt(22L));
    }
}
