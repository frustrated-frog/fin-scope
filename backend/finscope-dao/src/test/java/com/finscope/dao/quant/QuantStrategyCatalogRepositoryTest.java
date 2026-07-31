package com.finscope.dao.quant;

import com.finscope.dao.config.DatabaseInitializer;
import com.finscope.domain.quant.catalog.QuantStrategyCandidate;
import com.finscope.domain.quant.catalog.QuantStrategyCatalogSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.sqlite.SQLiteDataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.file.Files;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QuantStrategyCatalogRepositoryTest {
    private QuantStrategyCatalogRepository repository;

    @BeforeEach
    void setUp() throws Exception {
        SQLiteDataSource dataSource = new SQLiteDataSource();
        dataSource.setUrl("jdbc:sqlite:" + Files.createTempDirectory("quant-catalog-repo").resolve("finance.db"));
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        DatabaseInitializer initializer = new DatabaseInitializer();
        ReflectionTestUtils.setField(initializer, "jdbcTemplate", jdbc);
        ReflectionTestUtils.setField(initializer, "dataRoot", Files.createTempDirectory("quant-catalog-root").toString());
        initializer.afterPropertiesSet();
        repository = new QuantStrategyCatalogRepository();
        ReflectionTestUtils.setField(repository, "jdbcTemplate", jdbc);
    }

    @Test
    void upsertsSnapshotIdempotentlyAndArchivesMissingCandidates() {
        LocalDateTime firstSync = LocalDateTime.of(2026, 8, 1, 9, 0);
        repository.saveSource(source("sha-one", firstSync));
        repository.upsertCandidates("AWESOME_SYSTEMATIC_TRADING", "sha-one",
                Arrays.asList(candidate("value", "价值（账面价值）因素", "ADAPTABLE"),
                        candidate("roa", "股票内部的ROA效应", "NEEDS_FACTOR")), firstSync);

        LocalDateTime secondSync = firstSync.plusHours(1);
        repository.saveSource(source("sha-two", secondSync));
        repository.upsertCandidates("AWESOME_SYSTEMATIC_TRADING", "sha-two",
                Collections.singletonList(candidate("value", "价值因子（更新）", "ADAPTABLE")), secondSync);

        assertEquals(1, repository.findCandidates(null, null).size());
        assertEquals("价值因子（更新）", repository.findCandidates("ADAPTABLE", "价值").get(0).getTitle());
        assertEquals(2, repository.countAll());
        assertEquals("sha-two", repository.findSource().orElseThrow(AssertionError::new).getCommitSha());
        assertTrue(repository.findByExternalKey("roa").orElseThrow(AssertionError::new).isArchived());
    }

    @Test
    void retainsCandidateOriginAcrossDraftAndVersionLinking() {
        LocalDateTime now = LocalDateTime.of(2026, 8, 1, 9, 0);
        repository.saveSource(source("sha", now));
        repository.upsertCandidates("AWESOME_SYSTEMATIC_TRADING", "sha",
                Collections.singletonList(candidate("value", "价值策略", "ADAPTABLE")), now);
        Long candidateId = repository.findCandidates(null, null).get(0).getId();

        repository.saveOrigin(candidateId, 11L, now);
        repository.linkVersionForDraft(11L, 22L);

        assertEquals(candidateId, repository.findCandidateIdByDraft(11L).orElseThrow(AssertionError::new));
        assertEquals(Long.valueOf(22L), repository.findVersionIdByDraft(11L).orElseThrow(AssertionError::new));
        assertFalse(repository.findById(candidateId).orElseThrow(AssertionError::new).isArchived());
    }

    private QuantStrategyCatalogSource source(String sha, LocalDateTime time) {
        QuantStrategyCatalogSource value = new QuantStrategyCatalogSource();
        value.setCode("AWESOME_SYSTEMATIC_TRADING");
        value.setRepositoryUrl("https://github.com/paperswithbacktest/awesome-systematic-trading");
        value.setBranch("main");
        value.setCommitSha(sha);
        value.setStatus("READY");
        value.setLastSyncedAt(time);
        return value;
    }

    private QuantStrategyCandidate candidate(String key, String title, String compatibility) {
        QuantStrategyCandidate value = new QuantStrategyCandidate();
        value.setExternalKey(key);
        value.setTitle(title);
        value.setAssetClass("EQUITY");
        value.setRebalanceCadence("月度");
        value.setCompatibilityStatus(compatibility);
        value.setAdaptationNote("测试说明");
        value.setMappedFactors(Collections.singletonList("BP"));
        value.setMissingFactors(Collections.<String>emptyList());
        return value;
    }
}
