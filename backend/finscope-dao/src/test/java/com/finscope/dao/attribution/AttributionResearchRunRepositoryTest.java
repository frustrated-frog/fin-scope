package com.finscope.dao.attribution;

import com.finscope.dao.config.DatabaseInitializer;
import com.finscope.domain.attribution.AttributionResearchRun;
import com.finscope.domain.attribution.AttributionResearchStep;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.sqlite.SQLiteDataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AttributionResearchRunRepositoryTest {
    private AttributionResearchRunRepository repository;

    @BeforeEach
    void setUp() throws Exception {
        Path dataRoot = Files.createTempDirectory("finscope-attribution-run-test");
        SQLiteDataSource dataSource = new SQLiteDataSource();
        dataSource.setUrl("jdbc:sqlite:" + dataRoot.resolve("finance.db"));
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        DatabaseInitializer initializer = new DatabaseInitializer();
        ReflectionTestUtils.setField(initializer, "jdbcTemplate", jdbcTemplate);
        ReflectionTestUtils.setField(initializer, "dataRoot", dataRoot.toString());
        initializer.afterPropertiesSet();
        repository = new AttributionResearchRunRepository(jdbcTemplate);
    }

    @Test
    void persistsRunAndStepsInStableOrder() {
        AttributionResearchRun run = new AttributionResearchRun();
        run.setReportId(42L);
        run.setStatus("RUNNING");
        run.setPlanJson("{\"version\":1}");
        run.setBudgetJson("{\"maxQueries\":8}");
        repository.createRun(run);

        repository.saveStep(step(run.getId(), "industry", "INDUSTRY", "PENDING"));
        repository.saveStep(step(run.getId(), "company", "COMPANY", "COMPLETED"));

        AttributionResearchRun saved = repository.findByReportId(42L).orElseThrow(AssertionError::new);
        List<AttributionResearchStep> steps = repository.findStepsByRunId(saved.getId());

        assertEquals("RUNNING", saved.getStatus());
        assertEquals(2, steps.size());
        assertEquals("company", steps.get(0).getStepId());
        assertEquals("industry", steps.get(1).getStepId());
    }

    @Test
    void updatesRunToPartialWithTerminationReason() {
        AttributionResearchRun run = new AttributionResearchRun();
        run.setReportId(43L);
        run.setStatus("RUNNING");
        repository.createRun(run);

        repository.updateRun(run.getId(), "PARTIAL", "部分轨道搜索失败", "QUERY_BUDGET_EXHAUSTED");

        AttributionResearchRun saved = repository.findByReportId(43L).orElseThrow(AssertionError::new);
        assertEquals("PARTIAL", saved.getStatus());
        assertEquals("QUERY_BUDGET_EXHAUSTED", saved.getTerminationReason());
        assertTrue(saved.getUpdatedAt() != null);
    }

    @Test
    void keepsPlannedTracksUntimestampedAndPreservesTheirActualStartTime() {
        AttributionResearchRun run = new AttributionResearchRun();
        run.setReportId(44L);
        run.setStatus("RUNNING");
        repository.createRun(run);

        AttributionResearchStep planned = step(run.getId(), "industry", "INDUSTRY", "PLANNED");
        repository.saveStep(planned);
        AttributionResearchStep savedPlan = repository.findStepsByRunId(run.getId()).get(0);
        assertEquals("PLANNED", savedPlan.getStatus());
        assertEquals(null, savedPlan.getStartedAt());
        assertEquals(null, savedPlan.getEndedAt());

        LocalDateTime startedAt = LocalDateTime.of(2026, 7, 11, 10, 0);
        AttributionResearchStep running = step(run.getId(), "industry", "INDUSTRY", "RUNNING");
        running.setAttempt(1);
        running.setStartedAt(startedAt);
        repository.saveStep(running);

        AttributionResearchStep progressUpdate = step(run.getId(), "industry", "INDUSTRY", "RUNNING");
        progressUpdate.setAttempt(2);
        progressUpdate.setOutputSummary("已获得 3 条证据");
        repository.saveStep(progressUpdate);

        AttributionResearchStep savedRunning = repository.findStepsByRunId(run.getId()).get(0);
        assertEquals(startedAt, savedRunning.getStartedAt());
        assertEquals(2, savedRunning.getAttempt());
        assertEquals("已获得 3 条证据", savedRunning.getOutputSummary());
    }

    @Test
    void deletesRunAndAllRelatedStepsByReportId() {
        AttributionResearchRun run = new AttributionResearchRun();
        run.setReportId(45L);
        run.setStatus("COMPLETED");
        repository.createRun(run);
        repository.saveStep(step(run.getId(), "company", "COMPANY", "COMPLETED"));

        repository.deleteByReportId(45L);

        assertFalse(repository.findByReportId(45L).isPresent());
        assertTrue(repository.findStepsByRunId(run.getId()).isEmpty());
    }

    private AttributionResearchStep step(Long runId, String stepId, String track, String status) {
        AttributionResearchStep step = new AttributionResearchStep();
        step.setRunId(runId);
        step.setStepId(stepId);
        step.setTrack(track);
        step.setStatus(status);
        step.setAttempt(0);
        step.setMaxAttempts(2);
        return step;
    }
}
