package com.finscope.dao.quant;

import com.finscope.dao.config.DatabaseInitializer;
import com.finscope.domain.quant.forecast.ForecastCandidateRun;
import com.finscope.domain.quant.forecast.SingleStockForecastRun;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.sqlite.SQLiteDataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ForecastCandidateRunRepositoryTest {
    private ForecastCandidateRunRepository repository;
    private SingleStockForecastRunRepository runs;

    @BeforeEach
    void setUp() throws Exception {
        Path root = Files.createTempDirectory("finscope-candidate-run-test");
        SQLiteDataSource source = new SQLiteDataSource();
        source.setUrl("jdbc:sqlite:" + root.resolve("finance.db"));
        JdbcTemplate jdbc = new JdbcTemplate(source);
        DatabaseInitializer initializer = new DatabaseInitializer();
        ReflectionTestUtils.setField(initializer, "jdbcTemplate", jdbc);
        ReflectionTestUtils.setField(initializer, "dataRoot", root.toString());
        initializer.afterPropertiesSet();
        repository = new ForecastCandidateRunRepository();
        ReflectionTestUtils.setField(repository, "jdbcTemplate", jdbc);
        runs = new SingleStockForecastRunRepository();
        ReflectionTestUtils.setField(runs, "jdbcTemplate", jdbc);
    }

    @Test
    void savesOneFrozenRowPerCandidateAndRejectsDuplicates() {
        Long runId = runs.save(run("fingerprint-a")).getId();

        repository.saveAll(runId, Arrays.asList(
                candidate("LOGISTIC", "CHAMPION", .61d),
                candidate("REGIME_LOGISTIC", "CHALLENGER", .65d)));

        List<ForecastCandidateRun> values = repository.findByForecastRunId(runId);
        assertEquals(2, values.size());
        assertEquals(.61d, values.get(0).getCalibratedProbability(), .000001d);
        assertThrows(RuntimeException.class, () -> repository.saveAll(runId,
                Arrays.asList(candidate("LOGISTIC", "CHAMPION", .61d))));
    }

    @Test
    void settlesAllCandidatesOnceAndDeduplicatesMaturedEvidenceByFingerprint() {
        SingleStockForecastRun first = runs.save(run("fingerprint-a"));
        repository.saveAll(first.getId(), Arrays.asList(
                candidate("LOGISTIC", "CHAMPION", .61d),
                candidate("REGIME_LOGISTIC", "CHALLENGER", .65d)));
        SingleStockForecastRun repeated = runs.save(run("fingerprint-a"));
        repository.saveAll(repeated.getId(), Arrays.asList(
                candidate("LOGISTIC", "CHAMPION", .62d),
                candidate("REGIME_LOGISTIC", "CHALLENGER", .66d)));

        assertEquals(2, repository.settleByForecastRunId(first.getId(), .08d, "UP",
                LocalDateTime.of(2026, 8, 17, 18, 0)));
        assertEquals(0, repository.settleByForecastRunId(first.getId(), .08d, "UP",
                LocalDateTime.of(2026, 8, 17, 18, 1)));
        repository.settleByForecastRunId(repeated.getId(), .07d, "UP",
                LocalDateTime.of(2026, 8, 18, 18, 0));

        List<ForecastCandidateRun> evidence = repository.findMaturedEvidence(
                "603618.SH", 5, 20);
        assertEquals(2, evidence.size());
        assertTrue(evidence.stream().allMatch(item -> first.getId().equals(item.getForecastRunId())));
        assertTrue(evidence.stream().allMatch(item -> item.getPredictionCorrect() != null));
        assertFalse(evidence.stream().anyMatch(item -> repeated.getId().equals(item.getForecastRunId())));
    }

    private ForecastCandidateRun candidate(String code, String role, double probability) {
        ForecastCandidateRun value = new ForecastCandidateRun();
        value.setModelCode(code);
        value.setModelName(code);
        value.setModelVersion("competition-" + code.toLowerCase() + "-platt-v6");
        value.setRole(role);
        value.setRawProbability(probability + .03d);
        value.setCalibratedProbability(probability);
        value.setShadowDecision(probability >= .6d ? "UP" : "ABSTAIN");
        value.setQualificationStatus("QUALIFIED");
        value.setLockedSampleCount(15);
        value.setLockedAccuracy(.60d);
        value.setLockedBrierScore(.22d);
        value.setLockedLogLoss(.64d);
        value.setLockedBrierSkillScore(.08d);
        return value;
    }

    private SingleStockForecastRun run(String fingerprint) {
        SingleStockForecastRun value = new SingleStockForecastRun();
        value.setInstrumentCode("603618.SH");
        value.setAsOfDate(LocalDate.of(2026, 8, 7));
        value.setHorizonDays(5);
        value.setStatus("CONDITIONAL");
        value.setUpProbability(.61d);
        value.setDataFingerprint(fingerprint);
        value.setModelVersion("competition-logistic-platt-v6");
        value.setReportSchemaVersion("single-stock-research-v6");
        value.setReportJson("{}");
        value.setHoldingSnapshotJson("{}");
        return value;
    }
}
