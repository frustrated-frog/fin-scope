package com.finscope.dao.quant;

import com.finscope.dao.config.DatabaseInitializer;
import com.finscope.domain.quant.data.QuantDataSyncRun;
import com.finscope.domain.quant.data.QuantDataset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.sqlite.SQLiteDataSource;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class QuantDataSyncRunRepositoryTest {
    private QuantDatasetRepository datasets;
    private QuantDataSyncRunRepository runs;

    @BeforeEach
    void setUp() throws Exception {
        Path root = Files.createTempDirectory("finscope-quant-sync-run-test");
        SQLiteDataSource dataSource = new SQLiteDataSource();
        dataSource.setUrl("jdbc:sqlite:" + root.resolve("finance.db"));
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        DatabaseInitializer initializer = new DatabaseInitializer();
        ReflectionTestUtils.setField(initializer, "jdbcTemplate", jdbc);
        ReflectionTestUtils.setField(initializer, "dataRoot", root.toString());
        initializer.afterPropertiesSet();
        datasets = new QuantDatasetRepository();
        ReflectionTestUtils.setField(datasets, "jdbcTemplate", jdbc);
        runs = new QuantDataSyncRunRepository(jdbc);
    }

    @Test
    void persistsRunLifecycleAndPreventsOverlappingDatasetSyncs() {
        Long datasetId = datasets.save(dataset()).getId();
        QuantDataSyncRun running = runs.start(datasetId, "MANUAL", 2,
                LocalDateTime.of(2026, 7, 20, 14, 0));

        assertThrows(DataIntegrityViolationException.class, () ->
                runs.start(datasetId, "SCHEDULED", 2, LocalDateTime.of(2026, 7, 20, 14, 1)));

        QuantDataSyncRun finished = runs.finish(running.getId(), "PARTIAL", 1, 1,
                20, 1, "EASTMONEY_DIRECT", "000001.SZ: timeout",
                LocalDateTime.of(2026, 7, 20, 14, 2));

        assertEquals("PARTIAL", finished.getStatus());
        assertEquals(1, finished.getSucceededInstruments());
        assertEquals(1, finished.getFailedInstruments());
        assertEquals(20, finished.getInsertedRows());
        assertEquals(1, runs.findByDatasetId(datasetId).size());
        assertEquals(finished.getId(), runs.findByDatasetId(datasetId).get(0).getId());
    }

    @Test
    void recoversAnInterruptedRunAfterTheLeaseExpires() {
        Long datasetId = datasets.save(dataset()).getId();
        QuantDataSyncRun interrupted = runs.start(datasetId, "SCHEDULED", 30,
                LocalDateTime.of(2026, 7, 20, 7, 0));

        QuantDataSyncRun restarted = runs.start(datasetId, "SCHEDULED", 30,
                LocalDateTime.of(2026, 7, 20, 14, 0));

        assertEquals("RUNNING", restarted.getStatus());
        QuantDataSyncRun recovered = runs.findByDatasetId(datasetId).get(1);
        assertEquals("FAILED", recovered.getStatus());
        assertEquals("previous sync was interrupted before completion", recovered.getWarningSummary());
        assertEquals(interrupted.getId(), recovered.getId());
    }

    private static QuantDataset dataset() {
        QuantDataset value = new QuantDataset();
        value.setName("sync target");
        value.setMarket("A_SHARE");
        value.setUniverseType("CUSTOM");
        value.setSourceType("MARKET_DATA_SYNC");
        value.setDataKind("REAL");
        value.setDatasetLevel("RESEARCH");
        value.setFingerprintVersion("quant-dataset-v2");
        value.setPartitionManifest("[]");
        value.setStatus("BUILDING");
        return value;
    }
}
