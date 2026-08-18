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
    private JdbcTemplate jdbc;
    private DatabaseInitializer initializer;

    @BeforeEach
    void setUp() throws Exception {
        SQLiteDataSource dataSource = new SQLiteDataSource();
        dataSource.setUrl("jdbc:sqlite:" + Files.createTempDirectory("quant-catalog-repo").resolve("finance.db"));
        jdbc = new JdbcTemplate(dataSource);
        initializer = new DatabaseInitializer();
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

    @Test
    void findsTheLatestVersionOnlyWithinTheRequestedDataset() {
        LocalDateTime now = LocalDateTime.of(2026, 8, 1, 9, 0);
        repository.saveSource(source("sha", now));
        repository.upsertCandidates("AWESOME_SYSTEMATIC_TRADING", "sha",
                Collections.singletonList(candidate("value", "价值策略", "ADAPTABLE")), now);
        Long candidateId = repository.findCandidates(null, null).get(0).getId();
        insertDataset(3L, "数据集 A", now);
        insertDataset(4L, "数据集 B", now);
        insertVersion(21L, 3L, "策略 A", now);
        insertVersion(22L, 4L, "策略 B", now);
        repository.saveOrigin(candidateId, 11L, "sha-one", now);
        repository.linkVersionForDraft(11L, 21L);
        repository.saveOrigin(candidateId, 12L, "sha-two", now.plusMinutes(1));
        repository.linkVersionForDraft(12L, 22L);

        assertEquals(Long.valueOf(21L), repository.findLatestVersionIdByCandidateAndDatasetAndSourceCommit(
                        candidateId, 3L, "sha-one")
                .orElseThrow(AssertionError::new));
        assertEquals(Long.valueOf(22L), repository.findLatestVersionIdByCandidateAndDatasetAndSourceCommit(
                        candidateId, 4L, "sha-two")
                .orElseThrow(AssertionError::new));
        assertFalse(repository.findLatestVersionIdByCandidateAndDatasetAndSourceCommit(candidateId, 3L, "sha-two")
                .isPresent());
    }

    @Test
    void migratesLegacyOriginsAsUnknownWithoutClaimingTheCurrentSourceCommit() throws Exception {
        LocalDateTime now = LocalDateTime.of(2026, 8, 1, 9, 0);
        repository.saveSource(source("sha-current", now));
        repository.upsertCandidates("AWESOME_SYSTEMATIC_TRADING", "sha-current",
                Collections.singletonList(candidate("value", "价值策略", "ADAPTABLE")), now);
        Long candidateId = repository.findCandidates(null, null).get(0).getId();
        insertDataset(3L, "数据集 A", now);
        insertVersion(21L, 3L, "策略 A", now);
        jdbc.update("INSERT INTO quant_strategy_candidate_origin(candidate_id,draft_id,created_at) VALUES(?,?,?)",
                candidateId, 11L, now.toString());
        repository.linkVersionForDraft(11L, 21L);

        assertFalse(repository.findLatestVersionIdByCandidateAndDatasetAndSourceCommit(
                candidateId, 3L, "sha-current").isPresent());

        initializer.afterPropertiesSet();

        assertFalse(repository.findLatestVersionIdByCandidateAndDatasetAndSourceCommit(
                candidateId, 3L, "sha-current").isPresent());
        assertEquals(Long.valueOf(21L), repository.findLatestLegacyVersionIdByCandidateAndDataset(
                candidateId, 3L).orElseThrow(AssertionError::new));
    }

    private void insertDataset(Long id, String name, LocalDateTime now) {
        jdbc.update("INSERT INTO quant_dataset(id,name,market,universe_type,source_type,data_kind,status,created_at,updated_at) "
                        + "VALUES(?,?,'A_SHARE','CUSTOM','LOCAL','REAL','READY',?,?)",
                id, name, now.toString(), now.toString());
    }

    private void insertVersion(Long id, Long datasetId, String name, LocalDateTime now) {
        jdbc.update("INSERT INTO quant_strategy_version(id,name,dataset_id,version,spec_json,strategy_fingerprint,"
                        + "dataset_fingerprint,engine_version,source,created_at) VALUES(?,?,?,1,'{}',?,?,?,'AGENT',?)",
                id, name, datasetId, "strategy-" + id, "dataset-" + datasetId, "engine-v1", now.toString());
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
