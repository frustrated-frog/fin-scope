package com.finscope.dao.radar;

import com.finscope.dao.config.DatabaseInitializer;
import com.finscope.domain.radar.RadarRefreshRun;
import com.finscope.domain.radar.RadarRefreshStep;
import com.finscope.domain.radar.RadarSignal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.sqlite.SQLiteDataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RadarRefreshRunRepositoryTest {
    private final LocalDateTime now = LocalDateTime.of(2026, 8, 5, 10, 0);
    private JdbcTemplate jdbc;
    private RadarRefreshRunRepository runs;
    private RadarRepository radar;

    @BeforeEach
    void setUp() throws Exception {
        Path root = Files.createTempDirectory("finscope-radar-refresh-run-test");
        SQLiteDataSource dataSource = new SQLiteDataSource();
        dataSource.setUrl("jdbc:sqlite:" + root.resolve("finance.db"));
        jdbc = new JdbcTemplate(dataSource);
        DatabaseInitializer initializer = new DatabaseInitializer();
        ReflectionTestUtils.setField(initializer, "jdbcTemplate", jdbc);
        ReflectionTestUtils.setField(initializer, "dataRoot", root.toString());
        initializer.afterPropertiesSet();
        runs = new RadarRefreshRunRepository(jdbc);
        radar = new RadarRepository(jdbc);
    }

    @Test
    void recordsRunStepsAndReadsTheLatestCompletedProduction() {
        RadarRefreshRun run = runs.startRun("run-20260805-1000", "SCHEDULED", now);
        RadarRefreshStep step = runs.startStep(run.getId(), "FETCH", now);
        RadarRefreshStep completedStep = runs.completeStep(run.getId(), step.getStepCode(), "SUCCESS", 2, 2,
                "providers=2", now.plusSeconds(3));
        RadarRefreshRun completed = runs.completeRun(run.getId(), 2, 2, 1, "", now.plusSeconds(5));

        assertEquals("SUCCESS", completedStep.getStatus());
        assertEquals(2, completedStep.getInputCount());
        assertEquals(2, completedStep.getOutputCount());
        assertEquals("SUCCESS", runs.findLatestCompletedRun().get().getStatus());
        assertEquals(1, runs.findLatestCompletedRun().get().getEventCount());
        assertEquals(completed.getId(), runs.findLatestCompletedRun().get().getId());
        assertTrue(runs.findSteps(run.getId()).stream().anyMatch(value -> "FETCH".equals(value.getStepCode())));

        RadarRefreshRun failed = runs.startRun("run-20260805-1001", "MANUAL", now.plusMinutes(1));
        runs.failRun(failed.getId(), "upstream timeout", now.plusMinutes(2));
        assertEquals("FAILED", runs.findLatestRun().get().getStatus());
        assertEquals(failed.getId(), runs.findLatestRun().get().getId());
    }

    @Test
    void capturesSourceRankingMetadataForTheNextProductionRun() {
        RadarSignal signal = new RadarSignal();
        signal.setItemId("CLS:1");
        signal.setProviderCode("CLS");
        signal.setSourceName("财联社");
        signal.setSourceTier("TIER_1");
        signal.setTitle("测试热点");
        signal.setContent("测试内容");
        signal.setPublishedAt(now.minusMinutes(5));
        signal.setContentHash("hash-1");
        signal.setSourceRank(3);
        signal.setPreviousSourceRank(8);
        signal.setSourceWeight(0.85D);

        RadarSignal stored = radar.capture(signal, now);

        assertEquals(3, stored.getSourceRank());
        assertEquals(8, stored.getPreviousSourceRank());
        assertEquals(0.85D, stored.getSourceWeight(), 0.001D);
    }
}
